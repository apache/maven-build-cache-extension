# P01 — superpom-minimal

**Unique behavior:** Super POM default lifecycle bindings; no explicit parent; system-scope dependency.

## Setup

Single-module JAR project. No `<parent>` element. All dependency versions inline.
Plugin versions pinned inline; no `<pluginManagement>`. Zero profiles.

`lib/placeholder.jar` is a minimal valid JAR (empty ZIP) declared as a system-scoped dependency.

The build cache extension is loaded as a **build extension** via `<build><extensions>` in pom.xml
(not core extension — exercises the build-extension loading path).

## What it verifies

- Super POM default lifecycle phase bindings execute the standard phases in the correct order
  (compile, test-compile, test, package)
- Plugin versions declared inline produce a deterministic effective-plugin configuration
- System-scoped dependency resolves from the declared local `<systemPath>`
- `<packaging>jar</packaging>` produces a `.jar` artifact in `target/`

## How to run

```bash
mvn verify
mvn verify   # → cache hit
```
