# Research 011 — OSS Licensing Audit Findings

**Status**: Complete
**Date**: 2026-03-29
**Produced by**: INVESTIGATOR

---

## 1. Is Apache 2.0 Still Alive?

Yes. Apache License 2.0 is intentionally frozen, not deprecated.

The OSI approved Apache 2.0 in 2004. The Apache Software Foundation has not released a version 3.0 and has explicitly stated there are no plans to do so. "Frozen" means the license text is stable and final — this is a deliberate property, not neglect. It is one of the three most widely used OSS licenses in the world (alongside MIT and GPL-2.0+), and its adoption has accelerated among cloud-native and enterprise infrastructure projects. For a load testing tool targeting enterprise JVM shops, Apache 2.0 is the natural fit.

---

## 2. Decision Rationale: Apache 2.0 vs MIT

| Criterion | Apache 2.0 | MIT | Winner |
|---|---|---|---|
| Explicit patent grant | Yes — Section 3 grants patent license | No | Apache 2.0 |
| Enterprise legal team acceptance | Very high (Fortune 500 approved-list staple) | High | Apache 2.0 |
| Retaliation clause (patent troll protection) | Yes — Section 3 terminates patent grant on litigation | No | Apache 2.0 |
| Ecosystem fit (Spring, gRPC, Kafka, K8s) | All Apache 2.0 | Mixed | Apache 2.0 |
| Compatibility with GPL-2.0+ | Incompatible (one-way) | Compatible | MIT |
| Compatibility with GPL-3.0 | Compatible (one-way) | Compatible | Tie |
| Contributor friction | Slightly higher (NOTICE maintenance) | Lower | MIT |
| NOTICE file required | Yes | No | MIT |
| Closest competitor alignment | Gatling OSS: Apache 2.0 | k6: AGPL-3.0 | Apache 2.0 |
| Trademark protection language | Section 6 (explicit) | None | Apache 2.0 |
| SPDX identifier | `Apache-2.0` | `MIT` | Tie |
| Length / readability | Longer | Short | MIT |

**Score: Apache 2.0 wins 8 criteria, MIT wins 2, 4 ties.**

The decisive factor for a load testing tool used in regulated industries is the explicit patent grant. Enterprise procurement and legal teams routinely require it. MIT's lack of patent language creates uncertainty that slows or blocks adoption in financial services, healthcare, and government sectors.

---

## 3. Dependency License Summary

All runtime dependencies are Apache 2.0 or compatible. No GPL constraints apply.

| Dependency | License | Notes |
|---|---|---|
| Spring Boot / Spring Framework | Apache 2.0 | No constraints |
| gRPC-Java | Apache 2.0 | No constraints |
| Jackson (FasterXML) | Apache 2.0 | No constraints |
| Kotlin stdlib / coroutines | Apache 2.0 | No constraints |
| jOOQ | Apache 2.0 (community edition) | No constraints |
| Flyway (community) | Apache 2.0 | No constraints |
| Apache HttpClient5 | Apache 2.0 | No constraints |
| Micrometer | Apache 2.0 | No constraints |
| jjwt | Apache 2.0 | No constraints |
| picocli | Apache 2.0 | No constraints |
| Logback | LGPL 2.1 / EPL 1.0 (dual) | Compatible; dynamically linked; no outbound constraint |
| H2 Database | MPL-2.0 / EPL-1.0 (dual) | Compatible; no outbound constraint; used for embedded test storage |
| javax.annotation-api | CDDL 1.0 / GPL 2.0+CE (dual) | Compile-only; no runtime distribution; future: replace with `jakarta.annotation-api` (Apache 2.0) |

**Key finding**: No GPL dependencies exist. The license choice is unconstrained. No Apache JMeter source code has been copied — this is a clean reimplementation.

---

## 4. Trademark Risk: "jMeter Next"

This is the single blocking issue for public launch.

**The risk**: "JMeter" and the JMeter logo are registered trademarks of the Apache Software Foundation. The ASF Trademark Policy explicitly prohibits use of ASF project marks as the primary name of a software product, even when the software is compatible with or inspired by the ASF project. Using "jMeter Next" as the product name creates a clear policy violation and potential legal exposure.

**The ASF policy language** (paraphrased): You may not use an ASF mark as the name of your own product. You may use it in a descriptive phrase to indicate compatibility, e.g., "Plugin for Apache JMeter" or "Supports Apache JMeter JMX format."

**What is allowed**:
- "Supports Apache JMeter JMX test plan format"
- "Compatible with Apache JMeter .jmx files"
- A new product name that is entirely distinct from "JMeter"

**What is not allowed**:
- "jMeter Next" as a product name
- "B3Meter" as a product name
- "NextJMeter", "JMeter Pro", "JMeter NG", etc.

**Remediation**: Select a new project name that has no "JMeter" or "jMeter" component as a primary identifier. Retain JMX compatibility references in documentation using the approved descriptive phrasing above.

---

## 5. NOTICE File Requirement

Apache License 2.0 Section 4(d) states:

> If the Work includes a "NOTICE" text file as part of its distribution, [...] You must include a readable copy of the attribution notices contained within such NOTICE file.

Because the project bundles Apache 2.0 components that themselves ship NOTICE files (Spring, gRPC, etc.), the project must aggregate and include those attributions. There is no NOTICE file in the repository today. One must be created before the first binary distribution (JAR, Docker image, or release archive).

The minimum required content is documented in the spec (FR-003) and reflected in T-011-002.

---

## 6. Recommendations Summary

| Priority | Action | Spec Ref |
|---|---|---|
| BLOCKING | Rename project — drop "JMeter" from product name | FR-002, T-011-001 |
| HIGH | Create NOTICE file before any binary distribution | FR-003, T-011-002 |
| MEDIUM | Update group ID after rename | FR-004, T-011-006 |
| LOW | Add Gradle license-report task for Docker images | FR-005, T-011-008 |
| LOW | Update docs to trademark-safe JMX compatibility phrasing | FR-006, T-011-009 |
| LOW | Replace javax.annotation-api with jakarta.annotation-api | — (cleanup) |
