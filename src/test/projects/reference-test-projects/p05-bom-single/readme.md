# P05 — bom-single

**Unique behavior:** Single BOM import (`scope=import, type=pom`); no parent; BOM-only version management.

## Setup

Single-module JAR, no `<parent>`. `<dependencyManagement>` has one `scope=import` BOM
(`org.junit:junit-bom:5.10.0`). No explicit `<version>` on JUnit Jupiter dependencies.

The build cache extension is loaded as a **core extension** via `.mvn/extensions.xml`.

## What it verifies

- BOM version upgrade → cache miss; downgrade → cache hit
- Adding a second BOM import → cache miss (paves way for P06 multi-BOM test)
- BOM-managed version is resolved before hashing

## How to run

```bash
mvn verify
mvn verify   # → cache hit
```
