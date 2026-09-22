# P09 — repos-mirrors

**Unique behavior:** POM `<repositories>`; `<pluginRepositories>`; `settings.xml` mirror; snapshot `updatePolicy`.

## Setup

Single-module JAR. POM declares custom `<repositories>` and `<pluginRepositories>`.
`test-settings.xml` defines a mirror redirecting `central` to Maven Central (transparent pass-through).
Snapshot repository has `<updatePolicy>always</updatePolicy>`.

The build cache extension is loaded as a **core extension** via `.mvn/extensions.xml`.

## What it verifies

- Mirror redirect is transparent to artifact content hashing
- `<pluginRepositories>` is separate from `<repositories>` — each contributes independently
- `<updatePolicy>always</updatePolicy>` forces re-resolution on every build
- Snapshot vs release resolution policy correctly selects the right repo per artifact type

## How to run

```bash
mvn verify -s test-settings.xml
mvn verify -s test-settings.xml   # → cache hit
```
