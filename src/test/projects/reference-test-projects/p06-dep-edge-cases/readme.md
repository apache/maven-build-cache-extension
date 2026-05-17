# P06 — dep-edge-cases

**Unique behavior:** Multi-BOM mediation; system scope; optional dependency; classifier; layered exclusions; convergence + enforcer.

## Setup

Two-module flat reactor:
- `module-support`: provides a test-jar (via `maven-jar-plugin:test-jar`)
- `module-main`: exercises all dependency edge cases

```
root/
  ├── module-support/   (jar + test-jar classifier)
  └── module-main/      (multi-BOM, system scope, optional, classifier, exclusions, enforcer)
```

`module-main/lib/placeholder.jar` is a minimal valid JAR (empty ZIP) used for system-scope dependency testing.

The build cache extension is loaded as a **core extension** via `.mvn/extensions.xml`.

## What it verifies

- First BOM listed in `<dependencyManagement>` wins when the same GAV appears in multiple BOMs
- Swapping BOM import order changes the winning version (mediation order is declaration-order)
- Adding/removing `<exclusion>` changes the resolved transitive dependency set
- `maven-enforcer-plugin` with `requireUpperBoundDeps` detects version convergence violations
- System-scoped dependency resolves from the declared local `<systemPath>`
- Optional dependency does not propagate to modules that depend on the declaring module
- Classifier-qualified dependency coordinate requires both `artifactId` and `classifier` to match

## How to run

```bash
mvn verify
mvn verify   # → cache hit
```
