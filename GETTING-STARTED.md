# Getting Started with b3meter

A complete dummies guide — from zero to running load tests with 13 protocol mock servers, distributed gRPC workers, and custom test design.

---

## 0. Quick Reference

| Task | Command |
|------|---------|
| Start backend | `make run` |
| Start frontend | `make run-ui` |
| Run all tests | `make test` |
| Start mock servers (Docker) | `make mocks` |
| Run smoke tests | `make smoke` |
| Start distributed mode | `make distributed` |
| Start 3 local workers | `./scripts/start-local-workers.sh` |
| Stop local workers | `./scripts/stop-local-workers.sh` |
| CLI headless run | `./gradlew :modules:engine-adapter:run --args="-n -t test-plans/http-smoke.jmx"` |
| Build Docker images | `make docker` |
| Kubernetes deploy | `helm install b3meter ./deploy/helm/b3meter` |
| Full build (skip tests) | `make build` |
| Health check | `curl http://localhost:8080/actuator/health` |
| API docs | `http://localhost:8080/swagger-ui.html` |

---

## 1. Prerequisites

| Tool | Version | Check | Install (macOS) |
|------|---------|-------|-----------------|
| Java | 21+ | `java -version` | `brew install openjdk@21` |
| Node.js | 20+ | `node --version` | `brew install node@20` |
| Docker | any | `docker --version` | Docker Desktop |

> **Why Java 21?** Virtual threads (`spring.threads.virtual.enabled=true`) are the core scaling mechanism. The engine runs thousands of virtual users on a single JVM without thread pool exhaustion. Java 17 or lower will NOT work.

Verify your setup:
```bash
java -version    # Must show 21.x
node --version   # Must show v20.x
docker --version # Any recent version
```

---

## 2. Start the Application (Single Node)

### Step 1 — Start the backend

```bash
cd /Users/ladislavbihari/myWork/devjmeter/b3-meter
make run
```

Or manually:
```bash
./gradlew :modules:web-api:bootRun
```

Wait for this line in the console:
```
Started WebApiApplication in 2.1 seconds
```

Verify:
```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

> **No database needed.** Storage is in-memory (ConcurrentHashMap). Plans and runs persist for the lifetime of the JVM process. No auth required in default profile.

### Step 2 — Start the frontend

Open a **new terminal**:
```bash
cd /Users/ladislavbihari/myWork/devjmeter/b3-meter
make run-ui
```

Or manually:
```bash
cd web-ui && npm install && npm run dev
```

Open **http://localhost:3000** in your browser.

### Step 3 — Run your first test

In the web UI:
1. Click **Import** in the toolbar
2. Select `test-plans/http-smoke.jmx`
3. Click **Run** (green play button)
4. Watch results stream live in the dashboard

Or via CLI (no UI needed):
```bash
./gradlew :modules:engine-adapter:run \
  --args="-n -t test-plans/http-smoke.jmx"
```

### Port Conflict?

If port 8080 or 3000 is in use:
```bash
# Find what's using the port
lsof -i :8080

# Kill it
kill -9 $(lsof -t -i :8080)
```

---

## 3. Mock Servers and Smoke Testing

b3meter ships with **13 protocol mock servers** for self-testing. These simulate real endpoints so you can verify all protocols work without external dependencies.

### Step 1 — Start all mock servers

```bash
make mocks
```

Or manually:
```bash
docker compose --env-file .env.test -f docker-compose.test.yml up --build -d
```

Wait ~30 seconds for all containers to become healthy:
```bash
docker ps --filter "name=b3-meter" --format "table {{.Names}}\t{{.Status}}"
```

All 13 should show `(healthy)`.

### Step 2 — Verify mock status

With the backend running:
```bash
curl -s http://localhost:8080/api/v1/mocks/status | python3 -m json.tool
```

Or open `http://localhost:3000`, go to **Self Smoke** tab — you should see **13/13 UP**:

