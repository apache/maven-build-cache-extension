# P10 — reactor-partial

**Unique behavior:** Nested reactor; `-pl -am`; `-pl`; `-rf` resume; chained external parent.

## Setup

Four-module linear dependency chain. Root POM has `corp-parent` as an external parent
(empty `<relativePath/>`). `corp-parent` must be installed before building the main project.

```
corp-parent              ← external artifact (install first)
  └── p10-reactor-partial/  ← reactor root
        ├── module-api/      ← jar (no deps)
        ├── module-core/     ← jar (depends on module-api)
        ├── module-service/  ← jar (depends on module-core)
        └── module-app/      ← jar (depends on module-service)
```

The build cache extension is loaded as a **core extension** via `.mvn/extensions.xml`.

## What it verifies

- `-am` correctly expands the build set; out-of-scope modules are not rebuilt
- A module cached in a partial build is reusable in subsequent partial or full builds
- `-rf` resumes without re-executing already-cached modules before the resume point
- Chained external parent (corp-parent, empty `relativePath`) contributes to all children's keys

## How to run

```bash
# Step 1: install the corp-parent (one-time setup)
cd _corp-parent && mvn install && cd ..

# Step 2: full build
mvn verify

# Step 3: partial build after changing module-core
mvn verify -pl :module-service -am
# → module-core + module-service rebuild; module-api from cache; module-app out of scope

# Step 4: resume after failure at module-service
mvn verify -rf :module-service
```
