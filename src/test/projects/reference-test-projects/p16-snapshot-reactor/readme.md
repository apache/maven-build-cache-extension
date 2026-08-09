# P16 — snapshot-reactor

**Unique behavior:** Reactor SNAPSHOT deps; external SNAPSHOT; `updatePolicy` interaction.

## Setup

Three-module flat reactor where all modules have version `1.0-SNAPSHOT`.
`module-core` depends on `module-api:1.0-SNAPSHOT` (reactor SNAPSHOT).

```
root/
  ├── module-api/    (1.0-SNAPSHOT)
  ├── module-core/   (1.0-SNAPSHOT, depends on module-api:1.0-SNAPSHOT)
  └── module-app/    (1.0-SNAPSHOT, depends on module-core:1.0-SNAPSHOT)
```

The build cache extension is loaded as a **core extension** via `.mvn/extensions.xml`.

## What it verifies

- Reactor SNAPSHOT resolved from current build, not stale local repo entry
- Build is repeatable: same snapshot reactor content → cache hit
- SNAPSHOT inside reactor is distinguishable from external SNAPSHOT
- `updatePolicy` interaction: always-update policy forces re-resolution

## How to run

```bash
mvn verify
mvn verify   # → cache hit for all three modules
```