| Mock Server | Port | Protocol |
|---|---|---|
| http-mock | 9081 | HTTP |
| ws-mock | 9082 | WebSocket |
| sse-mock | 9083 | Server-Sent Events |
| hls-mock | 9084 | HLS streaming |
| grpc-mock | 9051 (gRPC) / 9085 (health) | gRPC |
| dash-mock | 9086 | DASH streaming |
| stun-mock | 9087 (HTTP) / 3478 (UDP) | STUN/ICE |
| webrtc-signaling | 9088 | WebRTC |
| mqtt-mock | 9883 (broker) / 9884 (health) | MQTT |
| tcp-mock | 9089 / 9090 (health) | TCP |
| smtp-mock | 9025 / 9026 (health) | SMTP |
| ftp-mock | 9122 / 9123 (health) | FTP |
| ldap-mock | 9390 / 9391 (health) | LDAP |

### Step 3 — Run smoke tests

```bash
make smoke
```

Or via API:
```bash
curl -s -X POST http://localhost:8080/api/v1/mocks/smoke | python3 -m json.tool
```

Or click **"Run Self Smoke"** in the web UI's Self Smoke tab.

Expected result: **13/13 PASS, 0 SKIP, 0 FAIL**.

### Port conflicts?

All mock ports are configurable via `.env.test`:
```bash
# Edit .env.test to change any port
HTTP_MOCK_PORT=7081   # was 9081
# Then restart
docker compose --env-file .env.test -f docker-compose.test.yml up -d
```

### Cleanup

```bash
docker compose --env-file .env.test -f docker-compose.test.yml down
```

---

## 4. Distributed Mode with gRPC Workers

Distributed mode splits virtual users across multiple worker JVMs connected via gRPC. This is how you scale beyond a single machine.

### How it works

```
                    ┌─────────────┐
                    │ Controller  │ port 8080
                    │ (web-api)   │
                    └──────┬──────┘
              gRPC         │         gRPC
         ┌─────────────────┼─────────────────┐
         ▼                 ▼                  ▼
  ┌─────────────┐  ┌─────────────┐   ┌─────────────┐
  │  Worker 1   │  │  Worker 2   │   │  Worker 3   │
  │  :9091      │  │  :9092      │   │  :9093      │
  └─────────────┘  └─────────────┘   └─────────────┘
```

1. **Configure** — Controller sends JMX plan + VU count to each worker via gRPC
2. **Coordinated start** — All workers receive `startAt = now + 5s` for synchronized begin
3. **Stream results** — Workers stream `SampleResultBatch` (with HDR histograms) back to controller
4. **Merge** — Controller merges histograms for accurate percentile aggregation

VU distribution:
- **DIVIDE mode** (default): 300 VUs / 3 workers = 100 VUs each
- **MULTIPLY mode**: 300 VUs x 3 workers = 900 VUs total

### Option A — Docker Compose (easiest)

```bash
make distributed
```

Or manually:
```bash
docker compose -f docker-compose.distributed.yml up --build
```

This starts:
- **controller** on port 8080 (preconfigured with `WORKER_ADDRESSES=worker-1:9090,worker-2:9090,worker-3:9090`)
- **worker-1**, **worker-2**, **worker-3** on internal gRPC port 9090

Open `http://localhost:8080` — the controller's UI. Import a test plan and click Run. The controller automatically distributes to all 3 workers.

### Option B — Local workers (no Docker)

Start workers as separate Gradle processes:

```bash
# Terminal 1: Start backend (controller)
make run

# Terminal 2: Start 3 workers
./scripts/start-local-workers.sh 3
```

Workers start on:
- Worker 1: gRPC=9091, HTTP=8091
- Worker 2: gRPC=9092, HTTP=8092
- Worker 3: gRPC=9093, HTTP=8093

Check workers are running:
```bash
curl http://localhost:8091/actuator/health  # Worker 1
curl http://localhost:8092/actuator/health  # Worker 2
curl http://localhost:8093/actuator/health  # Worker 3
```

Now trigger a distributed run via API:

