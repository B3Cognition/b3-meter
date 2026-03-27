# jMeter Next

> Modern web-based load testing tool — a complete rewrite of Apache JMeter for the cloud era.

![License](https://img.shields.io/badge/license-Apache%202.0-blue)
![Java](https://img.shields.io/badge/java-21-orange)
![React](https://img.shields.io/badge/react-19-61DAFB)

## Features

- **14 Protocol Samplers**: HTTP, WebSocket, SSE, MQTT, gRPC, HLS, DASH, WebRTC, FTP, LDAP + JSR223, BeanShell, OS Process, Debug
- **Modern Web UI**: React 19 + TypeScript + 24 themes (JMeter Classic, Darcula, Matrix, LCARS, Cyberpunk...)
- **Real-time Results**: Live throughput, response time, error rate charts via SSE
- **JMX Compatible**: Import/export Apache JMeter .jmx files
- **Distributed Mode**: Controller + N workers via gRPC, with Kubernetes auto-scaling
- **Self Smoke Testing**: Built-in regression suite with 11 mock servers
- **Innovation Features**: SLA Discovery, A/B Performance Testing, Chaos+Load Fusion
- **Pure JDK**: Zero external dependencies in the engine (Java 21 virtual threads)

## Quick Start

### Prerequisites
- Java 21+
- Node.js 20+
- Gradle 8+

### Run Locally
```bash
# Backend
./gradlew :modules:web-api:bootRun

# Frontend (separate terminal)
cd web-ui && npm install && npm run dev

# Open http://localhost:3000
```

### Run with Mock Servers
```bash
# Start all 11 protocol mock servers
./scripts/start-local-workers.sh 0  # just mock servers
# Or start mock servers + 3 worker nodes
./scripts/start-local-workers.sh 3
```

### Docker Compose
```bash
# Distributed mode (1 controller + 3 workers)
docker compose -f docker-compose.distributed.yml up

# With mock servers
docker compose -f docker-compose.test.yml up
```

### Kubernetes
```bash
helm install jmeter-next ./deploy/helm/jmeter-next \
  --set worker.replicaCount=5
```

## Architecture

```
┌─────────────────────────────────────────┐
│              React Web UI                │
│  (Visual Editor, XML, Results, Charts)  │
├─────────────────────────────────────────┤
│          Spring Boot REST API            │
│    (Plans, Runs, Workers, Mocks)        │
├─────────────────────────────────────────┤
│           Engine Service                 │
│  (NodeInterpreter, 14 Samplers,         │
│   VirtualUserExecutor, SampleBroker)    │
├─────────────────────────────────────────┤
│        Distributed Controller            │
│  (gRPC + WebSocket, Result Aggregator)  │
└─────────────────────────────────────────┘
```

## Protocol Support
| Protocol | Sampler | Mock Server | Port |
|----------|---------|:-----------:|:----:|
| HTTP/HTTPS | HttpSamplerExecutor | http-mock | 8081 |
| WebSocket | WebSocketSamplerExecutor | ws-mock | 8082 |
| SSE | SSESamplerExecutor | sse-mock | 8083 |
| HLS | HLSSamplerExecutor | hls-mock | 8084 |
| MQTT | MQTTSamplerExecutor | mqtt-mock | 1883 |
| gRPC | GrpcSamplerExecutor | grpc-mock | 50051 |
| DASH | DASHSamplerExecutor | dash-mock | 8086 |
| WebRTC | WebRTCSamplerExecutor | signaling | 8088 |
| FTP | FTPSamplerExecutor | ftp-mock | 2121 |
| LDAP | LDAPSamplerExecutor | ldap-mock | 3389 |

## Database

By default jMeter Next uses an embedded H2 database (zero setup). For production
deployments, PostgreSQL is recommended.

### PostgreSQL Setup
```bash
# 1. Create a database
createdb jmeter_next

# 2. Start with the postgres profile
DATABASE_URL=jdbc:postgresql://localhost:5432/jmeter_next \
DATABASE_USER=jmeter \
DATABASE_PASSWORD=jmeter \
./gradlew :modules:web-api:bootRun --args='--spring.profiles.active=postgres'
```

Flyway migrations are shared between H2 and PostgreSQL (standard SQL).

## API Documentation

After starting the backend, visit:
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup and guidelines.

## License

Apache License 2.0 — see [LICENSE](LICENSE).
