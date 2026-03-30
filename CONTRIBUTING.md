# Contributing to b3meter

Thank you for your interest in contributing to b3meter. This document provides guidelines and instructions for contributing.

## Development Setup

### Prerequisites

- **Java 21** (Temurin recommended)
- **Node.js 20+** (for web-ui)
- **Docker** (for integration tests and local deployment)
- **Gradle** (wrapper included, no global install needed)

### Getting Started

1. Fork and clone the repository:
   ```bash
   git clone https://github.com/b3-cognition/b3-meter.git
   cd b3-meter
   ```

2. Build the backend:
   ```bash
   ./gradlew build
   ```

3. Set up the frontend:
   ```bash
   cd web-ui
   npm ci
   npm run build
   ```

4. Run tests:
   ```bash
   # Backend tests
   ./gradlew test

   # Frontend type check
   cd web-ui && npx tsc --noEmit
   ```

5. Run locally with Docker Compose:
   ```bash
   docker compose -f docker-compose.test.yml up
   ```

## Project Structure

```
modules/
  build/                  # Build utilities
  distributed-controller/ # Distributed mode controller
  engine-adapter/         # JMeter engine adapter
  engine-service/         # Core engine service
  web-api/                # REST API (Spring Boot)
  web-ui/                 # Frontend (see web-ui/)
  worker-node/            # Distributed worker node
  worker-proto/           # Worker communication protocol
web-ui/                   # React/TypeScript frontend
deploy/                   # Deployment configurations
config/                   # Application configuration
test-plans/               # Sample JMeter test plans
```

## Coding Standards

### Kotlin/Java (Backend)

- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable and function names
- Write unit tests for new functionality
- Keep functions focused and short

### TypeScript (Frontend)

- Use TypeScript strict mode (no `any` types)
- Follow the existing component patterns in `web-ui/`
- Use functional components with hooks

### General

- Write clear commit messages describing the "why", not just the "what"
- Keep commits atomic -- one logical change per commit
- Add tests for bug fixes and new features

## Adding Dependencies

When adding a new third-party library as a runtime dependency:

1. Verify its license is compatible with Apache License 2.0.
   Permitted licenses include: Apache 2.0, MIT, BSD-2-Clause, BSD-3-Clause, ISC.
   Copyleft licenses (GPL, AGPL, SSPL) are **not** permitted in runtime dependencies.
2. Update `NOTICE` at the repository root with the library name, version, license, and
   project URL. Follow the existing format.
3. Run `./gradlew generateLicenseReport` and confirm the new dependency appears correctly.

## Generating License Report

Run the following command to regenerate the bundled third-party license file:

```bash
./gradlew generateLicenseReport
```

Output: `build/reports/licenses/THIRD-PARTY-LICENSES.txt`

This file is automatically included in Docker images at `/opt/app/THIRD-PARTY-LICENSES.txt`.

## Pull Request Process

1. Create a feature branch from `main`:
   ```bash
   git checkout -b feature/my-feature
   ```

2. Make your changes, ensuring:
   - All existing tests pass (`./gradlew test`)
   - TypeScript compiles without errors (`npx tsc --noEmit`)
   - New code has test coverage

3. Push your branch and open a Pull Request against `main`.

4. In your PR description:
   - Summarize the changes and motivation
   - Reference any related issues
   - Include test evidence or screenshots where applicable

5. Wait for CI checks to pass and at least one approving review.

## Reporting Issues

- Use GitHub Issues at https://github.com/b3-cognition/b3-meter/issues
- Include reproduction steps, expected vs actual behavior, and environment details
- Check existing issues before opening a duplicate

## License

By contributing, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE).
