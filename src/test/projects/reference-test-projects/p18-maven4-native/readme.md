# P18 — maven4-native

**Unique behavior:** Maven 4 native features; `<packaging>bom</packaging>`; `<subprojects>`;
build cache extension compatibility with Maven 4.

## Setup

Two-module reactor: `platform-bom` and `app`.
`platform-bom` acts as a bill-of-materials; `app` imports it.

```
p18-maven4-native/
  ├── platform-bom/    (pom/bom packaging — dependency versions)
  └── app/             (jar — imports platform-bom)
```

The root POM uses `<modules>` for Maven 3/4 compatibility. In Maven 4 you can replace
`<modules>` with `<subprojects>` and set `<packaging>bom</packaging>` on `platform-bom`.

`.mvn/maven.config` passes `--strict-checksums` for all builds.

The build cache extension is loaded as a **core extension** via `.mvn/extensions.xml`.

## What it verifies

- Build cache extension operates correctly under Maven 4's new lifecycle/resolver
- BOM module (`pom`/`bom` packaging) is properly fingerprinted — changing a managed version
  causes a cache miss in the modules that import that BOM
- `--strict-checksums` flag does not break cache serialization
- Maven 4 `<subprojects>` reactor discovery is compatible with the extension

## How to run

```bash
# Maven 3 (uses <modules> in root pom.xml)
mvn verify
mvn verify   # → cache hit for both modules

# Maven 4 (replace <modules> with <subprojects> and optionally use <packaging>bom</packaging>)
mvn4 verify
mvn4 verify  # → cache hit
```

> **Note:** Maven 4 must be installed separately. The project builds with Maven 3.9+ as well.
