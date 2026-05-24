from functools import lru_cache
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    # App
    app_name: str = "algoverse-ai-service"
    app_version: str = "1.0.0"
    debug: bool = False
    port: int = 8090

    # Database
    ai_db_url: str = "postgresql+asyncpg://postgres:postgres@localhost:5432/algoverse_ai"

    # Redis
    redis_host: str = "localhost"
    redis_port: int = 6379
    redis_password: str = ""
    redis_db: int = 2  # separate DB from auth-service (0) and problem-service (1)

    # Kafka
    kafka_bootstrap_servers: str = "localhost:9092"
    kafka_consumer_group: str = "ai-service-group"

    # Anthropic
    anthropic_api_key: str
    claude_model: str = "claude-sonnet-4-6"
    claude_max_tokens: int = 4096

    # JWT (RS256 public key from auth-service)
    jwt_public_key: str  # PEM-encoded RSA public key

    # Rate limits (per user per minute)
    hint_rate_limit: int = 20
    review_rate_limit: int = 10
    conversation_rate_limit: int = 60
    explain_rate_limit: int = 30

    # Problem service base URL (for fetching problem details)
    problem_service_url: str = "http://localhost:8082/api/v1"


@lru_cache
def get_settings() -> Settings:
    return Settings()
