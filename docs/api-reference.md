# API Reference

All endpoints are served at `http://localhost:8080` (default) and return JSON
unless otherwise noted. Authentication is via JWT Bearer token (see Auth
endpoints).

## Auth

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/auth/login` | Authenticate with username/password, returns JWT |
| `POST` | `/api/v1/auth/refresh` | Refresh an expired access token |
| `POST` | `/api/v1/auth/logout` | Invalidate the current refresh token |

## Test Plans

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/plans` | Create a new test plan |
| `GET` | `/api/v1/plans` | List all test plans |
| `GET` | `/api/v1/plans/{id}` | Get a single test plan |
| `PUT` | `/api/v1/plans/{id}` | Update a test plan |
| `DELETE` | `/api/v1/plans/{id}` | Soft-delete a test plan |
| `POST` | `/api/v1/plans/import` | Import a JMeter .jmx file |
| `GET` | `/api/v1/plans/{id}/export` | Export plan as .jmx |
| `GET` | `/api/v1/plans/{id}/revisions` | List plan revisions |
| `POST` | `/api/v1/plans/{id}/revisions/{rev}/restore` | Restore a specific revision |

## Test Runs

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/runs` | Start a new test run |
| `GET` | `/api/v1/runs` | List all test runs |
| `GET` | `/api/v1/runs/{id}` | Get run status and summary |
| `GET` | `/api/v1/runs/{id}/metrics` | Get detailed sample metrics |
| `POST` | `/api/v1/runs/{id}/stop` | Graceful stop (finish in-flight samples) |
| `POST` | `/api/v1/runs/{id}/stop-now` | Immediate stop |

## Reports

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/runs/{runId}/report` | Generate HTML/JSON report |
| `GET` | `/api/v1/runs/{runId}/report` | Download generated report |

## Workers

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/workers` | Register a worker |
| `GET` | `/api/v1/workers` | List all workers |
| `DELETE` | `/api/v1/workers/{id}` | Remove a worker |
| `GET` | `/api/v1/workers/{id}/health` | Health check a specific worker |

## Mock Servers

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/mocks/start` | Start all built-in mock servers |
| `POST` | `/api/v1/mocks/stop` | Stop all mock servers |
| `GET` | `/api/v1/mocks/status` | Get mock server health status |
| `POST` | `/api/v1/mocks/smoke` | Run smoke tests against mock servers |

## Plugins

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/plugins` | List installed plugins |
| `POST` | `/api/v1/plugins` | Install a new plugin (JAR upload) |
| `DELETE` | `/api/v1/plugins/{id}` | Remove a plugin |
| `POST` | `/api/v1/plugins/{id}/activate` | Activate an installed plugin |

## Proxy Recorder

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/proxy-recorder/start` | Start the recording proxy |
| `POST` | `/api/v1/proxy-recorder/stop` | Stop the recording proxy |
| `GET` | `/api/v1/proxy-recorder/status` | Get proxy recorder status |
| `GET` | `/api/v1/proxy-recorder/captured` | List captured requests |
| `POST` | `/api/v1/proxy-recorder/capture` | Manually add a captured request |
| `POST` | `/api/v1/proxy-recorder/apply` | Convert captured requests to a test plan |

## Schemas

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/schemas` | List all component schemas |
| `GET` | `/api/v1/schemas/{name}` | Get schema for a specific component |

## Streaming

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/stream/{runId}` | SSE stream of live results (text/event-stream) |

## Health

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/health` | Application health check |