```bash
# Step 1: Create a plan
PLAN_ID=$(curl -s -X POST http://localhost:8080/api/v1/plans \
  -H 'Content-Type: application/json' \
  -d '{"name":"distributed-test"}' | python3 -c "import json,sys; print(json.load(sys.stdin)['id'])")

echo "Plan ID: $PLAN_ID"

# Step 2: Upload JMX
curl -s -X PUT "http://localhost:8080/api/v1/plans/${PLAN_ID}" \
  -H 'Content-Type: application/json' \
  -d "{\"treeData\":$(python3 -c "import json; print(json.dumps(open('test-plans/http-smoke.jmx').read()))")}"

# Step 3: Start distributed run (specify worker addresses)
curl -s -X POST http://localhost:8080/api/v1/runs \
  -H 'Content-Type: application/json' \
  -d "{
    \"planId\": \"${PLAN_ID}\",
    \"virtualUsers\": 300,
    \"durationSeconds\": 60,
    \"workerAddresses\": [\"localhost:9091\", \"localhost:9092\", \"localhost:9093\"]
  }" | python3 -m json.tool
```

Stop workers:
```bash
./scripts/stop-local-workers.sh
```

### Option C — Kubernetes (production)

```bash
# Basic install with 5 workers
helm install b3meter ./deploy/helm/b3meter --set worker.replicaCount=5

# With autoscaling (2-10 workers, scales on CPU)
helm install b3meter ./deploy/helm/b3meter \
  --set worker.autoscaling.enabled=true \
  --set worker.autoscaling.minReplicas=2 \
  --set worker.autoscaling.maxReplicas=10

# With persistent storage
helm install b3meter ./deploy/helm/b3meter \
  --set persistence.enabled=true \
  --set persistence.storageClass=gp3

# Access the UI
kubectl port-forward svc/b3meter-controller 8080:8080 3000:3000
```

Default resource limits:
- Controller: 500m-2000m CPU, 512Mi-2Gi RAM
- Workers: 1000m-4000m CPU, 1Gi-4Gi RAM

Grafana dashboard available at `deploy/grafana/b3meter-dashboard.json`.

---

## 5. CLI Reference (Headless Mode)

Run tests without the web UI — ideal for CI/CD:

```bash
# Basic run
./gradlew :modules:engine-adapter:run \
  --args="-n -t test-plans/http-smoke.jmx"

# With JTL output
./gradlew :modules:engine-adapter:run \
  --args="-n -t test-plans/http-smoke.jmx -l results.jtl"

# With HTML report generation
./gradlew :modules:engine-adapter:run \
  --args="-n -t test-plans/http-smoke.jmx -l results.jtl -e -o ./report"

# Override properties
./gradlew :modules:engine-adapter:run \
  --args="-n -t test-plans/http-smoke.jmx -Jthreads=50 -Jduration=120"

# Generate report from existing JTL (no test run)
./gradlew :modules:engine-adapter:run \
  --args="-g results.jtl -o ./report"
```

| Flag | Description |
|------|-------------|
| `-n` | Non-GUI mode (required for headless) |
| `-t <file>` | Test plan `.jmx` file |
| `-l <file>` | Results log file (`.jtl`) |
| `-e` | Generate HTML report after run |
| `-o <dir>` | Report output directory |
| `-g <jtl>` | Generate report from existing JTL |
| `-J key=val` | Property override (repeatable) |
| `-p <file>` | Additional properties file |

Exit codes: `0` = success, `1` = test failure, `2` = config error.

---

## 6. Designing Your Own Tests

### Option A — Web UI (Visual Editor)

1. Open `http://localhost:3000`
2. Click **New** in the toolbar
3. The visual editor shows a tree: `Test Plan > Thread Group`
4. Click the Thread Group to set:
   - **Virtual Users** (threads)
   - **Ramp-up Period** (seconds)
   - **Duration** (seconds)
