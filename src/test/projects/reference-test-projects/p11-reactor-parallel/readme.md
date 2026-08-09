# P11 — reactor-parallel

**Unique behavior:** `-T N` concurrent execution; cache thread-safety.

## Setup

Five-module flat reactor with a diamond-like dependency graph:

```
root/
  ├── util/       (independent)
  ├── model/      (independent)
  ├── service-a/  (depends on util)
  ├── service-b/  (depends on model)
  └── app/        (depends on service-a + service-b)
```

`util` and `model` can build concurrently. `service-a` and `service-b` can build concurrently
after their respective dependencies complete. `app` waits for both services.

The build cache extension is loaded as a **core extension** via `.mvn/extensions.xml`.

## What it verifies

- No race condition in concurrent key calculation
- No file-locking conflict when two threads write to the local cache store simultaneously
- Cache reads/writes are atomic — no partial entries read by a competing thread
- `-T 4` produces identical artifacts to `-T 1`

## How to run

```bash
mvn verify -T 4
mvn verify -T 4   # → all modules cache hit

# Modify util source only, then:
mvn verify -T 4
# → util + service-a miss; model + service-b hit; app rebuilds
```
