# Metrics Architecture Specification

**Version**: 1.0
**Date**: 2026-03-25
**Status**: Draft

---

## 1. Metrics Pipeline

### End-to-End Data Flow

```
VU Thread (virtual thread)
  |
  v
SamplerExecutor.execute(PlanNode, variables)
  |  Returns: SampleResult {label, success, statusCode, connectTimeMs,
  |           latencyMs, totalTimeMs, responseBody, failureMessage}
  v
NodeInterpreter.executeChildren()
  |  Collects: List<SampleResult> per VU
  v
NodeInterpreter.publishBuckets(runId, results)
  |  Aggregates per label: count, errors, avg, min, max, p90, p95, p99
  |  Produces: SampleBucket record
  v
SampleStreamBroker.publish(runId, bucket)
  |  Fan-out to all registered SampleBucketConsumer instances
  |
  +---> SSE Consumer (web-api StreamingController)
  |       |  Converts SampleBucket to JSON MetricsBucket
  |       v  SSE event: type=metrics
  |     Browser (React LiveDashboard)
  |
  +---> Prometheus Consumer
  |       |  Updates Micrometer gauges/counters
  |       v  Scraped at /actuator/prometheus
  |     Prometheus + Grafana
  |
  +---> JSON Report Consumer
  |       |  Appends to per-run JSON file
  |       v  Written on test completion
  |     report-{runId}.json
  |
  +---> SLA Evaluator Consumer
  |       |  Checks bucket against SLA rules
  |       v  Emits SSE error event if SLA breached
  |     CI exit code (non-zero on breach)
  |
  +---> HDR Histogram Consumer
          |  Records totalTimeMs into per-label histogram
          v  Merged across workers in distributed mode
        Histogram snapshot exported on test end
```

### Aggregation Cadence

The current implementation publishes a single aggregate bucket per label after the
entire test run completes (`NodeInterpreter.publishBuckets()`). This must be changed
to publish **per-second rolling buckets** during the test:

#### Rolling Aggregator Design

```java
/**
 * Accumulates SampleResults and publishes SampleBuckets every 1 second.
 *
 * Sits between the VU threads and the SampleStreamBroker. Each VU calls
 * record(result) after every sample; a background timer thread publishes
 * aggregated buckets once per second.
 */
public final class SampleBucketAggregator implements AutoCloseable {
    // Per-label accumulators (lock-free, ConcurrentHashMap + LongAdder)
    private final ConcurrentHashMap<String, LabelAccumulator> accumulators;

    // Background scheduler (ScheduledExecutorService, 1 thread)
    private final ScheduledExecutorService scheduler;

    // Target broker
    private final SampleStreamBroker broker;
    private final String runId;

    /** Called by VU threads (lock-free). */
    public void record(SampleResult result) { ... }

    /** Called every 1 second by scheduler. Drains accumulators, publishes buckets. */
    private void flush() { ... }

    /** Called at test end. Flushes remaining data, shuts down scheduler. */
    @Override public void close() { ... }
}
```

The `LabelAccumulator` uses `LongAdder` for sample count and error count, and an
`HDRHistogram` (or `ConcurrentHistogram`) for response time distribution. The `flush()`
method snapshots each accumulator, computes percentiles from the histogram, and publishes
a `SampleBucket` via the broker.

---

## 2. Per-Protocol Metrics Matrix

### Legend

- **Yes** = This metric is measured for this protocol
- **No** = Not applicable to this protocol
- **Derived** = Computed from other measured values