5. Right-click Thread Group > **Add Sampler** > choose protocol (HTTP, WebSocket, gRPC, etc.)
6. Configure the sampler (URL, method, headers, body)
7. Add assertions (right-click sampler > Add Assertion)
8. Click **Run**
9. Switch to **Results** tab to see live metrics

### Option B — Import JMX

b3meter reads standard Apache JMeter `.jmx` files:
1. Click **Import** in the toolbar
2. Select any `.jmx` file
3. The visual editor shows the parsed tree
4. Modify if needed, then click **Run**

### Option C — Write JMX by Hand

A minimal HTTP test plan:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan version="1.2">
  <hashTree>
    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="My Test">
      <boolProp name="TestPlan.functional_mode">false</boolProp>
    </TestPlan>
    <hashTree>
      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="Users">
        <intProp name="ThreadGroup.num_threads">10</intProp>
        <intProp name="ThreadGroup.ramp_time">5</intProp>
        <boolProp name="ThreadGroup.scheduler">true</boolProp>
        <stringProp name="ThreadGroup.duration">60</stringProp>
      </ThreadGroup>
      <hashTree>
        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="GET Homepage">
          <stringProp name="HTTPSampler.domain">example.com</stringProp>
          <stringProp name="HTTPSampler.port">443</stringProp>
          <stringProp name="HTTPSampler.protocol">https</stringProp>
          <stringProp name="HTTPSampler.path">/</stringProp>
          <stringProp name="HTTPSampler.method">GET</stringProp>
        </HTTPSamplerProxy>
        <hashTree>
          <ResponseAssertion guiclass="AssertionGui" testclass="ResponseAssertion" testname="Assert 200">
            <intProp name="Assertion.test_type">2</intProp>
            <stringProp name="Assertion.test_field">Assertion.response_code</stringProp>
            <collectionProp name="Asserion.test_strings">
              <stringProp>200</stringProp>
            </collectionProp>
          </ResponseAssertion>
          <hashTree/>
        </hashTree>
      </hashTree>
    </hashTree>
  </jmeterTestPlan>
</jmeterTestPlan>
```

Save as `my-test.jmx` and run:
```bash
./gradlew :modules:engine-adapter:run --args="-n -t my-test.jmx"
```

### Option D — API-driven (programmatic)

```bash
# 1. Create plan
PLAN_ID=$(curl -s -X POST http://localhost:8080/api/v1/plans \
  -H 'Content-Type: application/json' \
  -d '{"name":"my-api-test"}' | python3 -c "import json,sys; print(json.load(sys.stdin)['id'])")

# 2. Upload JMX content
JMX_CONTENT=$(python3 -c "import json; print(json.dumps(open('test-plans/http-smoke.jmx').read()))")
curl -s -X PUT "http://localhost:8080/api/v1/plans/${PLAN_ID}" \
  -H 'Content-Type: application/json' \
  -d "{\"treeData\":${JMX_CONTENT}}"

# 3. Start run
RUN_ID=$(curl -s -X POST http://localhost:8080/api/v1/runs \
  -H 'Content-Type: application/json' \
  -d "{\"planId\":\"${PLAN_ID}\",\"virtualUsers\":10,\"durationSeconds\":30}" \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['id'])")

echo "Run started: $RUN_ID"

# 4. Monitor (poll every 2s)
while true; do
  STATUS=$(curl -s "http://localhost:8080/api/v1/runs/${RUN_ID}" \
    | python3 -c "import json,sys; print(json.load(sys.stdin)['status'])")
  echo "Status: $STATUS"
  [ "$STATUS" = "COMPLETED" ] || [ "$STATUS" = "STOPPED" ] || [ "$STATUS" = "ERROR" ] && break
  sleep 2
done

