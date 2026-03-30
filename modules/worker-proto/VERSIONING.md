# worker-proto Versioning Policy

## Schema version

The current proto schema is **v1** (proto package `b3meter.worker`, file `worker.proto`).

The schema does **not** carry an explicit version field in the package name (e.g. `b3meter.worker.v1`).
A version suffix will be added when a breaking change is introduced, following the policy below.

---

## Backward-compatible changes (allowed without a version bump)

The following changes preserve wire compatibility — older controllers can still decode messages
sent by newer workers, and vice versa:

| Change | Safe? |
|--------|-------|
| Add a new field with a **new field number** | Yes |
| Add a new `enum` value | Yes (unknown values default to 0 on decode) |
| Add a new RPC method | Yes (old stubs ignore unknown methods) |
| Rename a field (number stays the same) | Yes on the wire; updates all callers |
| Widen a numeric type (e.g. `int32` → `int64`) | Yes |
| Add a `map` field | Yes |

---

## Breaking changes (require a package version bump)

The following changes break wire compatibility and require renaming the package
(e.g. `b3meter.worker.v2`) and updating all callers:

| Change | Reason |
|--------|--------|
| Remove or reuse an existing field number | Decoding ambiguity |
| Change the type of an existing field | Corrupt decode |
| Remove an RPC method | Runtime failures in callers |
| Rename the package or service | Breaks generated stub class names |
| Change an `enum` value's number | Silent misinterpretation |

---

## Field number reservation

When a field is removed to clean up the schema, its number **must** be reserved so it
can never be accidentally reused:

```protobuf
message ExampleMessage {
    reserved 3, 7;
    reserved "old_field_name";
}
```

---

## Versioning workflow

1. **Patch** (non-breaking): update the `.proto`, regenerate, update `CHANGELOG` entry.
2. **Breaking**: introduce `b3meter.worker.v{N}` package, create `worker_v{N}.proto`,
   keep the old proto until all callers are migrated, then deprecate and remove.
3. All changes to `.proto` files must be reviewed by at least one engineer familiar with
   the distributed-controller and worker-node modules.

---

## Compatibility matrix

| worker-proto schema | distributed-controller | worker-node | Notes |
|---|---|---|---|
| v1 (current) | HEAD | HEAD | Initial production schema |

---

## Protobuf / gRPC library versions

Version strings are pinned in `gradle/libs.versions.toml` and validated at build time
by `modules/worker-proto/build.gradle.kts`.  Update both files together when upgrading.

| Library | Version |
|---------|---------|
| `com.google.protobuf:protobuf-java` | see `libs.versions.toml → protobuf` |
| `io.grpc:grpc-stub` | see `libs.versions.toml → grpc` |
| `io.grpc:grpc-protobuf` | see `libs.versions.toml → grpc` |
