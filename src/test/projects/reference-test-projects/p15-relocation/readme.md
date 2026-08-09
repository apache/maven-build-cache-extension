# P15 — relocation

**Unique behavior:** Consuming a dependency whose POM contains `<relocation>` metadata.

## Setup

Three components:
- `_relocated-artifact/`: POM with `<distributionManagement><relocation>` pointing to `new-artifact:2.0`
- `_new-artifact/`: The actual JAR artifact at the new coordinates
- Main project `pom.xml`: declares dependency on `old-artifact:1.0` (triggers relocation)

Both helper artifacts must be installed locally before the main build.

The build cache extension is loaded as a **core extension** via `.mvn/extensions.xml`.

## What it verifies

- Maven prints a relocation warning; build still succeeds
- Cache key uses the **resolved** coordinates (`new-artifact:2.0`) not the declared ones
- Declaring `new-artifact:2.0` directly → same cache key as via relocation (cache HIT)
- Upgrading `new-artifact` to `2.1` → cache miss

## How to run

```bash
# Step 1: install helper artifacts
cd _relocated-artifact && mvn install && cd ..
cd _new-artifact && mvn install && cd ..

# Step 2: build main project
mvn verify
mvn verify   # → cache hit
```
