# P20: Single Module Test-jar Dependencies

P20 tests building a single module with `mvn -f <submodule>` when that module depends on a test-jar artifact from another module.

## Project Structure

```
p20-single-module-testjar/
  ├── module-producer/  (produces test-jar artifact)
  └── module-consumer/  (depends on test-jar from producer)
```

## Test Scenario

- `module-producer`: produces both regular jar and test-jar
- `module-consumer`: depends on both regular jar and test-jar from producer

## Key Test Case

The critical test is: **Can `mvn -f module-consumer clean install` succeed when the build cache extension is enabled?**

- Full reactor builds (`mvn clean install`) should always work
- Single module builds (`mvn -f module-consumer clean install`) failed in issue #467 due to test-jar resolution problems
- The build cache extension could not resolve test-jar dependencies even when they existed in local repository

## Resolution Testing

This project validates that the build cache extension can properly:
1. Resolve test-jar dependencies from local repository in single module builds
2. Calculate checksums for projects with test-jar dependencies
3. Handle the case where `session.getAllProjects()` only contains the single module being built