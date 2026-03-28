.PHONY: run run-ui test test-engine build docker distributed mocks smoke clean help

run:            ## Start the backend (Spring Boot on port 8080)
	./gradlew :modules:web-api:bootRun

run-ui:         ## Start the frontend (React on port 3000)
	cd web-ui && npm install && npm run dev

test:           ## Run all backend tests
	./gradlew test

test-engine:    ## Run engine tests only (fastest)
	./gradlew :modules:engine-service:test

build:          ## Build all modules
	./gradlew build -x test

docker:         ## Build Docker images
	docker build -f Dockerfile.controller -t jmeter-next-controller .
	docker build -f Dockerfile.worker -t jmeter-next-worker .

distributed:    ## Run distributed mode (1 controller + 3 workers)
	docker compose -f docker-compose.distributed.yml up --build

mocks:          ## Start mock servers
	docker compose -f docker-compose.test.yml up --build

smoke:          ## Run smoke tests against mock servers
	curl -s -X POST http://localhost:8080/api/v1/mocks/start | python3 -m json.tool
	curl -s -X POST http://localhost:8080/api/v1/mocks/smoke | python3 -m json.tool

clean:          ## Clean build artifacts
	./gradlew clean
	rm -rf web-ui/node_modules web-ui/dist

help:           ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

.DEFAULT_GOAL := help