| Metric | HTTP | WebSocket | HLS | SSE | gRPC | MQTT | WebRTC |
|--------|------|-----------|-----|-----|------|------|--------|
| **Connect latency** | Yes: TCP+TLS handshake time from `HttpResponse.connectTimeMs()` | Yes: Time from TCP SYN to WS upgrade 101 | Yes: Measured on first HTTP request (master playlist) | Yes: Time to establish HTTP connection for event stream | Yes: HTTP/2 channel establishment time (first RPC on new channel) | Yes: Time from TCP connect to CONNACK packet | Yes: Time from signaling start to DTLS handshake complete |
| **First byte latency** | Yes: Time to first response byte from `HttpResponse.latencyMs()` | Yes: Time from send to first frame received | Yes: Time to first byte of master M3U8 response | Yes: Time from connection to first SSE event received | Yes: Time from RPC invoke to first response byte (or first stream message) | No: MQTT is message-based, not byte-stream | Yes: Time from SDP answer to first RTP packet |
| **Response time** | Yes: Total HTTP request/response cycle from `SampleResult.totalTimeMs` | Yes: Total time from connect through send to last expected message received | Yes: Total time from master playlist fetch through all segment downloads | Yes: Total listen duration (connect through listen_duration or expected_events) | Yes: Total RPC time from invoke to final response (unary) or last stream message | Yes: Time from PUBLISH to final ACK (PUBACK for QoS 1, PUBCOMP for QoS 2) | Yes: Total time from signaling to end of listen_duration |
| **Throughput (req/s)** | Yes: HTTP requests completed per second from `SampleBucket.samplesPerSecond` | No: WebSocket is message-based, not request-based | Derived: Calculated as (segment_count + 2 playlist requests) / total_time | No: SSE is a single long-lived request | Yes: RPCs completed per second | No: MQTT is message-based | No: WebRTC is stream-based |
| **Throughput (msg/s)** | No: HTTP is request/response, not message stream | Yes: WebSocket frames sent+received per second | No: HLS uses HTTP requests, not messages | Yes: SSE events received per second | Yes: For streaming RPCs, messages per second in each direction | Yes: MQTT messages published or received per second | No: RTP packets/s is a better metric (see bytes/s) |
| **Throughput (bytes/s)** | Yes: Response body size / totalTimeMs | Yes: Sum of frame payload sizes / connection duration | Yes: Sum of segment sizes / total download time (effective bitrate) | Yes: Sum of event data sizes / listen duration | Yes: Sum of response protobuf sizes / RPC time | Yes: Sum of message payload sizes / duration | Yes: RTP payload bytes / listen_duration (received bitrate) |
| **Error rate** | Yes: Non-2xx responses / total, from `SampleBucket.errorPercent()` | Yes: Connection failures + unexpected closes + timeouts / total samples | Yes: Failed segment downloads + playlist errors / total requests | Yes: Connection failures + reconnections / total samples | Yes: Non-OK gRPC status codes / total RPCs | Yes: Connection failures + publish timeouts + subscribe timeouts / total samples | Yes: Signaling failures + ICE failures + DTLS failures / total samples |
| **Active connections** | Derived: VU count (HTTP connections are transient per request, pooled internally) | Yes: Count of open WebSocket connections (from VU variable map) | No: Uses HTTP connections (counted under HTTP) | Yes: Count of open SSE connections (one per VU per sample) | Yes: Count of open gRPC channels (one per VU per target) | Yes: Count of connected MQTT clients (one per VU) | Yes: Count of active PeerConnections (one per VU per sample) |
| **p50/p90/p95/p99** | Yes: Computed from HDR Histogram of totalTimeMs across all HTTP samples for a label | Yes: Computed from HDR Histogram of totalTimeMs (connect+send+receive) | Yes: Computed from HDR Histogram of per-segment download times | Yes: Computed from HDR Histogram of first-event latency (not total listen time, which is fixed) | Yes: Computed from HDR Histogram of RPC totalTimeMs | Yes: Computed from HDR Histogram of publish/subscribe latency | Yes: Computed from HDR Histogram of signaling+ICE+DTLS setup time |

### Protocol-Specific Bonus Metrics

These metrics are collected in addition to the standard matrix above. They are stored
in `SampleResult.responseBody` as JSON and exposed via a dedicated SSE event type
(`type: protocol_metrics`).

| Protocol | Bonus Metric | How Measured |
|----------|-------------|--------------|
| HLS | Buffer stall count | Segments exceeding EXT-X-TARGETDURATION |
| HLS | Bitrate switches | Count of variant switches during adaptive playback |
| HLS | Effective bitrate (kbps) | Total segment bytes / total playback time |
| SSE | Inter-event gap (max ms) | Maximum time between consecutive events |
| SSE | Reconnect count | Number of automatic reconnections |
| gRPC | Stream message count | Messages in server/client/bidi streaming RPC |
| MQTT | QoS handshake latency | Time for full QoS 2 flow (PUBLISH to PUBCOMP) |
| MQTT | Message delivery ratio | Messages received / messages published |
| WebRTC | Packet loss % | (Expected - received) / expected from RTCP SR |
| WebRTC | Jitter (ms) | Inter-arrival jitter from RTCP |
| WebRTC | Round-trip time (ms) | From RTCP receiver reports |
| WebRTC | E2E latency (ms) | NTP timestamp delta from RTCP sender reports |
| WebRTC | Frame rate (fps) | Video frames / listen_duration |