# 5. Get final metrics
curl -s "http://localhost:8080/api/v1/runs/${RUN_ID}/metrics" | python3 -m json.tool
```

> **Important:** `planId` is always a UUID returned by the API — never a human-readable name. Use `virtualUsers` (not `threadCount`) in the request body.

### SLA Enforcement

Add SLA thresholds to your run request:

```bash
curl -s -X POST http://localhost:8080/api/v1/runs \
  -H 'Content-Type: application/json' \
  -d "{
    \"planId\": \"${PLAN_ID}\",
    \"virtualUsers\": 100,
    \"durationSeconds\": 300,
    \"slaP95Ms\": 500,
    \"slaP99Ms\": 1000,
    \"slaAvgMs\": 200,
    \"slaMaxErrorPercent\": 1.0
  }"
```

Check violations during or after the run:
```bash
curl -s "http://localhost:8080/api/v1/runs/${RUN_ID}/sla" | python3 -m json.tool
```

---

## 7. Available Test Plans

b3meter ships with ready-to-use test plans:

| Plan | Protocol | VUs | What it Tests |
|------|----------|-----|---------------|
| `http-smoke.jmx` | HTTP | 2 | Basic GET/POST |
| `ws-smoke.jmx` | WebSocket | 2 | Connect + send/receive |
| `sse-smoke.jmx` | SSE | 2 | Event stream subscription |
| `hls-smoke.jmx` | HLS | 2 | Manifest + segment fetch |
| `mqtt-smoke.jmx` | MQTT | 2 | Publish + subscribe |
| `grpc-smoke.jmx` | gRPC | 3 | Unary RPC call |
| `dash-smoke.jmx` | DASH | 2 | MPD + segment fetch |
| `stun-smoke.jmx` | STUN | 2 | STUN binding request |
| `webrtc-smoke.jmx` | WebRTC | 2 | Signaling exchange |
| `ftp-smoke.jmx` | FTP | 2 | Upload + download |
| `ldap-smoke.jmx` | LDAP | 2 | Search + bind |
| `tcp-smoke.jmx` | TCP | 2 | Echo exchange |
| `smtp-smoke.jmx` | SMTP | 2 | Send email |
| `wikipedia-10k.jmx` | HTTP | 10 | 10K requests to Wikipedia |
| `mixed-load-100vu.jmx` | Multi | 100 | All protocols mixed |
| `all-protocols-2min.jmx` | All | varied | 2-minute all-protocol run |

---

## 8. Metrics and Monitoring

### Prometheus (default pull endpoint)

b3meter exposes metrics at `http://localhost:9270/metrics`:
```bash
curl http://localhost:9270/metrics
```

### InfluxDB

Set in `application.yml` or environment:
```yaml
jmeter:
  outputs: influxdb
  output:
    influxdb:
      url: http://localhost:8086
      database: jmeter
```

### Grafana Dashboard

Import `deploy/grafana/b3meter-dashboard.json` into your Grafana instance.

### CSV / JSON output

```yaml
jmeter:
  outputs: csv    # or json
  output:
    csv:
      file: results.csv
    json:
      file: results.jsonl
```

---

## 9. Troubleshooting

| Problem | Solution |
|---------|----------|
| `java: command not found` | Install Java 21: `brew install openjdk@21` |
| Port 8080 in use | `kill -9 $(lsof -t -i :8080)` |
| Port 3000 in use | `kill -9 $(lsof -t -i :3000)` |
| First build is slow (3-5 min) | Normal — Gradle downloads dependencies. Subsequent builds are fast. |
| Mock server shows DOWN | Check Docker: `docker ps --filter name=b3-meter` — restart unhealthy containers |
| MQTT mock always DOWN | Ensure health port (9884) is mapped in `docker-compose.test.yml` |
| Smoke test says SKIP | Mock server health endpoint unreachable. Check port mappings and `.env.test` |
| `COMPLETED` but 0 samples | Test plan not loaded. Check JMX was uploaded via API or imported via UI. |
| Workers not connecting | Verify gRPC ports (9091+) are open. Check `curl http://localhost:8091/actuator/health`. |
| `QuotaExceededException` | Too many concurrent runs. Stop old runs first or increase `jmeter.quota.max-concurrent-runs`. |
| Run stuck in RUNNING | Use `POST /api/v1/runs/{id}/stop-now` to force abort. |
