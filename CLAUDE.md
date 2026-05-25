# CLAUDE.md

## Project Name

AlgoVerse

---

# Mission

Build the world’s most advanced AI-powered DSA and System Design learning platform with:

* cinematic UX
* real-time visualizations
* AST-based execution tracing
* AI mentorship
* personalized learning
* enterprise-grade scalability

---

# Engineering Principles

1. Clean Architecture
2. SOLID Principles
3. Domain Driven Design
4. Event Driven Architecture
5. Production-grade quality
6. Scalability first
7. Security first
8. Accessibility first
9. Test-driven mindset
10. Modular design

---

# Tech Stack

## Frontend

* React
* Vite
* TypeScript
* TailwindCSS
* Framer Motion
* Three.js
* React Three Fiber
* Zustand
* React Query
* Monaco Editor

## Backend

* Java 21
* Spring Boot 3
* Spring Cloud
* Kafka
* Redis
* PostgreSQL
* MongoDB
* Elasticsearch

## Infrastructure

* Docker
* Kubernetes
* Terraform
* GitHub Actions
* Prometheus
* Grafana
* Loki

---

# Architecture Standards

* Microservices architecture
* API Gateway pattern
* CQRS where appropriate
* Distributed tracing
* Centralized logging
* Independent deployments
* Feature modularization

---

# UI/UX Rules

1. Never build boring UI.
2. Use cinematic transitions.
3. Use motion intentionally.
4. Maintain visual hierarchy.
5. Maintain accessibility.
6. Use GPU accelerated animations.
7. Keep interactions delightful.

---

# Frontend Standards

* Atomic component structure
* Reusable hooks
* Strict typing
* Responsive design
* Lazy loading
* Code splitting
* Optimistic UI updates

---

# Backend Standards

* Clean layered architecture
* DTO validation
* OpenAPI documentation
* Secure APIs
* Kafka async communication
* Idempotent consumers
* Retry strategies

---

# Security Standards

* OAuth2
* JWT
* CSRF protection
* Rate limiting
* SQL injection prevention
* Secret rotation
* WAF integration

---

# AI Engine Standards

* AST parsing pipeline
* execution tracing
* state transition generation
* memory visualization
* AI hint generation
* recommendation engine

---

# Testing Standards

* Unit testing mandatory
* Integration tests mandatory
* E2E tests mandatory
* Load testing mandatory
* Security testing mandatory

---

# DevOps Standards

* Infrastructure as code
* Immutable deployments
* Blue/green deployments
* Observability by default
* Auto scaling enabled

---

# Code Quality Rules

* No duplicated logic
* No untyped APIs
* No hardcoded secrets
* No large components
* No tight coupling
* No skipped tests

---

# Repository Structure

/docs
/frontend
/backend
/infrastructure
/ai-engine
/testing
/devops
/scripts

---

# IMPORTANT

Always think:

* scalable
* maintainable
* observable
* secure
* performant
* beautiful

Your changes will be tested by OpenAI Codex platform

# Git 

* With every changes make sure to update the Readme file with latest necessary updates about the product or any setup ro run information
* All commits should happen in the format "COMMIT-<A random 4 digit number> | Commit Message"

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