---

## 3. HDR Histogram Integration

### Recording Strategy

HDR Histograms are recorded **per sampler label** (not per VU, not per protocol type).
This matches the existing `SampleBucket` aggregation granularity where `samplerLabel`
is the grouping key.

```java
/**
 * Per-label histogram maintained by SampleBucketAggregator.
 * Uses org.HdrHistogram.ConcurrentHistogram for lock-free recording
 * from multiple VU threads.
 */
class LabelAccumulator {
    private final String label;
    private final ConcurrentHistogram histogram;
    // Range: 1ms to 3,600,000ms (1 hour), 3 significant digits
    // Memory: ~30KB per histogram instance

    LabelAccumulator(String label) {
        this.label = label;
        this.histogram = new ConcurrentHistogram(3_600_000L, 3);
    }

    void record(long totalTimeMs) {
        histogram.recordValue(Math.max(1, totalTimeMs));
    }

    // Called by flush(): snapshot + reset for the next 1-second window
    Histogram getIntervalHistogramAndReset() {
        return histogram.getIntervalHistogram();
    }
}
```

### Per-Second Window Histograms

During the test, each `flush()` call (every 1 second) takes an interval snapshot of
each label's histogram. The snapshot provides exact percentiles for that 1-second
window (published as `SampleBucket.percentile90/95/99`).

The aggregator also maintains a **cumulative histogram** per label that accumulates
all samples from test start. The cumulative histogram is used for the final test report.

### Merging Across Distributed Workers

In distributed mode, each worker node maintains its own per-label histograms. The
controller merges them using HdrHistogram's built-in `add()` operation:

```
Worker 1: ConcurrentHistogram (per label, per 5-second window)
Worker 2: ConcurrentHistogram (per label, per 5-second window)
Worker N: ConcurrentHistogram (per label, per 5-second window)
           |
           v  Serialized via SampleResultBatch.percentiles map in worker.proto
           |  (Currently sends pre-computed p50/p90/p95/p99 values)
           v
Controller: Receives percentile values per worker per window
           |
           v  Option A (approximate): Weighted average of worker percentiles
           v  Option B (exact): Workers serialize full histogram as compressed
           |  byte array in a new SampleResultBatch.histogram_data field
           v
Controller: Merged histogram → exact global percentiles
```

**Recommendation**: Use Option B (exact merge) for accuracy. Extend the
`SampleResultBatch` proto message:

```protobuf
message SampleResultBatch {
    // ... existing fields ...

    // Compressed HdrHistogram snapshot (V2 encoding, ~2-5KB per label per window)
    bytes histogram_snapshot = 7;
}
```

The controller deserializes each worker's histogram snapshot and merges them:

```java
Histogram merged = new Histogram(3_600_000L, 3);
for (byte[] snapshot : workerSnapshots) {
    Histogram workerHist = Histogram.decodeFromCompressedByteBuffer(
        ByteBuffer.wrap(snapshot), 0);
    merged.add(workerHist);
}
// merged now has exact global percentiles
double globalP99 = merged.getValueAtPercentile(99.0);
```

### Export Formats

| Format | File Extension | When Generated | Contents |
|--------|---------------|----------------|----------|
| HdrHistogram V2 | `.hgrm` | Test end | Full cumulative histogram per label, compatible with hdrhistogram.org tools |
| CSV | `.csv` | Test end | Columns: label, timestamp, count, min, max, mean, p50, p75, p90, p95, p99, p999 |
| JSON | `.json` | Test end (embedded in report) | Histogram percentile values embedded in `report-{runId}.json` |

#### HGRM Output Example

```
       Value     Percentile TotalCount 1/(1-Percentile)
      12.000 0.000000000000          1           1.00
      45.000 0.250000000000         25           1.33
      52.000 0.500000000000         50           2.00
      78.000 0.750000000000         75           4.00
      95.000 0.900000000000         90          10.00
     112.000 0.950000000000         95          20.00
     198.000 0.990000000000         99         100.00
     312.000 0.999000000000        100        1000.00
#[Mean    =       58.420, StdDeviation   =       32.100]
#[Max     =      312.000, Total count    =          100]
#[Buckets =           18, SubBuckets     =         2048]
```

