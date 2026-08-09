# P02 — local-parent-inherit

**Unique behavior:** Local `relativePath` parent; inherited groupId/version/deps/plugins.

## Setup

Three-module flat reactor. Root POM defines `<dependencyManagement>`, `<pluginManagement>`,
and `<properties>`. Children inherit coordinates (no `<groupId>`/`<version>`), dependency
versions, and plugin config.

```
root/
  ├── module-api/    (no internal deps)
  ├── module-core/   (depends on module-api)
  └── module-app/    (depends on module-core)
```

The build cache extension is loaded as a **core extension** via `.mvn/extensions.xml`.

## What it verifies

- `module-api` source change → cache miss for api, core, app
- `module-app` change → miss only for app
- Parent POM property change → miss for all modules
- Children with inherited coordinates produce correct cache keys

## How to run

```bash
mvn verify
mvn verify   # → should be cache hit for all modules
```
