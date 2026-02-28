# P07 — plugin-rebinding

**Unique behavior:** `maven-plugin` packaging; lifecycle phase rebinding; plugin classpath `<dependencies>`.

## Setup

Single module with `<packaging>maven-plugin</packaging>`. Contains a minimal `@Mojo` class.
`maven-plugin-plugin:descriptor` is rebound from the default `generate-sources` phase to `process-sources`.

The build cache extension is loaded as a **core extension** via `.mvn/extensions.xml`.

## What it verifies

- Plugin descriptor (`META-INF/maven/plugin.xml`) stored and restored as a cached artifact
- Mojo source change → cache miss; descriptor regenerated
- Rebound execution fires at `process-sources` (not default phase); cache key reflects the rebound phase
- Custom `maven-plugin` packaging lifecycle handled correctly by the extension

## How to run

```bash
mvn verify
mvn verify   # → cache hit
```