---

## 4. SSE Event Schema Extension

The existing `MetricsBucket` SSE event type continues to carry per-second aggregated
metrics. A new event type is added for protocol-specific extended metrics:

```typescript
// New SSE event type for protocol-specific metrics
export interface ProtocolMetrics {
  timestamp: string;
  samplerLabel: string;
  protocol: 'HTTP' | 'WebSocket' | 'HLS' | 'SSE' | 'gRPC' | 'MQTT' | 'WebRTC' | 'DASH';
  metrics: Record<string, number | string>;
  // Examples:
  // HLS:    { bufferStalls: 0, effectiveBitrateKbps: 2800, segmentCount: 3 }
  // WebRTC: { packetLossPct: 0.5, jitterMs: 12, rttMs: 45, framesPerSec: 30 }
  // MQTT:   { qosHandshakeMs: 25, deliveryRatio: 1.0 }
}

// Extended SseEvent union
export type SseEvent =
  | { type: 'metrics'; data: MetricsBucket }
  | { type: 'protocol_metrics'; data: ProtocolMetrics }
  | { type: 'run_status'; data: RunStatusEvent }
  | { type: 'worker_status'; data: WorkerStatusEvent }
  | { type: 'error'; data: ErrorEvent }
  | { type: 'histogram_snapshot'; data: HistogramSnapshot }
  | { type: 'ping' };

export interface HistogramSnapshot {
  runId: string;
  samplerLabel: string;
  percentiles: {
    p50: number;
    p75: number;
    p90: number;
    p95: number;
    p99: number;
    p999: number;
  };
  min: number;
  max: number;
  mean: number;
  stddev: number;
  totalCount: number;
}
```

---

## 5. Prometheus Metrics Export

The Prometheus consumer exposes metrics at `/actuator/prometheus` using Micrometer:

```
# HTTP Sampler metrics
jmeter_sampler_requests_total{label="HTTP Request - Login", status="success"} 1523
jmeter_sampler_requests_total{label="HTTP Request - Login", status="error"} 12
jmeter_sampler_response_time_seconds{label="HTTP Request - Login", quantile="0.5"} 0.052
jmeter_sampler_response_time_seconds{label="HTTP Request - Login", quantile="0.9"} 0.095
jmeter_sampler_response_time_seconds{label="HTTP Request - Login", quantile="0.95"} 0.112
jmeter_sampler_response_time_seconds{label="HTTP Request - Login", quantile="0.99"} 0.198
jmeter_sampler_active_connections{label="WS Subscribe", protocol="websocket"} 50
jmeter_sampler_bytes_total{label="HLS Playback", direction="received"} 15728640
jmeter_sampler_messages_total{label="MQTT Publish", direction="sent"} 5000
jmeter_sampler_messages_total{label="MQTT Subscribe", direction="received"} 4998

# Run-level metrics
jmeter_run_virtual_users{run_id="abc123"} 50
jmeter_run_duration_seconds{run_id="abc123"} 60.5
jmeter_run_status{run_id="abc123", status="running"} 1

# Protocol-specific metrics
jmeter_hls_buffer_stalls_total{label="HLS Playback"} 0
jmeter_hls_effective_bitrate_kbps{label="HLS Playback"} 2800
jmeter_webrtc_packet_loss_percent{label="WebRTC Stream"} 0.5
jmeter_webrtc_jitter_ms{label="WebRTC Stream"} 12
jmeter_mqtt_delivery_ratio{label="MQTT PubSub"} 0.999
```

### Micrometer Integration

```java
@Component
public class PrometheusMetricsConsumer implements SampleBucketConsumer {

    private final MeterRegistry registry;

    @Override
    public void onBucket(SampleBucket bucket) {
        // Counter: total samples
        registry.counter("jmeter_sampler_requests",
            "label", bucket.samplerLabel(),
            "status", "success")
            .increment(bucket.sampleCount() - bucket.errorCount());

        registry.counter("jmeter_sampler_requests",
            "label", bucket.samplerLabel(),
            "status", "error")
            .increment(bucket.errorCount());

        // Timer: response time distribution
        // (Uses Micrometer's DistributionSummary with percentile histograms)
        DistributionSummary.builder("jmeter_sampler_response_time")
            .tag("label", bucket.samplerLabel())
            .publishPercentiles(0.5, 0.9, 0.95, 0.99)
            .register(registry)
            .record(bucket.avgResponseTime());
    }
}
```

