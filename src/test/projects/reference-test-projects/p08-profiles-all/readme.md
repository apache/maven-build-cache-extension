# P08 — profiles-all

**Unique behavior:** ALL profile activation types combined; all property source types.

## Profiles

| Profile | Activation |
|---------|-----------|
| `by-property` | `-Denv=ci` (property activation) |
| `by-os` | OS family `unix` |
| `by-file` | `trigger.properties` exists (already present) |
| `by-jdk` | JDK version `[11,)` |
| `default-on` | `activeByDefault=true` (resets when any other activates) |
| `settings-profile` | Defined in `test-settings.xml`, always active via `<activeProfiles>` |

## Property sources

- POM `<properties>`: `pom.prop=pom-value`
- Profile properties: each profile contributes a distinct property
- Settings profile: `settings.prop=settings-value` (from `test-settings.xml`)
- CLI: `-Denv=ci` activates `by-property` profile

## What it verifies

- Each activation mechanism independently changes the effective POM → different cache key
- `activeByDefault` resets when `-P by-property` is also active
- Profile merge precedence: CLI overrides settings which overrides POM `activeByDefault`

## How to run

```bash
# Activate by-property profile (and by-file, by-os, by-jdk if conditions match)
mvn verify -Denv=ci -s test-settings.xml
mvn verify -Denv=ci -s test-settings.xml   # → cache hit

# Without env=ci (different profile set → different cache key)
mvn verify -s test-settings.xml
```
