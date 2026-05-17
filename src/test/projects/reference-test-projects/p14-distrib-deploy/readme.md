# P14 — distrib-deploy

**Unique behavior:** `<distributionManagement>`; snapshot vs release repo selection; deploy lifecycle.

## Setup

Single-module JAR. `<distributionManagement>` defines separate snapshot and release repositories
(both pointing to local `file://` paths for testing).

The build cache extension is loaded as a **core extension** via `.mvn/extensions.xml`.

## What it verifies

- `<distributionManagement>` configures the target repositories for the `deploy` phase
- `maven-deploy-plugin` selects the snapshot repository when the version ends in `-SNAPSHOT`
- `maven-deploy-plugin` selects the release repository for non-SNAPSHOT versions
- The `deploy` phase executes after all prior standard lifecycle phases complete
- `<snapshotPolicy>` and `<releasePolicy>` in each repository descriptor are respected

## How to run

```bash
mvn deploy
mvn deploy   # → cache hit
```
