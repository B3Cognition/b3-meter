.PHONY: test test-fast test-unit test-integration build dev clean

test: test-unit test-integration

test-fast:
	./gradlew test --parallel

test-unit:
	./gradlew test -x :modules:web-api:test

test-integration:
	./gradlew :modules:web-api:test

build:
	./gradlew build && cd web-ui && npm run build

dev:
	./gradlew :modules:web-api:bootRun &
	cd web-ui && npm run dev

clean:
	./gradlew clean && rm -rf web-ui/dist web-ui/node_modules
