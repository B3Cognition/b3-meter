# Getting Started with b3meter

A step-by-step tutorial for first-time users. By the end you will have created,
executed, and inspected a test plan.

## Prerequisites

- Java 21+ (the Gradle wrapper downloads everything else)
- A terminal (macOS / Linux / WSL)

## 1. Start the Server

```bash
./gradlew :modules:web-api:bootRun
```

Open [http://localhost:3000](http://localhost:3000) in your browser.
The React SPA loads automatically.

## 2. Create Your First Test Plan

1. Click **New** in the toolbar.
2. Right-click the plan node and select **Add > Thread Group**.
3. Right-click the Thread Group and select **Add > Sampler > HTTP Request**.
4. Configure the sampler:
   - **Domain**: `example.com`
   - **Path**: `/`
   - **Method**: `GET`
5. Click **Run** to execute the plan.

## 3. View Results

While the test runs (and after it finishes) you have three views:

| View | What it shows |
|------|---------------|
| **Summary Report** | Throughput, average response time, error rate |
| **Charts** | Real-time graphs (response time, throughput, errors) |
| **View Results Tree** | Per-request details (headers, body, timing) |

## 4. Import a JMeter .jmx File

If you have existing JMeter plans:

1. Click **Import** in the toolbar.
2. Select any `.jmx` file.
3. The plan loads with all samplers, config elements, and listeners.
4. Click **Run** to execute.

You can also export back to `.jmx` via **Export** for round-trip compatibility.

## 5. Self Smoke Testing

b3meter ships with built-in mock servers to verify every protocol works:

1. Click **Self Smoke** in the toolbar.
2. Click **Start All** to launch mock HTTP, HTTPS, WebSocket, gRPC, GraphQL,
   JDBC, and MQTT servers.
3. Click **Run Self Smoke** to execute a pre-built plan that hits each server.
4. All samplers should return green (200 / success).

## 6. Distributed Mode

Run load tests across multiple workers for higher throughput:

```bash
# Start 3 local workers (ports 9091, 9092, 9093):
./scripts/start-local-workers.sh 3

# Workers auto-register with the controller.
# Start a test run from the UI — it distributes automatically.
```

For production deployments, use the controller/worker CLI:

```bash
# On the controller machine:
java -jar b3-meter.jar controller --port 8080

# On each worker machine:
java -jar b3-meter.jar worker --controller http://controller:8080
```

## Next Steps

- Read the [API Reference](api-reference.md) for programmatic access.
- Explore the plugin system: install custom samplers, listeners, and protocols.
- Set up CI/CD with exit codes and SLA evaluation (see `--ci` flag).
