# P17 — war-webapp

**Unique behavior:** WAR packaging; profile-driven filter activation; multi-module with lib+war.

## Setup

Two-module reactor: `webapp-lib` (JAR) and `webapp-war` (WAR packaging).
The WAR depends on the JAR library and uses Maven resource filtering via profiles.

```
p17-war-webapp/
  ├── webapp-lib/    (jar packaging — shared library)
  └── webapp-war/    (war packaging — depends on webapp-lib)
```

Three profiles control filtering:
- `dev` — activated by `-Denv=dev`; sets `app.env=development`
- `prod` — activated by `-Denv=prod`; sets `app.env=production`
- `default-config` — `activeByDefault=true`; provides a safe default filter

The build cache extension is loaded as a **core extension** via `.mvn/extensions.xml`.

## What it verifies

- WAR packaging produces a `.war` artifact that is correctly cached and restored
- Changing a filter property file (dev ↔ prod) causes a cache miss for the WAR module
- The `webapp-lib` JAR module is independently cached; changes to the WAR filter do not
  invalidate the lib cache entry
- Profile-controlled `<filters>` entries are part of the effective build input

## How to run

```bash
mvn package                      # first build (cache miss)
mvn package                      # second build (cache hit for both modules)
mvn package -Denv=prod           # different profile → cache miss for webapp-war
```
