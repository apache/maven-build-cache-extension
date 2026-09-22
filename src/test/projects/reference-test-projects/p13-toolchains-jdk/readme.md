# P13 — toolchains-jdk

**Unique behavior:** `toolchains.xml`; `maven-toolchains-plugin`; external JDK selection.

## Setup

Single-module JAR. `maven-toolchains-plugin` bound to `validate` phase selects JDK 11.
`maven-compiler-plugin` uses `--release 11` to produce JDK 11 bytecode.

`.mvn/toolchains.xml` provides JDK 11 and JDK 17 entries — **update the paths** to match
your local JDK installations before running.

The build cache extension is loaded as a **core extension** via `.mvn/extensions.xml`.

## Prerequisites

- JDK 11 and JDK 17 installed locally
- Update `.mvn/toolchains.xml` with the correct `<jdkHome>` paths

## What it verifies

- Build with JDK 11 toolchain → cache key includes the toolchain's JDK vendor/version
- Build with JDK 17 toolchain (change version in pom.xml) → different cache key
- Same JDK, same source → cache hit
- Toolchain-provided JDK + `--release` produces stable, deterministic output

## How to run

```bash
# Using project-local toolchains.xml
mvn verify -t .mvn/toolchains.xml
mvn verify -t .mvn/toolchains.xml   # → cache hit
```
