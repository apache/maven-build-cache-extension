# P03 — remote-parent

**Unique behavior:** Empty `<relativePath/>` → parent fetched from repository.

## Setup

Single-module JAR. Parent is `org.apache:apache:29` from Maven Central.
`<relativePath/>` is explicitly empty (forces remote lookup, not local filesystem search).

The build cache extension is loaded as a **core extension** via `.mvn/extensions.xml`.

## What it verifies

- Remote parent resolved from local repo; its contribution to the effective POM is stable
- Bumping `<parent><version>` → cache miss
- Downgrading back → cache hit (key is symmetric)

## Prerequisites

- Internet access (or `org.apache:apache:29` in local Maven cache)

## How to run

```bash
mvn verify
mvn verify   # → should be cache hit
```
