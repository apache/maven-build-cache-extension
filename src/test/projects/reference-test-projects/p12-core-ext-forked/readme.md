# P12 — core-ext-forked

**Unique behavior:** Core extension (`.mvn/extensions.xml`); `maven-invoker-plugin` forked child process; session independence between parent and child.

## Setup

Parent project (multi-module) with `module-main` that triggers `maven-invoker-plugin:run`
in `integration-test` phase. The child project in `src/it/child-build/` has its own
`.mvn/extensions.xml` loading the cache extension as a **core extension**.

```
p12-core-ext-forked/
  .mvn/extensions.xml                  ← core extension (parent)
  .mvn/maven-build-cache-config.xml
  module-main/                         ← invokes maven-invoker-plugin
  src/it/child-build/                  ← forked child project
    .mvn/extensions.xml               ← core extension (child, independent session)
    .mvn/maven-build-cache-config.xml
    pom.xml
    src/main/java/.../ChildApp.java
```

## What it verifies

- `maven-invoker-plugin` forks a new Maven JVM process for each project in `src/it/`
- The forked process has its own session, local repository, and loaded extension set
- Core extension loaded via `.mvn/extensions.xml` in the child process is independent of the
  parent session; changes to parent state do not affect the child
- `maven-invoker-plugin` `<goals>` and `<properties>` control what the child process executes
- Changing the child project source triggers a re-execution of the invoker plugin's test

## How to run

```bash
mvn verify
mvn verify   # → cache hit on parent; child project is a separate build
```
