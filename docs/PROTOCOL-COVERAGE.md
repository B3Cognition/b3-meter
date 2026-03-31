# Protocol Coverage

## Three-Tier Classification

### Tier 1 — Protocol Samplers with Real Network I/O (12 protocols)

Each requires: mock server + smoke JMX + unit test.

| Protocol | Mock Server | Smoke JMX | Port |
|----------|-------------|-----------|------|
| HTTP | `http-mock` | `http-smoke.jmx` | 9081 |
| WebSocket | `ws-mock` | `ws-smoke.jmx` | 9082 |
| SSE | `sse-mock` | `sse-smoke.jmx` | 9083 |
| HLS | `hls-mock` | `hls-smoke.jmx` | 9084 |
| MQTT | `mqtt-mock` | `mqtt-smoke.jmx` | 9883 |
| gRPC | `grpc-mock` | `grpc-smoke.jmx` | 9051 |
| DASH | `dash-mock` | `dash-smoke.jmx` | 9086 |
| WebRTC | `webrtc-signaling-mock` | `webrtc-smoke.jmx` | 9088 |
| FTP | `ftp-mock` | `ftp-smoke.jmx` | 9122 |
| LDAP | `ldap-mock` | `ldap-smoke.jmx` | 9390 |
| TCP | `tcp-mock` | `tcp-smoke.jmx` | 9089 |
| SMTP | `smtp-mock` | `smtp-smoke.jmx` | 9025 |

STUN is a sub-protocol of WebRTC; it is exercised via the `stun-mock` server and `stun-smoke.jmx` alongside the WebRTC tier.

**JDBC special case**: Tier 1 sampler (real JDBC socket I/O in production) but uses H2 in-memory as the mock in tests. JDBC is a JVM API layered over a TCP socket, not a bare socket protocol, so an in-JVM H2 database is the correct lightweight substitute — no external port is opened during the test run.

---

### Tier 2 — Stub Protocol Samplers (3 protocols)

AJP, JMS, MailReader.

These samplers return HTTP 501 (Not Implemented). No mock server is required. A unit test verifies the clean-failure contract.

**Why stubs?** Constitution Principle II — *no external runtime dependencies*. AJP requires a Tomcat connector; JMS requires a broker (ActiveMQ/RabbitMQ); MailReader requires a live IMAP/POP3 server. Bundling any of these as a mock server would inflate the test runtime and create fragile infrastructure. The stub contract (501 + descriptive error) is the correct production behaviour until a use-case justifies the cost.

---

### Tier 3 — Non-Protocol Executors

All controllers, assertions, extractors, timers, config elements, listeners, and scripting samplers.

These operate entirely on in-memory objects (`SampleResult`, `PlanNode`, variable context). They open no outbound socket. Unit tests use pre-built `SampleResult` instances or stub execution trees. Examples: `ConstantTimer`, `ResponseAssertion`, `RegExExtractor`, `JSR223PreProcessor`, `BeanShellPreProcessor`, `LoopController`, `IfController`.

---

## Three-List Consistency Model

Every Tier 1 protocol must appear in all four locations simultaneously. Adding a new protocol requires updating all four:

```
MockServerController.initServers()     — starts the process
        ↕
MockServerController.SMOKE_PLANS       — names the JMX plan
        ↕
SelfSmoke.tsx PROTOCOL_ORDER           — shows the row in the UI
        ↕
test-plans/e2e/<name>-smoke.jmx        — the actual test plan file
```

**Consistency check** (T029):

```bash
# Count must match across all four:
grep "MockServerDef" modules/web-api/.../MockServerController.java | wc -l
grep '".*-smoke"' modules/web-api/.../MockServerController.java | wc -l
grep "'-mock'" web-ui/src/components/SelfSmoke/SelfSmoke.tsx | wc -l
ls test-plans/e2e/*-smoke.jmx | wc -l
```

Expected: all counts equal 13 (11 original + TCP + SMTP; STUN counts as a separate mock alongside WebRTC).
