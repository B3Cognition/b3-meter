# ADR-003: HDR Histogram for Distributed Percentile Aggregation

**Status**: Accepted
**Date**: 2026-03-29 (reverse-engineered from codebase)
**Deciders**: Project owner (Ladislav Bihari)

## Context

In distributed load testing, multiple worker nodes each collect response time samples. The controller must aggregate these into global metrics. The question is: how to accurately compute global p95/p99 percentiles across workers without each worker sending every raw sample (too much data) or averaging per-worker percentile values (mathematically incorrect)?

## Decision Drivers

- Accuracy: p99 of merged data must reflect the true 99th percentile, not an average of per-worker p99 values
- Network efficiency: Workers should not stream every individual sample to the controller
- Coordinated omission: High-latency responses during saturation must not be hidden
- Established tool: Solution should be well-tested and not require novel math

## Considered Options

### Option 1: Average per-worker percentile values

`global_p99 = avg(worker1_p99, worker2_p99, ..., workerN_p99)`

**Pros**:
- Simple; no serialization overhead

**Cons**:
- Mathematically wrong: average of percentiles is not the percentile of the combined distribution
- Example: worker1 p99=100ms, worker2 p99=5000ms → avg says 2550ms, but true merged p99 may be 4900ms
- Hides the worst-case worker's contribution

### Option 2: HdrHistogram with serialized histogram bytes

Each worker encodes its response time histogram as `hdr_histogram` bytes and sends to the controller. Controller uses `HdrHistogram.add()` to merge histograms, then reads percentiles from the merged histogram.

**Pros**:
- Mathematically exact: merged histogram gives true global percentile
- Network efficient: histogram is compact (sparse RLE encoding)
- Battle-tested: HdrHistogram is used by Gatling, k6, and others
- Handles coordinated omission correctly when used with corrected recording

**Cons**:
- Requires HdrHistogram dependency
- Histogram precision is configured (value range and significant digits)
- Slightly more complex serialization than a simple double

### Option 3: Reservoir sampling

Send a random sample of individual measurements from each worker.

**Pros**:
- True individual samples

**Cons**:
- Biased at extremes (p99 requires many samples to be representative)
- High network overhead for accurate p99

## Decision

**Option 2: HDR Histogram** was chosen.

Source evidence: `worker.proto:147-151` includes `hdr_histogram bytes` field in `SampleResultBatch`; `HdrHistogramAccumulator.java` in engine-service; `ResultAggregator.java` in distributed-controller.

The `percentiles` map is retained in `SampleResultBatch` as a backward-compatibility fallback when `hdr_histogram` is empty.

## Consequences

**Positive**:
- Accurate p95/p99 regardless of worker count or distribution shape
- Compact wire format
- Consistent with industry standards (k6, Gatling)

**Negative**:
- Requires HdrHistogram library in engine-service (but it's a minimal JDK-compatible library)
- Histogram precision must be carefully configured to avoid memory bloat

**Risks**:
- If workers send empty `hdr_histogram`, controller falls back to averaging (per fallback path in proto) — this is less accurate but acceptable for degraded-mode operation

## Enforcement

FR-004.005 (spec) mandates histogram merge. SC-002.005 (spec) requires <±1% accuracy vs exact sort.

## Related

- [constitution.md](../constitution.md) — Principle III (accurate percentiles)
- Source: `worker.proto:147-151`, `HdrHistogramAccumulator.java`, `ResultAggregator.java`
