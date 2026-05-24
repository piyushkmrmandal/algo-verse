"""
AlgoVerse AI Service — FastAPI application entry point.

Port: 8090
Prefix: /api/v1
"""

import asyncio
import logging
import uuid
from contextlib import asynccontextmanager
from datetime import datetime, timezone

import structlog
from fastapi import FastAPI, Request, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from prometheus_fastapi_instrumentator import Instrumentator

from app.config import get_settings
from app.kafka.consumer import start_hints_consumer
from app.routers import code_review, conversation, explain, hints, learning_path, recommendations, skill_model

# ── Logging setup ────────────────────────────────────────────────────────────

structlog.configure(
    processors=[
        structlog.contextvars.merge_contextvars,
        structlog.processors.TimeStamper(fmt="iso"),
        structlog.stdlib.add_log_level,
        structlog.dev.ConsoleRenderer(),
    ],
    wrapper_class=structlog.stdlib.BoundLogger,
    context_class=dict,
    logger_factory=structlog.stdlib.LoggerFactory(),
)
logger = structlog.get_logger()

settings = get_settings()

# ── Lifespan ─────────────────────────────────────────────────────────────────

_kafka_task: asyncio.Task | None = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    global _kafka_task
    logger.info("Starting AI service", version=settings.app_version, port=settings.port)

    # Start Kafka consumer in background
    _kafka_task = asyncio.create_task(start_hints_consumer())

    yield

    # Graceful shutdown
    if _kafka_task:
        _kafka_task.cancel()
        try:
            await _kafka_task
        except asyncio.CancelledError:
            pass
    logger.info("AI service shut down cleanly")


# ── App factory ───────────────────────────────────────────────────────────────

app = FastAPI(
    title="AlgoVerse AI Service",
    version=settings.app_version,
    description="AI-powered DSA tutoring, code review, and personalised learning.",
    docs_url="/docs",
    redoc_url="/redoc",
    lifespan=lifespan,
)

# CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5173", "https://algoverse.io"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Prometheus metrics
Instrumentator().instrument(app).expose(app, endpoint="/metrics")

# ── Request ID middleware ─────────────────────────────────────────────────────

@app.middleware("http")
async def request_id_middleware(request: Request, call_next):
    trace_id = request.headers.get("X-Trace-Id", str(uuid.uuid4()))
    structlog.contextvars.bind_contextvars(trace_id=trace_id)
    response = await call_next(request)
    response.headers["X-Trace-Id"] = trace_id
    return response


# ── Global error handler ──────────────────────────────────────────────────────

@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    logger.exception("Unhandled exception", path=request.url.path)
    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content={
            "traceId": structlog.contextvars.get_contextvars().get("trace_id", ""),
            "code": "INTERNAL_ERROR",
            "message": "An unexpected error occurred.",
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "details": None,
        },
    )


# ── Routers ───────────────────────────────────────────────────────────────────

PREFIX = "/api/v1"

app.include_router(hints.router, prefix=PREFIX)
app.include_router(code_review.router, prefix=PREFIX)
app.include_router(explain.router, prefix=PREFIX)
app.include_router(skill_model.router, prefix=PREFIX)
app.include_router(recommendations.router, prefix=PREFIX)
app.include_router(learning_path.router, prefix=PREFIX)
app.include_router(conversation.router, prefix=PREFIX)


# ── Health endpoints ──────────────────────────────────────────────────────────

@app.get("/health", tags=["Health"])
async def health():
    return {"status": "ok", "service": settings.app_name, "version": settings.app_version}


@app.get("/health/ready", tags=["Health"])
async def ready():
    return {"status": "ready", "timestamp": datetime.now(timezone.utc).isoformat()}