---

## 6. JSON Report Format

Generated at test completion, one file per run:

```json
{
  "runId": "abc-123-def",
  "planId": "my-test-plan",
  "startedAt": "2026-03-25T10:00:00Z",
  "endedAt": "2026-03-25T10:01:00Z",
  "durationMs": 60000,
  "virtualUsers": 50,
  "status": "STOPPED",
  "summary": {
    "totalSamples": 15230,
    "totalErrors": 12,
    "errorRate": 0.079,
    "overallThroughput": 253.8
  },
  "samplers": [
    {
      "label": "HTTP Request - Login",
      "protocol": "HTTP",
      "sampleCount": 5000,
      "errorCount": 5,
      "errorRate": 0.1,
      "throughput": 83.3,
      "responseTime": {
        "min": 12,
        "max": 312,
        "mean": 58.4,
        "stddev": 32.1,
        "p50": 52,
        "p75": 78,
        "p90": 95,
        "p95": 112,
        "p99": 198,
        "p999": 312
      },
      "connectTime": {
        "min": 1,
        "max": 45,
        "mean": 5.2,
        "p95": 12
      },
      "bytesReceived": 2560000,
      "bytesSent": 500000
    },
    {
      "label": "WS Subscribe - Sports",
      "protocol": "WebSocket",
      "sampleCount": 250,
      "errorCount": 0,
      "errorRate": 0.0,
      "messagesReceived": 750,
      "messagesSent": 250,
      "responseTime": {
        "min": 45,
        "max": 520,
        "mean": 120.5,
        "p50": 105,
        "p90": 200,
        "p95": 310,
        "p99": 480
      },
      "connectTime": {
        "min": 3,
        "max": 85,
        "mean": 12.3,
        "p95": 35
      },
      "activeConnections": 50
    }
  ],
  "sla": {
    "rules": [
      {"metric": "p95", "sampler": "HTTP Request - Login", "threshold": 500, "passed": true, "actual": 112},
      {"metric": "errorRate", "sampler": "*", "threshold": 1.0, "passed": true, "actual": 0.079}
    ],
    "overallPassed": true
  },
  "timeSeries": [
    {
      "timestamp": "2026-03-25T10:00:01Z",
      "label": "HTTP Request - Login",
      "sampleCount": 83,
      "errorCount": 0,
      "avgResponseTime": 55.2,
      "p95": 98,
      "throughput": 83
    }
  ]
}
```

---

## 7. SLA Evaluator

The SLA evaluator is a `SampleBucketConsumer` that checks each bucket against
configured rules and emits violations as SSE error events:

### SLA Rule Format (in JMX or runtime config)

```json
{
  "slaRules": [
    {"metric": "p95", "samplerLabel": "*", "operator": "lt", "threshold": 500},
    {"metric": "p99", "samplerLabel": "HTTP Request - Login", "operator": "lt", "threshold": 1000},
    {"metric": "errorRate", "samplerLabel": "*", "operator": "lt", "threshold": 1.0},
    {"metric": "throughput", "samplerLabel": "*", "operator": "gt", "threshold": 100}
  ]
}
```

### Evaluation Logic

```java
public final class SlaEvaluator implements SampleBucketConsumer {

    @Override
    public void onBucket(SampleBucket bucket) {
        for (SlaRule rule : rules) {
            if (!rule.matchesLabel(bucket.samplerLabel())) continue;

            double actual = switch (rule.metric()) {
                case "p50"        -> bucket.percentile90(); // Note: need p50 in bucket
                case "p90"        -> bucket.percentile90();
                case "p95"        -> bucket.percentile95();
                case "p99"        -> bucket.percentile99();
                case "errorRate"  -> bucket.errorPercent();
                case "throughput" -> bucket.samplesPerSecond();
                case "avg"        -> bucket.avgResponseTime();
                default -> Double.NaN;
            };

            if (!rule.evaluate(actual)) {
                // Publish violation event
                broker.publishSlaViolation(runId, rule, actual);
            }
        }
    }
}
```

SLA breaches during a test run:
1. Are published as SSE error events (visible in the UI).
2. Are accumulated in the run context.
3. At test end, if any SLA was breached, the CLI exit code is non-zero (for CI).
