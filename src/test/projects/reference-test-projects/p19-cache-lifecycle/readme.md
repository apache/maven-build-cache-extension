<!--
Licensed to the Apache Software Foundation (ASF) under one or more contributor license
agreements. See the NOTICE file for details. Licensed under the Apache License, Version 2.0.
-->

# P19 — Cache Lifecycle

**Unique dimension:** Execution-control behaviors — tracked properties with `skipValues`,
`defaultValues`, `runAlways`, `ignoreMissing`, and lifecycle escalation configuration.

## Purpose

This project serves as the common base for all integration tests that exercise the
extension's *execution-control* features: how the extension decides whether to reuse a
cached result or re-run a plugin execution.

## Design

The base `.mvn/maven-build-cache-config.xml` enables caching with the XX algorithm and
tracks `maven-surefire-plugin:test.skipTests` with `defaultValue="false"`.

Each test class that needs additional configuration (e.g. `<runAlways>`, `<ignoreMissing>`,
`<nolog>`, custom `<dirScan>`) must:
1. Obtain an isolated project copy via `ReferenceProjectBootstrap.prepareProject(path, qualifier)`.
2. Overwrite `.mvn/maven-build-cache-config.xml` in the copy with its specific settings.
3. Proceed with the usual two-build round-trip.

## Tests using this project

| Group | Test Class                             | Config change                            |
|-------|----------------------------------------|------------------------------------------|
| D-07  | `RunAlwaysPluginTest`                  | Adds `<runAlways><plugins>`              |
| D-08  | `RunAlwaysByGoalTest`                  | Adds `<runAlways><goalsLists>`           |
| D-09  | `RunAlwaysByExecutionIdTest`           | Adds `<runAlways><executions>`           |
| D-11  | `IgnoreMissingPluginTest`              | Adds `<ignoreMissing>`                   |
| D-12  | `TrackedPropertyDefaultValueTest`      | Uses base config (defaultValue already)  |
| D-13  | `MissingExecutionTriggerRebuildTest`   | Uses base config                         |
| B-05  | `PerPluginDirScanTest`                 | Adds `<dirScan>` for compiler plugin     |
| B-06  | `EffectivePomExcludePropertyTest`      | Adds `<excludeProperties>` for surefire  |
| M-02  | `TrackedPropertyPathNormalizationTest` | Adds a `File` tracked property           |
| O-03  | `LogAllPropertiesGlobalTest`           | Sets `<logAllProperties>true`            |
| TC-92 | `TrackedPropertyNologTest`             | Adds `nolog="true"` to a property        |
