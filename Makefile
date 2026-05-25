.PHONY: infra up down logs ps clean kafka-topics web help

## ─────────────────────────────────────────────────────────
## AlgoVerse Developer Shortcuts
## Run `make help` to list all targets.
## ─────────────────────────────────────────────────────────

infra: ## Start infrastructure only (postgres, redis, kafka, elasticsearch, mongodb)
	docker compose -f docker-compose.infra.yml up -d

up: ## Start the full stack (all services + infra)
	docker compose up -d

down: ## Stop all running services
	docker compose down

logs: ## Tail logs for a service (usage: make logs s=auth-service)
	docker compose logs -f $(s)

ps: ## Show status of all containers
	docker compose ps

clean: ## Remove all containers + named volumes (DESTRUCTIVE)
	docker compose down -v --remove-orphans

kafka-topics: ## Create required Kafka topics (runs create-topics.sh in the broker)
	docker exec algoverse-kafka bash /scripts/create-topics.sh

web: ## Start the frontend Vite dev server locally
	cd apps/web && npm run dev

help: ## Display this help message
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'
