# P04 — ci-friendly

**Unique behavior:** `${revision}` + `${changelist}` CI-friendly version placeholders; `flatten-maven-plugin`.

## Setup

Two-module flat reactor. Root and children all use `<version>${revision}${changelist}</version>`.
`flatten-maven-plugin` bound to `process-resources` phase to flatten the POM before packaging.
`.mvn/maven.config` sets default values: `revision=1.0` and `changelist=-SNAPSHOT`.

```
root/
  ├── module-a/   (no internal deps)
  └── module-b/   (depends on module-a)
```

The build cache extension is loaded as a **core extension** via `.mvn/extensions.xml`.

## What it verifies

- Resolved value `1.0-SNAPSHOT` (not `${revision}`) appears in the cache key
- Same version twice → cache hit
- `-Drevision=2.0 -Dchangelist=` (release build) → cache miss
- Flattened `pom.xml` under `target/` excluded from file hash

## How to run

```bash
mvn verify
mvn verify   # → cache hit

# Release build (different cache key)
mvn verify -Drevision=2.0 -Dchangelist=
```
