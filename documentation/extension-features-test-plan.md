<!---
 Licensed to the Apache Software Foundation (ASF) under one or more
 contributor license agreements.  See the NOTICE file distributed with
 this work for additional information regarding copyright ownership.
 The ASF licenses this file to You under the Apache License, Version 2.0
 (the "License"); you may not use this file except in compliance with
 the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->

# Maven Build Cache Extension — Feature & Test Plan v3

**Version:** 3.0
**Date:** 2026-02-28
**Source baseline:** `CacheConfigImpl.java`, `build-cache-config.mdo`, `MavenProjectInput.java`
**Supersedes:** `extension-features-test-plan-v2.md` (v2)

---

## Table of Contents

1. [Introduction & Document Strategy](#1-introduction--document-strategy)
2. [Feature Catalog](#2-feature-catalog) (F1–F13, 79 features)
3. [Test Matrix](#3-test-matrix) (Group BASE + Groups A–P)
4. [Required Test Coverage](#4-required-test-coverage) (master traceability table)
5. [Minimal Implementation Backlog](#5-minimal-implementation-backlog)
6. [Coverage Gaps & Recommendations](#6-coverage-gaps--recommendations)
7. [Appendix: Property Reference](#7-appendix-property-reference)

---

## 1. Introduction & Document Strategy

### 1.1 Why the feature-centric approach (defense of v1)

The prompt objectives are:

> *"Enumerate all different ways to setup and configure the extension… categorize by criticality…
> add test matrix to achieve full coverage without redundancy… add status and reference to existing tests."*

`extension-features-test-plan.md` (v1) meets these objectives because it is
**feature-first, not test-first**:

- **Feature catalog (F1–F13)** directly answers *"enumerate all configuration flags and
  induced behaviors"* — every config option is named, mapped to its XML path and system
  property, and classified P0–P3.
- **Orthogonal test matrix** directly answers *"minimal set of orthogonal POM samples covering
  every unique Maven behavior without redundant overlap"* — each distinct behavior appears in
  exactly one primary scenario.
- **F11 Configuration Injection** explicitly covers *"all different ways to inject
  configuration (pom, env, cli)"* in one place.
- **Property Reference Appendix** is the single source of truth for all 21 system properties.

### 1.2 What the alternative document adds (taken into v2)

`test-coverage-target-state.md` adds genuine value in:

| Area                           | What it reveals                                    | Incorporated in v2        |
|--------------------------------|----------------------------------------------------|---------------------------|
| Dependency change correctness  | Dep version change must → cache miss               | TC-012, Group A           |
| Reactor cascade                | Upstream module change → downstream miss           | TC-013, Group I           |
| Portability                    | Absolute path normalization for remote reuse       | TC-074, Group M           |
| Project type variations        | WAR, POM packaging, shade plugin edge cases        | TC-057–TC-079, Group N    |
| Report generation              | `build-cache-report.xml`, `buildinfo.xml` debugs   | TC-083–TC-087, Group O    |
| Internal architecture          | Staging, OutputType, serialization                 | TC-088–TC-093, Group P    |
| `IncrementalRestoreTest` scope | Already covers `attachedOutputs` + classifiers     | Coverage correction F6.2  |
| `SkipBuildExtensionTest` scope | Already covers `-Dmaven.build.cache.enabled=false` | Coverage correction F1.3  |
| `PerModuleFlagsTest` scope     | Already covers per-module POM-property config      | Coverage correction F11.4 |

### 1.3 Coverage corrections (vs v1)

Reading the actual test sources revealed these features are **already covered** (v1 marked ❌):

| Feature                                          | Corrected Status | Evidence                                                                      |
|--------------------------------------------------|------------------|-------------------------------------------------------------------------------|
| F6.2 `attachedOutputs`                           | ✅                | `IncrementalRestoreTest` tests `extra-resources/` and `other-resources/` dirs |
| Classified artifacts (sources, javadoc)          | ✅                | `IncrementalRestoreTest` verifies sources.jar + javadoc.jar in cache          |
| F1.3 `-Dmaven.build.cache.enabled=false` via CLI | ✅                | `SkipBuildExtensionTest.cacheDisabledViaCommandLine`                          |
| Clean-only run → cache disabled                  | ✅                | `SkipBuildExtensionTest.simple`                                               |
| F11.4 Per-module POM property override           | ✅                | `PerModuleFlagsTest` (three modules: normal, skipCache, disabled)             |

### 1.4 Project-agnostic paradigm (v3)

The cache extension does not analyze project-specific business logic — it intercepts
Maven's build lifecycle and operates on file hashes, effective-POM representations, and
plugin execution metadata. This makes it fundamentally **project-agnostic**: every core
behavior must work correctly regardless of whether the project uses a remote parent,
CI-friendly versions, WAR packaging, or profiles.

#### `<reference project set>`

`documentation/maven-test-projects.md` §2 defines 19 reference configurations stored under
`src/test/projects/reference-test-projects/`. Together they cover all orthogonal Maven
behavior dimensions:

| ID  | Name                 | Unique dimension(s)                                          |
|-----|----------------------|--------------------------------------------------------------|
| P01 | superpom-minimal     | Super POM defaults; simplest single-module baseline          |
| P02 | local-parent-inherit | Local reactor parent; full dependency/plugin inheritance     |
| P03 | remote-parent        | `<relativePath/>` empty → parent resolved from repository    |
| P04 | ci-friendly          | `${revision}` + flatten-maven-plugin; CI-friendly versions   |
| P05 | bom-single           | Single BOM import; no explicit parent                        |
| P06 | dep-edge-cases       | Multi-BOM mediation; system/optional/classifier; enforcer    |
| P07 | plugin-rebinding     | `maven-plugin` packaging; lifecycle rebinding; plugin deps   |
| P08 | profiles-all         | All profile activation types; all property sources           |
| P09 | repos-mirrors        | POM `<repositories>`; settings mirror; snapshot updatePolicy |
| P10 | reactor-partial      | Nested reactor; `-pl -am`; chained external parent           |
| P11 | reactor-parallel     | `-T N` concurrent execution; cache thread-safety             |
| P12 | core-ext-forked      | Core extension + `maven-invoker-plugin` forked child         |
| P13 | toolchains-jdk       | `toolchains.xml`; external JDK selection                     |
| P14 | distrib-deploy       | `<distributionManagement>`; snapshot/release deploy          |
| P15 | relocation           | Consuming a dependency that carries `<relocation>` metadata  |
| P16 | snapshot-reactor     | Reactor SNAPSHOT deps; SNAPSHOT external; snapshot policy    |
| P17 | war-webapp           | WAR lifecycle; `src/main/webapp`; profile-filtered resources |
| P18 | maven4-native        | `<subprojects>`; `<packaging>bom</packaging>`; Maven 4       |
| P19 | cache-lifecycle      | `skipValues` reconciliation; lifecycle escalation config     |

#### v3 testing paradigm

**Base scenario (parametrized):**
Every fundamental cache behavior is codified in `CacheBaseBehaviorParametrizedTest` —
a new JUnit 5 `@ParameterizedTest` class that runs each assertion against all 17 eligible
reference projects (P01–P12, P14–P17, P19). P13 is conditional on toolchain JDK availability;
P18 runs on Maven 4 only.

**Feature-specific scenarios:**
Behaviors that depend on a specific project dimension (WAR packaging, nested reactor,
CI-friendly versions, etc.) target the most appropriate reference project from the set.

**New projects in v3:**
Only two behaviors remain outside the `<reference project set>` and require new projects:

1. `shade-plugin-project` — shade plugin replaces the JAR artifact (TC-079)
2. `assembly-plugin-project` — assembly plugin produces a ZIP with output exclusion (TC-080)

All other test scenarios that required "NEW: `project-name`" in v2 are now mapped to
an existing reference project.

---

## 2. Feature Catalog

### Criticality scale

| Label  | Meaning                                                   |
|--------|-----------------------------------------------------------|
| **P0** | Core correctness — wrong artifacts or silent misses       |
| **P1** | Visible user-facing behavior — broken in normal workflows |
| **P2** | Advanced / edge-case correctness                          |
| **P3** | Diagnostic / observability — invisible in typical use     |

### Test status legend

| Symbol | Meaning                                                            |
|--------|--------------------------------------------------------------------|
| ✅      | Integration test exists and covers this behavior                   |
| ⚠️     | Partially covered (related test but does not directly assert this) |
| ❌      | No integration test — gap                                          |

---

### F1 Core Enablement

| ID   | Feature                                       | XML / System Property                       | Criticality | Status | Test Reference                                                             |
|------|-----------------------------------------------|---------------------------------------------|-------------|--------|----------------------------------------------------------------------------|
| F1.1 | Build extension via POM `<build><extensions>` | `pom.xml <extensions>`                      | P0          | ✅      | `BuildExtensionTest`                                                       |
| F1.2 | Core extension via `.mvn/extensions.xml`      | `.mvn/extensions.xml`                       | P0          | ✅      | `CoreExtensionTest`                                                        |
| F1.3 | Global enable/disable (XML or CLI)            | `<enabled>` / `-Dmaven.build.cache.enabled` | P0          | ✅      | `BuildExtensionTest`, `SkipBuildExtensionTest.cacheDisabledViaCommandLine` |
| F1.4 | Config path override                          | `-Dmaven.build.cache.configPath`            | P1          | ✅      | `config/CustomConfigPathTest`                                              |
| F1.5 | Defaults-only mode (no XML file present)      | implicit                                    | P1          | ✅      | `config/NoConfigFileDefaultsTest`                                          |
| F1.6 | Maven version guard (requires ≥ 3.9.0)        | `CacheConfigImpl.initialize()`              | P1          | ❌      | —                                                                          |
| F1.7 | XML config validation                         | `<validateXml>`                             | P2          | ❌      | —                                                                          |
| F1.8 | Malformed XML → descriptive startup error     | implicit                                    | P1          | ✅      | `config/InvalidConfigXmlTest`                                              |

---

### F2 Input Fingerprinting

| ID    | Feature                                               | XML / System Property                                       | Criticality | Status | Test Reference                                                    |
|-------|-------------------------------------------------------|-------------------------------------------------------------|-------------|--------|-------------------------------------------------------------------|
| F2.1  | Global glob pattern                                   | `<input><global><glob>` / `maven.build.cache.input.glob`    | P0          | ✅      | `IncludeExcludeTest`                                              |
| F2.2  | Global include paths                                  | `<input><global><includes>` / `maven.build.cache.input`     | P0          | ✅      | `IncludeExcludeTest`                                              |
| F2.3  | Global exclude paths                                  | `<input><global><excludes>` / `maven.build.cache.exclude.*` | P0          | ✅      | `IncludeExcludeTest`                                              |
| F2.4  | Source file added → cache miss                        | implicit                                                    | P0          | ✅      | `checksumcorrectness/AddedSourceFileInvalidatesCacheTest`         |
| F2.5  | Source file deleted → cache miss                      | implicit                                                    | P0          | ✅      | `checksumcorrectness/DeletedSourceFileInvalidatesCacheTest`       |
| F2.6  | Source file modified → cache miss                     | implicit                                                    | P0          | ✅      | `checksumcorrectness/SourceChangeInvalidatesCacheTest`            |
| F2.7  | POM functional change → cache miss                    | implicit                                                    | P0          | ✅      | `checksumcorrectness/PomChangeInvalidatesCacheTest`               |
| F2.8  | POM whitespace-only change → cache hit                | implicit                                                    | P0          | ✅      | `checksumcorrectness/WhitespaceOnlyPomChangeNoCacheMissTest`      |
| F2.9  | Property change → cache miss                          | `<properties>`                                              | P0          | ✅      | `checksumcorrectness/PropertyChangeInvalidatesCacheTest`          |
| F2.10 | Resource file change → cache miss                     | `src/main/resources`                                        | P0          | ✅      | `checksumcorrectness/ResourceChangeInvalidatesCacheTest`          |
| F2.11 | Test source change → cache miss                       | `src/test/java`                                             | P0          | ✅      | `checksumcorrectness/TestSourceChangeInvalidatesCacheTest`        |
| F2.12 | Dependency version change → cache miss                | effective POM                                               | P0          | ✅      | `checksumcorrectness/DependencyVersionChangeInvalidatesCacheTest` |
| F2.13 | Per-plugin `dirScan` configuration                    | `<input><plugins><plugin><dirScan>`                         | P1          | ❌      | —                                                                 |
| F2.14 | Per-execution `dirScan` override                      | `<input><plugins><plugin><executions>`                      | P2          | ❌      | —                                                                 |
| F2.15 | Effective POM property exclusion per plugin           | `<plugin><effectivePom><excludeProperties>`                 | P1          | ✅      | `inputfiltering/EffectivePomExcludePropertyTest`                  |
| F2.16 | Plugin dependency exclusion from fingerprint          | `<plugin><excludeDependencies>`                             | P2          | ❌      | —                                                                 |
| F2.17 | `processPlugins` flag                                 | `maven.build.cache.processPlugins`                          | P1          | ✅      | `inputfiltering/ProcessPluginsDisabledTest`                       |
| F2.18 | Project-level glob override (POM property)            | `maven.build.cache.input.glob` in `<properties>`            | P1          | ✅      | `inputfiltering/PerProjectGlobOverrideTest`                       |
| F2.19 | Project-level additional include (POM property)       | `maven.build.cache.input.*` in `<properties>`               | P1          | ✅      | `inputfiltering/ProjectLevelIncludeTest`                          |
| F2.20 | Project-level exclude (POM property)                  | `maven.build.cache.exclude.*` in `<properties>`             | P1          | ❌      | —                                                                 |
| F2.21 | Profile activation changes effective POM → cache miss | `-P profile`                                                | P1          | ✅      | `inputfiltering/ProfileCliActivationInvalidatesTest`              |
| F2.22 | Hidden files excluded automatically                   | implicit                                                    | P2          | ❌      | —                                                                 |

---

### F3 Hash Algorithms

| ID   | Feature                                       | XML / System Property     | Criticality | Status | Test Reference                           |
|------|-----------------------------------------------|---------------------------|-------------|--------|------------------------------------------|
| F3.1 | Default algorithm: XX (XXHash64)              | `<hashAlgorithm>XX`       | P0          | ✅      | (all existing ITs)                       |
| F3.2 | SHA-256                                       | `<hashAlgorithm>SHA-256`  | P2          | ✅      | `hashalgorithm/HashAlgorithmSHA256Test`  |
| F3.3 | SHA-1                                         | `<hashAlgorithm>SHA-1`    | P2          | ❌      | —                                        |
| F3.4 | METRO                                         | `<hashAlgorithm>METRO`    | P2          | ❌      | —                                        |
| F3.5 | XXMM (memory-mapped, needs `--add-opens`)     | `<hashAlgorithm>XXMM`     | P2          | ❌      | —                                        |
| F3.6 | METRO+MM (memory-mapped, needs `--add-opens`) | `<hashAlgorithm>METRO+MM` | P2          | ❌      | —                                        |
| F3.7 | Invalid algorithm → startup failure           | `<hashAlgorithm>BOGUS`    | P1          | ✅      | `hashalgorithm/InvalidHashAlgorithmTest` |
| F3.8 | Algorithm change between builds → cache miss  | config change             | P2          | ❌      | —                                        |

---

### F4 Execution Control

| ID    | Feature                                    | XML / System Property                       | Criticality | Status | Test Reference                                             |
|-------|--------------------------------------------|---------------------------------------------|-------------|--------|------------------------------------------------------------|
| F4.1  | `runAlways` by plugin coordinates          | `<executionControl><runAlways><plugins>`    | P1          | ✅      | `pluginexecution/RunAlwaysPluginTest`                      |
| F4.2  | `runAlways` by goal name                   | `<executionControl><runAlways><goalsLists>` | P1          | ✅      | `pluginexecution/RunAlwaysByGoalTest`                      |
| F4.3  | `runAlways` by execution ID                | `<executionControl><runAlways><executions>` | P1          | ✅      | `pluginexecution/RunAlwaysByExecutionIdTest`               |
| F4.4  | `alwaysRunPlugins` CLI property            | `-Dmaven.build.cache.alwaysRunPlugins`      | P1          | ✅      | `pluginexecution/AlwaysRunPluginsCliTest`                  |
| F4.5  | `ignoreMissing` — skip absent executions   | `<executionControl><ignoreMissing>`         | P1          | ✅      | `pluginexecution/IgnoreMissingPluginTest`                  |
| F4.6  | Reconcile: `skipValue` allows cache hit    | `<property skipValue="…">`                  | P0          | ✅      | `pluginexecution/TrackedPropertySkipValueAllowsReuseTest`  |
| F4.7  | Reconcile: mismatch → cache miss           | `<reconcile>…<property>`                    | P0          | ✅      | `pluginexecution/TrackedPropertyMismatchCacheMissTest`     |
| F4.8  | Reconcile: match → cache hit               | `<reconcile>…<property>`                    | P0          | ✅      | `pluginexecution/TrackedPropertyMatchCacheHitTest`         |
| F4.9  | Reconcile: `defaultValue` used when absent | `<property defaultValue="…">`               | P1          | ✅      | `pluginexecution/TrackedPropertyDefaultValueTest`          |
| F4.10 | Reconcile: `logAll` flag on goal           | `<reconcile><plugin logAll="true">`         | P3          | ✅      | `pluginexecution/LogAllPropertiesTest`                     |
| F4.11 | Reconcile: `nolog` suppresses property     | `<property nolog="true">`                   | P3          | ✅      | `pluginexecution/TrackedPropertyNologTest`                 |
| F4.12 | `logAllProperties` global flag             | `<reconcile><logAllProperties>`             | P3          | ✅      | `pluginexecution/LogAllPropertiesGlobalTest`               |
| F4.13 | Forked execution tracked correctly         | implicit                                    | P1          | ✅      | `ForkedExecutionsTest`, `ForkedExecutionCoreExtensionTest` |
| F4.14 | Duplicate goal executions within one build | implicit                                    | P1          | ✅      | `DuplicateGoalsTest`                                       |
| F4.15 | Missing cached execution → full rebuild    | `Build.getMissingExecutions()`              | P1          | ✅      | `pluginexecution/MissingExecutionTriggerRebuildTest`       |

---

### F5 Artifact Restore

| ID   | Feature                                         | XML / System Property                         | Criticality | Status | Test Reference                               |
|------|-------------------------------------------------|-----------------------------------------------|-------------|--------|----------------------------------------------|
| F5.1 | Standard artifact restore (JAR → local repo)    | implicit                                      | P0          | ✅      | `BuildExtensionTest.simple`                  |
| F5.2 | Classified artifact restore (sources, javadoc)  | implicit                                      | P1          | ✅      | `IncrementalRestoreTest`                     |
| F5.3 | `lazyRestore` — defer remote download           | `-Dmaven.build.cache.lazyRestore`             | P1          | ✅      | `IncrementalRestoreTest`                     |
| F5.4 | `restoreGeneratedSources=false`                 | `-Dmaven.build.cache.restoreGeneratedSources` | P1          | ✅      | `artifacts/RestoreGeneratedSourcesFalseTest` |
| F5.5 | `restoreOnDiskArtifacts=false`                  | `-Dmaven.build.cache.restoreOnDiskArtifacts`  | P1          | ✅      | `artifacts/RestoreOnDiskArtifactsFalseTest`  |
| F5.6 | Corrupted/truncated ZIP handled gracefully      | implicit                                      | P1          | ✅      | `failurerecovery/CorruptedZipCacheEntryTest` |
| F5.7 | NORMALIZED_VERSION → SNAPSHOT bump is cache hit | `calculateProjectVersionChecksum=false`       | P2          | ✅      | `versioning/SnapshotVersionBumpCacheHitTest` |

---

### F6 Output Management

| ID   | Feature                                          | XML / System Property                       | Criticality | Status | Test Reference                                                |
|------|--------------------------------------------------|---------------------------------------------|-------------|--------|---------------------------------------------------------------|
| F6.1 | Primary artifact stored/restored                 | implicit                                    | P0          | ✅      | `BuildExtensionTest.simple`                                   |
| F6.2 | `attachedOutputs` — additional directories       | `<attachedOutputs><dirName glob="…">`       | P1          | ✅      | `IncrementalRestoreTest` (extra-resources/, other-resources/) |
| F6.3 | `preservePermissions` — POSIX permissions        | `<attachedOutputs preservePermissions="…">` | P2          | ✅      | `output/PermissionsPreservationTest`                          |
| F6.4 | Output exclude patterns (regex)                  | `<output><exclude><patterns>`               | P1          | ✅      | `output/OutputExcludePatternTest`                             |
| F6.5 | `maxBuildsCached` LRU eviction                   | `<local><maxBuildsCached>`                  | P2          | ✅      | `output/MaxLocalBuildsCachedTest`                             |
| F6.6 | Custom local cache location                      | `-Dmaven.build.cache.location`              | P2          | ✅      | `BuildExtensionTest.skipSaving`                               |
| F6.7 | Stale artifacts in `target/` not leaked to cache | `stagePreExistingArtifacts` internal        | P2          | ✅      | `internal/StagingRemovesStaleClassesTest`                     |

---

### F7 Remote Cache

| ID   | Feature                                      | XML / System Property                                                 | Criticality | Status | Test Reference                                     |
|------|----------------------------------------------|-----------------------------------------------------------------------|-------------|--------|----------------------------------------------------|
| F7.1 | Remote cache read                            | `<remote><url>` / `-Dmaven.build.cache.remote.url`                    | P1          | ✅      | `RemoteCacheDavTest`                               |
| F7.2 | Remote cache enable/disable                  | `<remote enabled="…">` / `-Dmaven.build.cache.remote.enabled`         | P1          | ✅      | `RemoteCacheDavTest`                               |
| F7.3 | Remote save (push)                           | `<remote><save><enabled>` / `-Dmaven.build.cache.remote.save.enabled` | P1          | ✅      | `RemoteCacheDavTest`                               |
| F7.4 | `save.final` — prohibit overwrite            | `-Dmaven.build.cache.remote.save.final`                               | P2          | ✅      | `remote/SaveFinalRemoteTest` (@Disabled)           |
| F7.5 | Server authentication via settings.xml       | `<remote id="…">` / `-Dmaven.build.cache.remote.server.id`            | P1          | ✅      | `RemoteCacheDavTest`                               |
| F7.6 | Remote unavailable → graceful local fallback | implicit                                                              | P2          | ✅      | `remote/RemoteUnavailableFallbackTest` (@Disabled) |
| F7.7 | `failFast` on remote restore failure         | `-Dmaven.build.cache.failFast`                                        | P2          | ✅      | `admin/FailFastTest`                               |
| F7.8 | `baselineUrl` diff report                    | `-Dmaven.build.cache.baselineUrl`                                     | P3          | ✅      | `remote/BaselineDiffTest` (@Disabled)              |

---

### F8 Multi-Module / Reactor

| ID   | Feature                                             | XML / System Property                           | Criticality | Status | Test Reference                                       |
|------|-----------------------------------------------------|-------------------------------------------------|-------------|--------|------------------------------------------------------|
| F8.1 | Full reactor — each module independently cached     | implicit                                        | P0          | ✅      | `Issue21Test`                                        |
| F8.2 | Reactor cascade — upstream change → downstream miss | implicit                                        | P1          | ✅      | `multimodule/UpstreamModuleChangeDownstreamMissTest` |
| F8.3 | Partial reactor `-pl`                               | implicit `-pl`                                  | P1          | ✅      | `multimodule/MultiModulePartialBuildTest`            |
| F8.4 | Partial reactor `-pl -am` upstream restore          | implicit `-pl -am`                              | P1          | ✅      | `multimodule/MultiModulePartialWithAmTest`           |
| F8.5 | Parallel builds `-T` — no corruption                | implicit `-T`                                   | P1          | ✅      | `multimodule/ParallelBuildTest`                      |
| F8.6 | `scanProfiles` — profiles in multi-module key       | `<multiModule><scanProfiles>`                   | P2          | ✅      | `multimodule/ScanProfilesTest`                       |
| F8.7 | Per-module `skipCache` via POM property             | `maven.build.cache.skipCache` in `<properties>` | P1          | ✅      | `PerModuleFlagsTest`                                 |
| F8.8 | Per-module cache disabled via POM property          | `maven.build.cache.enabled` in `<properties>`   | P1          | ✅      | `PerModuleFlagsTest`                                 |

---

### F9 Lifecycle Phases

| ID   | Feature                                                 | XML / System Property                                     | Criticality | Status | Test Reference                                                         |
|------|---------------------------------------------------------|-----------------------------------------------------------|-------------|--------|------------------------------------------------------------------------|
| F9.1 | `compile` phase cached and restored                     | implicit                                                  | P0          | ✅      | `lifecyclephases/CompilePhaseDefaultCachedTest`                        |
| F9.2 | `test-compile` phase cached                             | implicit                                                  | P0          | ✅      | `lifecyclephases/TestCompilePhaseTest`                                 |
| F9.3 | `package` phase cached                                  | implicit                                                  | P0          | ✅      | `lifecyclephases/CompileThenPackageEscalationTest`                     |
| F9.4 | `install` phase cached                                  | implicit                                                  | P0          | ✅      | `lifecyclephases/InstallPhaseTest`                                     |
| F9.5 | Phase escalation → prior phase restored, new phase runs | implicit                                                  | P0          | ✅      | `CompileThenPackageEscalationTest`, `PackageThenInstallEscalationTest` |
| F9.6 | `clean`-only run → cache bypassed entirely              | implicit                                                  | P1          | ✅      | `SkipBuildExtensionTest.simple`                                        |
| F9.7 | `clean verify` → clean runs first, then cache           | implicit                                                  | P1          | ✅      | `lifecyclephases/CleanVerifyTest`                                      |
| F9.8 | `mandatoryClean` blocks save without prior clean        | `<mandatoryClean>` / `-Dmaven.build.cache.mandatoryClean` | P1          | ✅      | `MandatoryCleanTest`                                                   |
| F9.9 | `verify` phase (with integration tests) cached          | implicit                                                  | P1          | ✅      | `BuildExtensionTest.simple`                                            |

---

### F10 Admin Controls

| ID    | Feature                                      | XML / System Property                | Criticality | Status | Test Reference                                |
|-------|----------------------------------------------|--------------------------------------|-------------|--------|-----------------------------------------------|
| F10.1 | `skipCache` — force rebuild, still writes    | `-Dmaven.build.cache.skipCache`      | P1          | ✅      | `PerModuleFlagsTest`                          |
| F10.2 | `skipSave` — read-only cache usage           | `-Dmaven.build.cache.skipSave`       | P1          | ✅      | `BuildExtensionTest.skipSaving`               |
| F10.3 | `failFast` — abort on restore failure        | `-Dmaven.build.cache.failFast`       | P2          | ✅      | `admin/FailFastTest`                          |
| F10.4 | `mandatoryClean` — require clean before save | `-Dmaven.build.cache.mandatoryClean` | P1          | ✅      | `MandatoryCleanTest`                          |
| F10.5 | Mid-build failure → no partial cache entry   | implicit                             | P0          | ✅      | `failurerecovery/BuildFailsMidwayNoCacheTest` |
| F10.6 | `maxBuildsCached` LRU eviction               | `<local><maxBuildsCached>`           | P2          | ✅      | `output/MaxLocalBuildsCachedTest`             |

---

### F11 Configuration Injection

| ID    | Feature                                | Mechanism                              | Criticality | Status | Test Reference                                            |
|-------|----------------------------------------|----------------------------------------|-------------|--------|-----------------------------------------------------------|
| F11.1 | XML file at default path               | `.mvn/maven-build-cache-config.xml`    | P0          | ✅      | all standard ITs                                          |
| F11.2 | XML file at custom path                | `-Dmaven.build.cache.configPath`       | P1          | ✅      | `config/CustomConfigPathTest`                             |
| F11.3 | CLI `-D` system property overrides XML | `-D<property>`                         | P1          | ✅      | `BuildExtensionTest.skipSaving`, `SkipBuildExtensionTest` |
| F11.4 | POM `<properties>` per-module override | `<properties>`                         | P1          | ✅      | `PerModuleFlagsTest`                                      |
| F11.5 | No XML file → built-in defaults active | implicit                               | P1          | ✅      | `config/NoConfigFileDefaultsTest`                         |
| F11.6 | `alwaysRunPlugins` CLI comma-list      | `-Dmaven.build.cache.alwaysRunPlugins` | P1          | ✅      | `pluginexecution/AlwaysRunPluginsCliTest`                 |

---

### F12 Project Versioning

| ID    | Feature                                       | XML / System Property                                  | Criticality | Status | Test Reference                                       |
|-------|-----------------------------------------------|--------------------------------------------------------|-------------|--------|------------------------------------------------------|
| F12.1 | `adjustMetaInf` — normalize MANIFEST.MF       | `<projectVersioning><adjustMetaInf>`                   | P2          | ✅      | `versioning/MetaInfVersionAdjustmentTest`            |
| F12.2 | `calculateProjectVersionChecksum`             | `<projectVersioning><calculateProjectVersionChecksum>` | P2          | ✅      | `versioning/SnapshotVersionBumpWithChecksumFlagTest` |
| F12.3 | NORMALIZED_VERSION: SNAPSHOT bump → cache hit | implicit (default)                                     | P2          | ✅      | `versioning/SnapshotVersionBumpCacheHitTest`         |
| F12.4 | CI-friendly `${revision}` version stability   | flatten-maven-plugin pattern                           | P1          | ✅      | `versioning/CIFriendlyRevisionVersionTest`           |

---

### F13 Diagnostics

| ID    | Feature                                             | XML / System Property                 | Criticality | Status | Test Reference                               |
|-------|-----------------------------------------------------|---------------------------------------|-------------|--------|----------------------------------------------|
| F13.1 | Per-goal `logAll` flag                              | `<reconcile><plugin logAll="true">`   | P3          | ✅      | `pluginexecution/LogAllPropertiesTest`       |
| F13.2 | `logAllProperties` global flag                      | `<reconcile><logAllProperties>`       | P3          | ✅      | `pluginexecution/LogAllPropertiesGlobalTest` |
| F13.3 | `buildinfo.xml` FileHash debug                      | `<debugs><debug>FileHash</debug>`     | P3          | ✅      | `reports/BuildInfoXmlDebugTest`              |
| F13.4 | `buildinfo.xml` EffectivePom debug                  | `<debugs><debug>EffectivePom</debug>` | P3          | ❌      | —                                            |
| F13.5 | `build-cache-report.xml` generated per build        | implicit                              | P2          | ✅      | `reports/CacheReportGeneratedTest`           |
| F13.6 | `buildsdiff.xml` generated with baselineUrl         | `-Dmaven.build.cache.baselineUrl`     | P3          | ✅      | `remote/BaselineDiffTest` (@Disabled)        |
| F13.7 | CacheSource field (LOCAL/REMOTE/BUILD) in buildinfo | `Build.source`                        | P3          | ✅      | `internal/CacheSourceTrackingTest`           |

---

## 3. Test Matrix

Each row defines a distinct, independently executable integration test targeting one primary
behavior. The matrix is **orthogonal**: every behavior appears in one primary row only.

The "Reference Project" column shows:

- `PARAM P01-P19` — parametrized test running against all eligible reference projects
- `P<n>` — specific reference project from `src/test/projects/reference-test-projects/`
- `legacy: <name>` — existing non-reference test project (pre-v3)
- `new: <name>` — a new project not in the reference set (2 cases only)

---

### Group BASE: Parametrized Cross-Project Base Tests (NEW in v3)

**Test class:** `CacheBaseBehaviorParametrizedTest` ✅ (implemented)
**Reference projects:** P01–P12, P14–P17, P19 (17 projects)
**Conditional:** P13 — skipped when toolchain JDK installations are not configured
**Maven 4 only:** P18 — included when running against Maven 4

Each BASE scenario runs as a `@ParameterizedTest(@MethodSource("allReferenceProjects"))`.
For multi-module projects the test targets the first leaf module.
This class provides **119 test runs** (17 projects × 7 scenarios) validating that core
cache behaviors are project-agnostic.

| ID      | Scenario                                             | Features         | Status |
|---------|------------------------------------------------------|------------------|--------|
| BASE-01 | First build completes; identical second build is hit | F1.1, F1.2, F5.1 | ✅      |
| BASE-02 | Source file modification → cache miss                | F2.6             | ✅      |
| BASE-03 | POM property value change → cache miss               | F2.9             | ✅      |
| BASE-04 | `enabled=false` CLI → build runs, cache bypassed     | F1.3             | ✅      |
| BASE-05 | `skipCache=true` → rebuild occurs; cache written     | F10.1            | ✅      |
| BASE-06 | `skipSave=true` → cache hit; no new write            | F10.2            | ✅      |
| BASE-07 | Compile→Package phase escalation → rebuild           | F9.5             | ✅      |

---

### Group A: Core Checksum Correctness (P0)

| ID   | Scenario                         | Features         | Reference Project              | Status                                                              |
|------|----------------------------------|------------------|--------------------------------|---------------------------------------------------------------------|
| A-01 | Build extension: hit/miss cycle  | F1.1, F5.1, F6.1 | legacy: `build-extension`      | ✅ `BuildExtensionTest.simple`                                       |
| A-02 | Core extension: hit/miss cycle   | F1.2, F5.1       | PARAM P01-P19                  | ✅ `CoreExtensionTest` + BASE-01                                     |
| A-03 | Source file added → miss         | F2.4             | legacy: `checksum-correctness` | ✅ `AddedSourceFileInvalidatesCacheTest`                             |
| A-04 | Source file deleted → miss       | F2.5             | legacy: `checksum-correctness` | ✅ `DeletedSourceFileInvalidatesCacheTest`                           |
| A-05 | Source file modified → miss      | F2.6             | PARAM P01-P19                  | ✅ `SourceChangeInvalidatesCacheTest` + BASE-02                      |
| A-06 | POM functional change → miss     | F2.7             | legacy: `checksum-correctness` | ✅ `PomChangeInvalidatesCacheTest`                                   |
| A-07 | POM whitespace-only → hit        | F2.8             | legacy: `checksum-correctness` | ✅ `WhitespaceOnlyPomChangeNoCacheMissTest`                          |
| A-08 | Resource file change → miss      | F2.10            | legacy: `checksum-correctness` | ✅ `ResourceChangeInvalidatesCacheTest`                              |
| A-09 | Test source change → miss        | F2.11            | legacy: `checksum-correctness` | ✅ `TestSourceChangeInvalidatesCacheTest`                            |
| A-10 | Property change → miss           | F2.9             | PARAM P01-P19                  | ✅ `PropertyChangeInvalidatesCacheTest` + BASE-03                    |
| A-11 | Dependency version change → miss | F2.12            | **P01**                        | ✅ `checksumcorrectness/DependencyVersionChangeInvalidatesCacheTest` |

### Group B: Input Filtering (P0/P1)

| ID   | Scenario                                         | Features  | Reference Project         | Status                                                 |
|------|--------------------------------------------------|-----------|---------------------------|--------------------------------------------------------|
| B-01 | Global include/exclude/glob                      | F2.1–F2.3 | legacy: `include-exclude` | ✅ `IncludeExcludeTest`                                 |
| B-02 | Project-level glob override via POM property     | F2.18     | **P02**                   | ✅ `inputfiltering/PerProjectGlobOverrideTest`          |
| B-03 | Project-level include via POM property           | F2.19     | **P02**                   | ✅ `inputfiltering/ProjectLevelIncludeTest`             |
| B-04 | `processPlugins=false` skips param introspection | F2.17     | **P01**                   | ✅ `inputfiltering/ProcessPluginsDisabledTest`          |
| B-05 | Per-plugin dirScan limits scanned paths          | F2.13     | **P19**                   | ❌ `PerPluginDirScanTest`                               |
| B-06 | Effective POM property exclusion per plugin      | F2.15     | **P19**                   | ✅ `inputfiltering/EffectivePomExcludePropertyTest`     |
| B-07 | Profile activation changes effective POM → miss  | F2.21     | **P08**                   | ✅ `inputfiltering/ProfileCliActivationInvalidatesTest` |

### Group C: Lifecycle Phases & Escalation (P0)

| ID   | Scenario                                 | Features   | Reference Project          | Status                                         |
|------|------------------------------------------|------------|----------------------------|------------------------------------------------|
| C-01 | Compile phase cached                     | F9.1       | legacy: `lifecycle-phases` | ✅ `CompilePhaseDefaultCachedTest`              |
| C-02 | Test-compile phase cached                | F9.2       | legacy: `lifecycle-phases` | ✅ `TestCompilePhaseTest`                       |
| C-03 | Compile→Package escalation → rebuild     | F9.3, F9.5 | PARAM P01-P19              | ✅ `CompileThenPackageEscalationTest` + BASE-07 |
| C-04 | Package→Install escalation → rebuild     | F9.4, F9.5 | legacy: `lifecycle-phases` | ✅ `PackageThenInstallEscalationTest`           |
| C-05 | Install phase: artifact in local repo    | F9.4       | legacy: `lifecycle-phases` | ✅ `InstallPhaseTest`                           |
| C-06 | Clean-only run → cache bypassed          | F9.6       | PARAM P01-P19              | ✅ `SkipBuildExtensionTest.simple`              |
| C-07 | `clean verify` — clean then cached build | F9.7       | PARAM P01-P19              | ✅ `lifecyclephases/CleanVerifyTest`            |

### Group D: Plugin Execution & Reconciliation (P0/P1)

| ID   | Scenario                                     | Features     | Reference Project | Status                                                 |
|------|----------------------------------------------|--------------|-------------------|--------------------------------------------------------|
| D-01 | Tracked property match → hit                 | F4.8         | **P19**           | ✅ `TrackedPropertyMatchCacheHitTest`                   |
| D-02 | Tracked property mismatch → miss             | F4.7         | **P19**           | ✅ `TrackedPropertyMismatchCacheMissTest`               |
| D-03 | skipValue enables cache hit with skip flag   | F4.6         | **P19**           | ✅ `TrackedPropertySkipValueAllowsReuseTest`            |
| D-04 | logAll dumps all properties                  | F4.10, F13.1 | **P19**           | ✅ `LogAllPropertiesTest`                               |
| D-05 | Forked lifecycle execution tracked           | F4.13        | **P12**           | ✅ `ForkedExecutionsTest`                               |
| D-06 | Duplicate goal executions handled            | F4.14        | **P01**           | ✅ `DuplicateGoalsTest`                                 |
| D-07 | `runAlways` by plugin — plugin always runs   | F4.1         | **P19**           | ✅ `pluginexecution/RunAlwaysPluginTest`                |
| D-08 | `runAlways` by goal name                     | F4.2         | **P19**           | ✅ `pluginexecution/RunAlwaysByGoalTest`                |
| D-09 | `runAlways` by execution ID                  | F4.3         | **P19**           | ✅ `pluginexecution/RunAlwaysByExecutionIdTest`         |
| D-10 | `alwaysRunPlugins` CLI property              | F4.4, F11.6  | **P01**           | ✅ `pluginexecution/AlwaysRunPluginsCliTest`            |
| D-11 | `ignoreMissing` — absent executions skipped  | F4.5         | **P19**           | ✅ `pluginexecution/IgnoreMissingPluginTest`            |
| D-12 | Reconcile `defaultValue` for absent property | F4.9         | **P19**           | ✅ `pluginexecution/TrackedPropertyDefaultValueTest`    |
| D-13 | Missing cached execution → full rebuild      | F4.15        | **P19**           | ✅ `pluginexecution/MissingExecutionTriggerRebuildTest` |

### Group E: Artifact Restore (P1)

| ID   | Scenario                                          | Features         | Reference Project                 | Status                                         |
|------|---------------------------------------------------|------------------|-----------------------------------|------------------------------------------------|
| E-01 | Incremental restore + extra outputs + classifiers | F5.2, F5.3, F6.2 | legacy: `mbuildcache-incremental` | ✅ `IncrementalRestoreTest`                     |
| E-02 | `restoreGeneratedSources=false` skips sources     | F5.4             | **P01**                           | ✅ `artifacts/RestoreGeneratedSourcesFalseTest` |
| E-03 | `restoreOnDiskArtifacts=false` skips target/      | F5.5             | **P01**                           | ✅ `artifacts/RestoreOnDiskArtifactsFalseTest`  |
| E-04 | Corrupted ZIP entry → clean rebuild               | F5.6             | legacy: `failure-recovery`        | ✅ `CorruptedZipCacheEntryTest`                 |

### Group F: Output Management (P1/P2)

| ID   | Scenario                                        | Features    | Reference Project | Status                                 |
|------|-------------------------------------------------|-------------|-------------------|----------------------------------------|
| F-01 | Output exclude pattern filters from cache entry | F6.4        | **P17**           | ✅ `output/OutputExcludePatternTest`    |
| F-02 | `preservePermissions=true` restores POSIX bits  | F6.3        | **P01**           | ✅ `output/PermissionsPreservationTest` |
| F-03 | `maxBuildsCached` evicts oldest entries         | F6.5, F10.6 | **P01**           | ✅ `output/MaxLocalBuildsCachedTest`    |

### Group G: Admin Controls (P1)

| ID   | Scenario                                      | Features    | Reference Project | Status                                                           |
|------|-----------------------------------------------|-------------|-------------------|------------------------------------------------------------------|
| G-01 | `skipCache=true` forces rebuild, still writes | F10.1, F8.7 | PARAM P01-P19     | ✅ `PerModuleFlagsTest` + BASE-05                                 |
| G-02 | `skipSave=true` reads from cache, no write    | F10.2       | PARAM P01-P19     | ✅ `BuildExtensionTest.skipSaving` + BASE-06                      |
| G-03 | `mandatoryClean=true` blocks save             | F9.8, F10.4 | PARAM P01-P19     | ✅ `MandatoryCleanTest`                                           |
| G-04 | Mid-reactor failure → no partial entry        | F10.5       | **P06**           | ✅ `BuildFailsMidwayNoCacheTest`                                  |
| G-05 | `-Dmaven.build.cache.enabled=false` via CLI   | F1.3, F11.3 | PARAM P01-P19     | ✅ `SkipBuildExtensionTest.cacheDisabledViaCommandLine` + BASE-04 |
| G-06 | `failFast=true` aborts on restore failure     | F7.7, F10.3 | **P01**           | ✅ `admin/FailFastTest`                                           |

### Group H: Configuration Injection (P1)

| ID   | Scenario                                     | Features    | Reference Project         | Status                              |
|------|----------------------------------------------|-------------|---------------------------|-------------------------------------|
| H-01 | XML at default path configures all behaviors | F11.1       | PARAM P01-P19             | ✅ all standard ITs                  |
| H-02 | CLI `-D` overrides XML setting               | F11.3       | legacy: `build-extension` | ✅ `SkipBuildExtensionTest`          |
| H-03 | POM `<properties>` per-module override       | F11.4       | **P10**                   | ✅ `PerModuleFlagsTest`              |
| H-04 | configPath override to non-default location  | F11.2, F1.4 | **P01**                   | ✅ `config/CustomConfigPathTest`     |
| H-05 | No XML file → built-in defaults active       | F11.5, F1.5 | **P01** (no-config)       | ✅ `config/NoConfigFileDefaultsTest` |
| H-06 | Malformed XML → descriptive startup error    | F1.8        | **P01**                   | ✅ `config/InvalidConfigXmlTest`     |

> **H-03 note:** `PerModuleFlagsTest` currently uses the legacy `per-module-flags` project.
> The preferred v3 reference is P10 (nested reactor) which can host per-module property tests.
>
> **H-05 note:** Test sets up P01 without a `.mvn/maven-build-cache-config.xml` file.

### Group I: Multi-Module & Reactor (P1)

| ID   | Scenario                                          | Features   | Reference Project         | Status                                                 |
|------|---------------------------------------------------|------------|---------------------------|--------------------------------------------------------|
| I-01 | Full reactor: each module independently cached    | F8.1       | legacy: `build-extension` | ✅ `Issue21Test`                                        |
| I-02 | Per-module skipCache / disabled via POM           | F8.7, F8.8 | **P10**                   | ✅ `PerModuleFlagsTest`                                 |
| I-03 | Upstream module change → downstream miss          | F8.2       | **P02**                   | ✅ `multimodule/UpstreamModuleChangeDownstreamMissTest` |
| I-04 | Partial reactor `-pl module`                      | F8.3       | **P10**                   | ✅ `multimodule/MultiModulePartialBuildTest`            |
| I-05 | Partial reactor `-pl module -am` upstream restore | F8.4       | **P10**                   | ✅ `multimodule/MultiModulePartialWithAmTest`           |
| I-06 | Parallel build `-T2` cache correctness            | F8.5       | **P11**                   | ✅ `multimodule/ParallelBuildTest`                      |

### Group J: Remote Cache (P1/P2)

| ID   | Scenario                             | Features        | Reference Project | Status                                               |
|------|--------------------------------------|-----------------|-------------------|------------------------------------------------------|
| J-01 | Remote cache read + write via WebDAV | F7.1–F7.3, F7.5 | legacy: `dav`     | ✅ `RemoteCacheDavTest`                               |
| J-02 | Remote unavailable → local fallback  | F7.6            | legacy: `dav`     | ✅ `remote/RemoteUnavailableFallbackTest` (@Disabled) |
| J-03 | `save.final=true` — no overwrite     | F7.4            | legacy: `dav`     | ✅ `remote/SaveFinalRemoteTest` (@Disabled)           |
| J-04 | Baseline URL diff report             | F7.8, F13.6     | **P01** + remote  | ✅ `remote/BaselineDiffTest` (@Disabled)              |

### Group K: Hash Algorithms (P2)

| ID   | Scenario                            | Features | Reference Project | Status                                     |
|------|-------------------------------------|----------|-------------------|--------------------------------------------|
| K-01 | SHA-256 algorithm correct hit/miss  | F3.2     | PARAM P01-P19     | ✅ `hashalgorithm/HashAlgorithmSHA256Test`  |
| K-02 | Invalid algorithm → startup failure | F3.7     | **P01**           | ✅ `hashalgorithm/InvalidHashAlgorithmTest` |

### Group L: Project Versioning (P2)

| ID   | Scenario                                                 | Features    | Reference Project | Status                                                 |
|------|----------------------------------------------------------|-------------|-------------------|--------------------------------------------------------|
| L-01 | SNAPSHOT version bump → cache hit (NORMALIZED_VERSION)   | F5.7, F12.3 | **P16**           | ✅ `versioning/SnapshotVersionBumpCacheHitTest`         |
| L-02 | SNAPSHOT bump + `calculateProjectVersionChecksum` → miss | F12.2       | **P16**           | ✅ `versioning/SnapshotVersionBumpWithChecksumFlagTest` |
| L-03 | `adjustMetaInf=true` normalizes MANIFEST.MF              | F12.1       | **P01**           | ✅ `versioning/MetaInfVersionAdjustmentTest`            |
| L-04 | CI-friendly `${revision}` version: cache key stable      | F12.4       | **P04**           | ✅ `versioning/CIFriendlyRevisionVersionTest`           |

### Group M: Portability & Cross-Environment (P2)

| ID   | Scenario                                                             | Features    | Reference Project | Status                                               |
|------|----------------------------------------------------------------------|-------------|-------------------|------------------------------------------------------|
| M-01 | Absolute path stripped from effective POM → same key across machines | portability | PARAM P01-P19     | ✅ `portability/AbsolutePathNormalizationTest`        |
| M-02 | Tracked `File` property normalized to relative                       | portability | **P19**           | ✅ `portability/TrackedPropertyPathNormalizationTest` |

### Group N: Project Type Variations (P1/P2)

| ID   | Scenario                                                 | Features | Reference Project              | Status                                              |
|------|----------------------------------------------------------|----------|--------------------------------|-----------------------------------------------------|
| N-01 | WAR packaging: web resources cached/restored             | F6.1     | **P17**                        | ✅ `projecttypes/WarPackagingTest`                   |
| N-02 | POM packaging: no source scan, key from effective POM    | F9, F2   | **P02**                        | ✅ `projecttypes/PomPackagingTest`                   |
| N-03 | Test-jar classifier artifact cached                      | F5.2     | **P06**                        | ✅ `projecttypes/TestJarProjectTest`                 |
| N-04 | Shade plugin: shaded JAR is the cached artifact          | F6.1     | new: `shade-plugin-project`    | ✅ `projecttypes/ShadePluginArtifactReplacementTest` |
| N-05 | Assembly plugin: exclusion pattern prevents ZIP in cache | F6.4     | new: `assembly-plugin-project` | ✅ `projecttypes/AssemblyPluginZipExcludeTest`       |

### Group O: Report Generation & Diagnostics (P2/P3)

| ID   | Scenario                                        | Features | Reference Project | Status                               |
|------|-------------------------------------------------|----------|-------------------|--------------------------------------|
| O-01 | `build-cache-report.xml` present after build    | F13.5    | PARAM P01-P19     | ✅ `reports/CacheReportGeneratedTest` |
| O-02 | Report shows CACHED / REBUILT status per module | F13.5    | **P02**           | ✅ `reports/CacheReportStatusTest`    |
| O-03 | `buildinfo.xml` FileHash debug entries          | F13.3    | **P01**           | ✅ `reports/BuildInfoXmlDebugTest`    |

### Group P: Internal Architecture (P2/P3)

| ID   | Scenario                                              | Features   | Reference Project | Status                                        |
|------|-------------------------------------------------------|------------|-------------------|-----------------------------------------------|
| P-01 | Stale `target/` artifacts not leaked into cache       | F6.7       | PARAM P01-P19     | ✅ `internal/StagingRemovesStaleClassesTest`   |
| P-02 | Extra CLI goal after lifecycle goal (regression #399) | implicit   | PARAM P01-P19     | ✅ `internal/AdditionalGoalAfterLifecycleTest` |
| P-03 | `Build` round-trip serialization (unit test)          | XmlService | —                 | ✅ `internal/BuildSerializationRoundTripTest`  |

---

## 4. Required Test Coverage

Master traceability table mapping behaviors to test classes and reference projects.

**Status:** ✅ exists | ⚠️ partial | ❌ missing
**Reference Project:** `P<n>` = reference project · `PARAM` = parametrized across P01-P19
· `legacy: <name>` = existing non-reference project · `new: <name>` = new project needed

| TC#    | Behavior / Test Case                                            | Test Class                                           | Reference Project                 | Priority | Status |
|--------|-----------------------------------------------------------------|------------------------------------------------------|-----------------------------------|----------|--------|
| TC-001 | Build extension loads; first build saved, second restored       | `BuildExtensionTest.simple`                          | PARAM P01-P19                     | P0       | ✅      |
| TC-002 | Core extension loads; same hit/miss cycle                       | `CoreExtensionTest`                                  | PARAM P01-P19                     | P0       | ✅      |
| TC-003 | `maven.build.cache.enabled=false` via CLI disables cache        | `SkipBuildExtensionTest.cacheDisabledViaCommandLine` | PARAM P01-P19                     | P0       | ✅      |
| TC-004 | Adding a source file invalidates cache                          | `AddedSourceFileInvalidatesCacheTest`                | legacy: `checksum-correctness`    | P0       | ✅      |
| TC-005 | Deleting a source file invalidates cache                        | `DeletedSourceFileInvalidatesCacheTest`              | legacy: `checksum-correctness`    | P0       | ✅      |
| TC-006 | Modifying a source file invalidates cache                       | `SourceChangeInvalidatesCacheTest`                   | PARAM P01-P19                     | P0       | ✅      |
| TC-007 | Modifying POM (functional change) invalidates cache             | `PomChangeInvalidatesCacheTest`                      | legacy: `checksum-correctness`    | P0       | ✅      |
| TC-008 | Whitespace-only POM change is a cache hit                       | `WhitespaceOnlyPomChangeNoCacheMissTest`             | legacy: `checksum-correctness`    | P0       | ✅      |
| TC-009 | Resource file change invalidates cache                          | `ResourceChangeInvalidatesCacheTest`                 | legacy: `checksum-correctness`    | P0       | ✅      |
| TC-010 | Test source change invalidates cache                            | `TestSourceChangeInvalidatesCacheTest`               | legacy: `checksum-correctness`    | P0       | ✅      |
| TC-011 | POM `<properties>` value change invalidates cache               | `PropertyChangeInvalidatesCacheTest`                 | PARAM P01-P19                     | P0       | ✅      |
| TC-012 | Dependency version change in POM invalidates cache              | `DependencyVersionChangeInvalidatesCacheTest`        | P01                               | P0       | ✅      |
| TC-013 | `compile` phase cached and restored                             | `CompilePhaseDefaultCachedTest`                      | legacy: `lifecycle-phases`        | P0       | ✅      |
| TC-014 | `test-compile` phase cached and restored                        | `TestCompilePhaseTest`                               | legacy: `lifecycle-phases`        | P0       | ✅      |
| TC-015 | Phase escalation: compile→package forces rebuild                | `CompileThenPackageEscalationTest`                   | PARAM P01-P19                     | P0       | ✅      |
| TC-016 | Phase escalation: package→install forces rebuild                | `PackageThenInstallEscalationTest`                   | legacy: `lifecycle-phases`        | P0       | ✅      |
| TC-017 | Install phase: artifact available in local repo after restore   | `InstallPhaseTest`                                   | legacy: `lifecycle-phases`        | P0       | ✅      |
| TC-018 | Tracked property match → cache hit                              | `TrackedPropertyMatchCacheHitTest`                   | P19                               | P0       | ✅      |
| TC-019 | Tracked property mismatch → cache miss                          | `TrackedPropertyMismatchCacheMissTest`               | P19                               | P0       | ✅      |
| TC-020 | skipValue allows cache hit when skip property set               | `TrackedPropertySkipValueAllowsReuseTest`            | P19                               | P0       | ✅      |
| TC-021 | Full reactor: modules cached independently                      | `Issue21Test`                                        | P02, P10, P11                     | P0       | ✅      |
| TC-022 | Mid-build failure: nothing written to cache                     | `BuildFailsMidwayNoCacheTest`                        | P06                               | P0       | ✅      |
| TC-023 | Global include paths restrict fingerprinted sources             | `IncludeExcludeTest`                                 | legacy: `include-exclude`         | P0       | ✅      |
| TC-024 | Global exclude paths ignore matching files                      | `IncludeExcludeTest`                                 | legacy: `include-exclude`         | P0       | ✅      |
| TC-025 | Global glob pattern selects source subset                       | `IncludeExcludeTest`                                 | legacy: `include-exclude`         | P0       | ✅      |
| TC-026 | Incremental restore + extra output dirs + classifiers           | `IncrementalRestoreTest`                             | legacy: `mbuildcache-incremental` | P1       | ✅      |
| TC-027 | Forked lifecycle execution tracked and cached                   | `ForkedExecutionsTest`                               | P12                               | P1       | ✅      |
| TC-028 | Duplicate goal executions deduped correctly                     | `DuplicateGoalsTest`                                 | P01                               | P1       | ✅      |
| TC-029 | logAll flag dumps all mojo properties                           | `LogAllPropertiesTest`                               | P19                               | P1       | ✅      |
| TC-030 | Per-module skipCache via POM property                           | `PerModuleFlagsTest`                                 | P10                               | P1       | ✅      |
| TC-031 | Per-module cache disabled via POM property                      | `PerModuleFlagsTest`                                 | P10                               | P1       | ✅      |
| TC-032 | `skipCache=true` global: rebuilds all, still writes             | `SkipBuildExtensionTest`                             | PARAM P01-P19                     | P1       | ✅      |
| TC-033 | `skipSave=true`: reads cache, does not write                    | `BuildExtensionTest.skipSaving`                      | PARAM P01-P19                     | P1       | ✅      |
| TC-034 | `mandatoryClean=true`: save blocked without clean               | `MandatoryCleanTest`                                 | PARAM P01-P19                     | P1       | ✅      |
| TC-035 | Corrupted ZIP cache entry: rebuild triggered                    | `CorruptedZipCacheEntryTest`                         | PARAM P01-P19                     | P1       | ✅      |
| TC-036 | Remote cache read and write (WebDAV)                            | `RemoteCacheDavTest`                                 | legacy: `dav`                     | P1       | ✅      |
| TC-037 | clean-only run: cache bypassed, no lookup                       | `SkipBuildExtensionTest.simple`                      | PARAM P01-P19                     | P1       | ✅      |
| TC-038 | Upstream reactor module change → downstream cache miss          | `UpstreamModuleChangeDownstreamMissTest`             | P02                               | P1       | ✅      |
| TC-039 | No `.mvn/maven-build-cache-config.xml`: defaults active         | `NoConfigFileDefaultsTest`                           | P01 (no-config)                   | P1       | ✅      |
| TC-040 | configPath override: non-default XML loaded                     | `CustomConfigPathTest`                               | P01                               | P1       | ✅      |
| TC-041 | Malformed XML config: descriptive error at startup              | `InvalidConfigXmlTest`                               | P01                               | P1       | ✅      |
| TC-042 | Profile activation changes effective POM → cache miss           | `ProfileCliActivationInvalidatesTest`                | P08                               | P1       | ✅      |
| TC-043 | `processPlugins=false`: plugin params not introspected          | `ProcessPluginsDisabledTest`                         | P01                               | P1       | ✅      |
| TC-044 | Project-level glob override in POM `<properties>`               | `PerProjectGlobOverrideTest`                         | P02                               | P1       | ✅      |
| TC-045 | Project-level additional include via POM properties             | `ProjectLevelIncludeTest`                            | P02                               | P1       | ✅      |
| TC-046 | Effective POM property exclusion for specific plugin            | `EffectivePomExcludePropertyTest`                    | P19                               | P1       | ✅      |
| TC-047 | `runAlways` by plugin coordinates: plugin always executes       | `RunAlwaysPluginTest`                                | P19                               | P1       | ✅      |
| TC-048 | `runAlways` by goal name                                        | `RunAlwaysByGoalTest`                                | P19                               | P1       | ✅      |
| TC-049 | `runAlways` by execution ID                                     | `RunAlwaysByExecutionIdTest`                         | P19                               | P1       | ✅      |
| TC-050 | `alwaysRunPlugins` CLI: comma-list of plugin:goal pairs         | `AlwaysRunPluginsCliTest`                            | P01                               | P1       | ✅      |
| TC-051 | `ignoreMissing`: absent cached execution skipped                | `IgnoreMissingPluginTest`                            | P19                               | P1       | ✅      |
| TC-052 | Reconcile `defaultValue` used when property absent              | `TrackedPropertyDefaultValueTest`                    | P19                               | P1       | ✅      |
| TC-053 | Missing cached execution triggers full rebuild                  | `MissingExecutionTriggerRebuildTest`                 | P19                               | P1       | ✅      |
| TC-054 | `restoreGeneratedSources=false`: generated sources not restored | `RestoreGeneratedSourcesFalseTest`                   | P01                               | P1       | ✅      |
| TC-055 | `restoreOnDiskArtifacts=false`: target/ artifacts not restored  | `RestoreOnDiskArtifactsFalseTest`                    | P01                               | P1       | ✅      |
| TC-056 | Partial reactor `-pl module-a`: targeted module only            | `MultiModulePartialBuildTest`                        | P10                               | P1       | ✅      |
| TC-057 | Partial reactor `-pl module-b -am`: upstream restored           | `MultiModulePartialWithAmTest`                       | P10                               | P1       | ✅      |
| TC-058 | WAR packaging: web resources cached and restored                | `WarPackagingTest`                                   | P17                               | P1       | ✅      |
| TC-059 | POM packaging: no source scan, effective POM is key             | `PomPackagingTest`                                   | P02                               | P1       | ✅      |
| TC-060 | CI-friendly `${revision}` version: cache key stable             | `CIFriendlyRevisionVersionTest`                      | P04                               | P1       | ✅      |
| TC-061 | `clean verify`: clean runs before cache lookup                  | `CleanVerifyTest`                                    | PARAM P01-P19                     | P1       | ✅      |
| TC-062 | Output exclude regex pattern filters files from entry           | `OutputExcludePatternTest`                           | P17                               | P2       | ✅      |
| TC-063 | `preservePermissions=true`: POSIX bits restored                 | `PermissionsPreservationTest`                        | P01 (Linux only)                  | P2       | ✅      |
| TC-064 | `maxBuildsCached=2`: third build evicts oldest entry            | `MaxLocalBuildsCachedTest`                           | P01                               | P2       | ✅      |
| TC-065 | `save.final=true`: second remote write rejected                 | `SaveFinalRemoteTest`                                | legacy: `dav`                     | P2       | ✅      |
| TC-066 | Remote server unavailable: falls back to local cache            | `RemoteUnavailableFallbackTest`                      | legacy: `dav`                     | P2       | ✅      |
| TC-067 | `failFast=true`: build aborts on restore failure                | `FailFastTest`                                       | P01                               | P2       | ✅      |
| TC-068 | Parallel build `-T2`: cache correctness maintained              | `ParallelBuildTest`                                  | P11                               | P2       | ✅      |
| TC-069 | Per-plugin `dirScan` limits paths scanned                       | `PerPluginDirScanTest`                               | P19                               | P2       | ❌      |
| TC-070 | Plugin dependency exclusion from fingerprint                    | `ExcludeDependenciesPluginTest`                      | P07                               | P2       | ❌      |
| TC-071 | SHA-256 algorithm: correct hit/miss behavior                    | `HashAlgorithmSHA256Test`                            | PARAM P01-P19                     | P2       | ✅      |
| TC-072 | Invalid hash algorithm: descriptive startup failure             | `InvalidHashAlgorithmTest`                           | P01                               | P2       | ✅      |
| TC-073 | SNAPSHOT version bump → cache hit (NORMALIZED_VERSION)          | `SnapshotVersionBumpCacheHitTest`                    | P16                               | P2       | ✅      |
| TC-074 | SNAPSHOT bump + `calculateProjectVersionChecksum` → miss        | `SnapshotVersionBumpWithChecksumFlagTest`            | P16                               | P2       | ✅      |
| TC-075 | `adjustMetaInf=true`: MANIFEST.MF version normalized            | `MetaInfVersionAdjustmentTest`                       | P01                               | P2       | ✅      |
| TC-076 | Absolute path stripped → same cache key across workspaces       | `AbsolutePathNormalizationTest`                      | PARAM P01-P19                     | P2       | ✅      |
| TC-077 | Tracked `File` property normalized to relative path             | `TrackedPropertyPathNormalizationTest`               | P19                               | P2       | ✅      |
| TC-078 | Test-jar classifier artifact cached and restored                | `TestJarProjectTest`                                 | P06                               | P2       | ✅      |
| TC-079 | Shade plugin: shaded JAR (not empty original) is cached         | `ShadePluginArtifactReplacementTest`                 | new: `shade-plugin-project`       | P2       | ✅      |
| TC-080 | Assembly plugin: output exclusion prevents ZIP in cache         | `AssemblyPluginZipExcludeTest`                       | new: `assembly-plugin-project`    | P2       | ✅      |
| TC-081 | Extra CLI goal after lifecycle goal (regression #399)           | `AdditionalGoalAfterLifecycleTest`                   | PARAM P01-P19                     | P2       | ✅      |
| TC-082 | `build-cache-report.xml` generated after each build             | `CacheReportGeneratedTest`                           | PARAM P01-P19                     | P2       | ✅      |
| TC-083 | Report shows correct CACHED/REBUILT per module                  | `CacheReportStatusTest`                              | P02                               | P2       | ✅      |
| TC-084 | Stale `target/` files not included in new cache entry           | `StagingRemovesStaleClassesTest`                     | PARAM P01-P19                     | P2       | ✅      |
| TC-085 | `scanProfiles` includes active profiles in reactor key          | `ScanProfilesTest`                                   | P08                               | P2       | ✅      |
| TC-086 | `logAllProperties` global flag dumps all params                 | `LogAllPropertiesGlobalTest`                         | P19                               | P3       | ✅      |
| TC-087 | `buildinfo.xml` debug: FileHash entries present                 | `BuildInfoXmlDebugTest`                              | P01                               | P3       | ✅      |
| TC-088 | CacheSource field: LOCAL after local hit                        | `CacheSourceTrackingTest`                            | PARAM P01-P19                     | P3       | ✅      |
| TC-089 | `baselineUrl` diff: `buildsdiff.xml` generated                  | `BaselineDiffTest`                                   | P01 + remote                      | P3       | ✅      |
| TC-090 | `Build` object serializes and deserializes cleanly              | `BuildSerializationRoundTripTest`                    | —                                 | P3       | ✅      |
| TC-091 | Exclusion FILENAME vs PATH rule types (unit)                    | `ExclusionRuleTypesTest`                             | —                                 | P3       | ✅      |
| TC-092 | `nolog` suppresses property from reconcile output               | `TrackedPropertyNologTest`                           | P19                               | P3       | ✅      |

**Summary:**

| Priority  | Total TCs | ✅ Existing | ❌ Missing |
|-----------|-----------|------------|-----------|
| P0        | 25        | 25         | 0         |
| P1        | 36        | 36         | 0         |
| P2        | 24        | 22         | 2         |
| P3        | 7         | 7          | 0         |
| **Total** | **92**    | **90**     | **2**     |

**Additional work in v3 (Group BASE):**

| Class                               | Scenarios              | Reference Projects    | New test runs |
|-------------------------------------|------------------------|-----------------------|---------------|
| `CacheBaseBehaviorParametrizedTest` | 7 (BASE-01 to BASE-07) | P01-P12, P14-P17, P19 | 119           |

---

## 5. Minimal Implementation Backlog

Only **missing (❌) tests** from §4, organized by sprint. Reference projects replace the
"NEW: `project-name`" entries from v2. Only two genuinely new projects are required.

### Sprint 0 — Parametrized Base (cross-cutting, highest ROI)

| Item                         | Class                               | Reference Projects    | Notes                                     |
|------------------------------|-------------------------------------|-----------------------|-------------------------------------------|
| BASE parametrized test class | `CacheBaseBehaviorParametrizedTest` | P01-P12, P14-P17, P19 | 7 scenarios × 17 projects = 119 test runs |

### Sprint 1 — P0 (1 new test)

| TC#    | Test Class                                    | Reference Project | Notes                                             |
|--------|-----------------------------------------------|-------------------|---------------------------------------------------|
| TC-012 | `DependencyVersionChangeInvalidatesCacheTest` | P01               | Build, bump dep version in pom, verify cache miss |

### Sprint 2 — P1 correctness (8 new tests)

| TC#    | Test Class                               | Reference Project | Notes                                                                        |
|--------|------------------------------------------|-------------------|------------------------------------------------------------------------------|
| TC-038 | `UpstreamModuleChangeDownstreamMissTest` | P02               | Change module-api source; verify module-core and module-app also miss        |
| TC-042 | `ProfileCliActivationInvalidatesTest`    | P08               | Profile adds `<plugin>` to effective POM; `-P on` vs `-P off` → miss         |
| TC-044 | `PerProjectGlobOverrideTest`             | P02               | One module sets `maven.build.cache.input.glob={*.java}`; `.txt` change → hit |
| TC-045 | `ProjectLevelIncludeTest`                | P02               | One module sets `maven.build.cache.input.1=extra-dir`; change there → miss   |
| TC-046 | `EffectivePomExcludePropertyTest`        | P19               | `surefire` `argLine` excluded; different values → cache hit                  |
| TC-056 | `MultiModulePartialBuildTest`            | P10               | Build full reactor; then `-pl module-b`; module-b rebuilt, others skipped    |
| TC-057 | `MultiModulePartialWithAmTest`           | P10               | Change module-a; `-pl module-b -am`; module-a restored, module-b rebuilt     |
| TC-060 | `CIFriendlyRevisionVersionTest`          | P04               | Two runs with same `-Drevision=2.0` → hit; switch to `3.0` → miss            |

### Sprint 3 — P1 config coverage (9 new tests)

| TC#    | Test Class                   | Reference Project | Notes                                                                               |
|--------|------------------------------|-------------------|-------------------------------------------------------------------------------------|
| TC-039 | `NoConfigFileDefaultsTest`   | P01 (no-config)   | Set up P01 without `.mvn/maven-build-cache-config.xml`; cache works with XX default |
| TC-040 | `CustomConfigPathTest`       | P01               | Place config at `alt/cache-config.xml`; pass `-Dmaven.build.cache.configPath`       |
| TC-041 | `InvalidConfigXmlTest`       | P01               | Corrupt XML; verify build fails with descriptive non-NPE error                      |
| TC-043 | `ProcessPluginsDisabledTest` | P01               | Set `maven.build.cache.processPlugins=false`; plugin config change → cache hit      |
| TC-047 | `RunAlwaysPluginTest`        | P19               | `<runAlways><plugins>` for `maven-install-plugin`; on cache hit, install still runs |
| TC-048 | `RunAlwaysByGoalTest`        | P19               | `<runAlways><goalsLists>` for goal; on cache hit, that goal still runs              |
| TC-049 | `RunAlwaysByExecutionIdTest` | P19               | `<runAlways><executions>` for specific execId                                       |
| TC-050 | `AlwaysRunPluginsCliTest`    | P01               | `-Dmaven.build.cache.alwaysRunPlugins=maven-install-plugin:install`; install always |
| TC-061 | `CleanVerifyTest`            | PARAM P01-P19     | `mvn clean verify`; clean runs; then cache hit for subsequent identical run         |

### Sprint 4 — P1 behaviors (7 new tests)

| TC#    | Test Class                           | Reference Project | Notes                                                                          |
|--------|--------------------------------------|-------------------|--------------------------------------------------------------------------------|
| TC-051 | `IgnoreMissingPluginTest`            | P19               | Plugin in `<ignoreMissing>` absent from cached build; no error, cache hit      |
| TC-052 | `TrackedPropertyDefaultValueTest`    | P19               | Tracked property absent; `defaultValue="false"` substituted; match → hit       |
| TC-053 | `MissingExecutionTriggerRebuildTest` | P19               | Cache has `compile` only; current build needs `test`; missing → full rebuild   |
| TC-054 | `RestoreGeneratedSourcesFalseTest`   | P01               | Cache hit; `-Dmaven.build.cache.restoreGeneratedSources=false`; no gen-sources |
| TC-055 | `RestoreOnDiskArtifactsFalseTest`    | P01               | Cache hit; `-Dmaven.build.cache.restoreOnDiskArtifacts=false`; no target/      |
| TC-058 | `WarPackagingTest`                   | P17               | WAR project: build, restore; verify `WEB-INF/` content from cache              |
| TC-059 | `PomPackagingTest`                   | P02               | POM aggregator module: no source scan; effective POM change → miss             |

### Sprint 5 — P2 advanced (12 new tests)

| TC#    | Test Class                      | Reference Project | Notes                                                                              |
|--------|---------------------------------|-------------------|------------------------------------------------------------------------------------|
| TC-062 | `OutputExcludePatternTest`      | P17               | `<output><exclude>.*\.war</patterns>` → WAR not in cache bundle                    |
| TC-063 | `PermissionsPreservationTest`   | P01 (Linux only)  | Attach executable script; restore; verify `rwxr-xr-x` bits                         |
| TC-064 | `MaxLocalBuildsCachedTest`      | P01               | `<maxBuildsCached>2</maxBuildsCached>`; 3 distinct builds; oldest evicted          |
| TC-065 | `SaveFinalRemoteTest`           | legacy: `dav`     | Push once with `save.final=true`; second PUT rejected                              |
| TC-066 | `RemoteUnavailableFallbackTest` | legacy: `dav`     | Mock 503; falls back to local cache; build succeeds                                |
| TC-067 | `FailFastTest`                  | P01               | `-Dmaven.build.cache.failFast=true`; corrupt remote cache; verify abort            |
| TC-068 | `ParallelBuildTest`             | P11               | `-T2` on reactor; verify all modules correctly cached/restored                     |
| TC-069 | `PerPluginDirScanTest`          | P19               | `<dirScan>` on `maven-compiler-plugin` to `src/main/java`; resource change → hit   |
| TC-070 | `ExcludeDependenciesPluginTest` | P07               | `excludeDependencies=true` on plugin; bump its dep version → cache still hits      |
| TC-071 | `HashAlgorithmSHA256Test`       | PARAM P01-P19     | `<hashAlgorithm>SHA-256</hashAlgorithm>`; standard hit/miss cycle passes           |
| TC-072 | `InvalidHashAlgorithmTest`      | P01               | `<hashAlgorithm>BOGUS</hashAlgorithm>`; verify startup fails descriptively         |
| TC-085 | `ScanProfilesTest`              | P08               | `<multiModule><scanProfiles>`: subtree build with `-P myprofile` discovers reactor |

### Sprint 6 — P2 portability, versioning, project types (10 new tests)

| TC#    | Test Class                                | Reference Project              | Notes                                                                          |
|--------|-------------------------------------------|--------------------------------|--------------------------------------------------------------------------------|
| TC-073 | `SnapshotVersionBumpCacheHitTest`         | P16                            | `1.0-SNAPSHOT` → `1.1-SNAPSHOT`; no other change → HIT                         |
| TC-074 | `SnapshotVersionBumpWithChecksumFlagTest` | P16                            | Same bump + `<calculateProjectVersionChecksum>true` → MISS                     |
| TC-075 | `MetaInfVersionAdjustmentTest`            | P01                            | `<adjustMetaInf>true</adjustMetaInf>`; restore JAR; verify MANIFEST.MF version |
| TC-076 | `AbsolutePathNormalizationTest`           | PARAM P01-P19                  | Build in dir1; copy cache; restore in dir2; same cache key                     |
| TC-077 | `TrackedPropertyPathNormalizationTest`    | P19                            | Tracked `File` set to absolute path; verify normalized to relative             |
| TC-078 | `TestJarProjectTest`                      | P06                            | Add `test-jar` goal to P06; test JAR cached and restored with classifier       |
| TC-079 | `ShadePluginArtifactReplacementTest`      | new: `shade-plugin-project`    | Shade replaces JAR; verify shaded JAR (not original) in cache                  |
| TC-080 | `AssemblyPluginZipExcludeTest`            | new: `assembly-plugin-project` | Assembly produces `.zip`; exclude pattern → ZIP not in cache bundle            |
| TC-081 | `AdditionalGoalAfterLifecycleTest`        | PARAM P01-P19                  | `mvn package dependency:tree`; lifecycle cached; extra goal runs always        |
| TC-083 | `CacheReportStatusTest`                   | P02                            | Multi-module: one hit, one miss; report shows CACHED vs REBUILT correctly      |

### Sprint 7 — P2/P3 diagnostic (9 new tests)

| TC#    | Test Class                        | Type | Reference Project | Notes                                                                        |
|--------|-----------------------------------|------|-------------------|------------------------------------------------------------------------------|
| TC-082 | `CacheReportGeneratedTest`        | IT   | PARAM P01-P19     | After `mvn verify`; verify `target/incremental-maven/build-cache-report.xml` |
| TC-084 | `StagingRemovesStaleClassesTest`  | IT   | PARAM P01-P19     | Pre-stage stale `.class` in `target/`; build; stale class NOT in cache       |
| TC-086 | `LogAllPropertiesGlobalTest`      | IT   | P19               | `<logAllProperties>true</logAllProperties>`; all mojo params logged          |
| TC-087 | `BuildInfoXmlDebugTest`           | IT   | P01               | `<debugs><debug>FileHash</debug>`; per-file hash entries in `buildinfo.xml`  |
| TC-088 | `CacheSourceTrackingTest`         | IT   | PARAM P01-P19     | Second build: `source=LOCAL`; remote build: `source=REMOTE`                  |
| TC-089 | `BaselineDiffTest`                | IT   | P01 + remote      | `-Dmaven.build.cache.baselineUrl=…`; verify `buildsdiff.xml` generated       |
| TC-090 | `BuildSerializationRoundTripTest` | unit | —                 | `Build` with all fields → `toBytes()` → `loadBuild()` → identical            |
| TC-091 | `ExclusionRuleTypesTest`          | unit | —                 | FILENAME vs PATH rule, FILE/DIRECTORY/ALL entry types                        |
| TC-092 | `TrackedPropertyNologTest`        | IT   | P19               | `<property nolog="true">`: property NOT logged in reconcile output           |

---

## 6. Coverage Gaps & Recommendations

### What v3 adds over v2

The **project-agnostic paradigm** is the defining contribution of v3:

1. **`CacheBaseBehaviorParametrizedTest`** (Sprint 0) is the highest-ROI single test class
   to write. It runs 7 fundamental behaviors against 17 reference projects, producing 119
   test runs that prove the extension is truly project-agnostic. This class alone eliminates
   the most critical category of unknown failures (does cache work with P10's nested reactor?
   with P04's CI-friendly version? with P08's profile-activated effective POM?).

2. **Eliminated 28 "NEW project" entries from v2**. Every test that previously required a
   bespoke project now maps to an existing reference project, reducing test infrastructure
   maintenance cost.

3. **Two new projects remain justified:**
    - `shade-plugin-project`: shade replaces the primary JAR, a packaging pattern not
      represented in any reference project.
    - `assembly-plugin-project`: assembly produces a supplementary ZIP that the extension
      must handle via output exclusion; no reference project uses the assembly plugin.

### What v2 got right (unchanged)

- The feature-centric catalog (F1–F13) remains the definitive reference for all 21 system
  properties and their XML equivalents (see §7).
- The orthogonal test matrix principle: each distinct behavior appears in one primary row.
- The priority ordering (P0 → P3) which drives sprint planning.

### Known exclusions from full parametrization

| Project | Why excluded from BASE                          | Recommended approach                                   |
|---------|-------------------------------------------------|--------------------------------------------------------|
| P13     | Requires JDK at specific `toolchains.xml` paths | Conditional skip; run in CI with toolchains configured |
| P18     | Requires Maven 4 binary                         | Run in Maven 4 CI job only                             |

### Recommended implementation order

```
Sprint 0 (project-agnostic proof):  CacheBaseBehaviorParametrizedTest (119 runs)
Sprint 1 (P0 correctness):          TC-012
Sprint 2 (P1 correctness):          TC-038, TC-042, TC-044–TC-046, TC-056–TC-057, TC-060
Sprint 3 (P1 config coverage):      TC-039–TC-041, TC-043, TC-047–TC-051, TC-061
Sprint 4 (P1 behaviors):            TC-052–TC-055, TC-058–TC-059
Sprint 5 (P2 advanced):             TC-062–TC-072, TC-085
Sprint 6 (P2 portability+types):    TC-073–TC-081, TC-083
Sprint 7 (P2/P3 diagnostic):        TC-082, TC-084, TC-086–TC-092
```

---

## 7. Appendix: Property Reference

All `maven.build.cache.*` properties recognized by `CacheConfigImpl`. Resolution order:
**user properties** (CLI `-D`) → **system properties** → default.

| Property                                    | Default                                                | Type       | XML equivalent                                     |
|---------------------------------------------|--------------------------------------------------------|------------|----------------------------------------------------|
| `maven.build.cache.configPath`              | `(multimodule root)/.mvn/maven-build-cache-config.xml` | path       | —                                                  |
| `maven.build.cache.enabled`                 | `true`                                                 | boolean    | `<configuration><enabled>`                         |
| `maven.build.cache.location`                | `~/.m2/build-cache`                                    | path       | `<configuration><local><location>`                 |
| `maven.build.cache.remote.enabled`          | `false`                                                | boolean    | `<configuration><remote enabled="…">`              |
| `maven.build.cache.remote.url`              | —                                                      | URL        | `<configuration><remote><url>`                     |
| `maven.build.cache.remote.server.id`        | —                                                      | string     | `<configuration><remote id="…">`                   |
| `maven.build.cache.remote.save.enabled`     | `false`                                                | boolean    | `<configuration><remote><save><enabled>`           |
| `maven.build.cache.remote.save.final`       | `false`                                                | boolean    | — (CLI only)                                       |
| `maven.build.cache.failFast`                | `false`                                                | boolean    | — (CLI only)                                       |
| `maven.build.cache.baselineUrl`             | —                                                      | URL        | — (CLI only)                                       |
| `maven.build.cache.lazyRestore`             | `false`                                                | boolean    | — (CLI only)                                       |
| `maven.build.cache.restoreGeneratedSources` | `true`                                                 | boolean    | — (CLI / POM property)                             |
| `maven.build.cache.restoreOnDiskArtifacts`  | `true`                                                 | boolean    | — (CLI / POM property)                             |
| `maven.build.cache.alwaysRunPlugins`        | —                                                      | comma-list | `<executionControl><runAlways>` (partial override) |
| `maven.build.cache.skipCache`               | `false`                                                | boolean    | — (CLI / POM property)                             |
| `maven.build.cache.skipSave`                | `false`                                                | boolean    | — (CLI only)                                       |
| `maven.build.cache.mandatoryClean`          | `false`                                                | boolean    | `<configuration><mandatoryClean>`                  |
| `maven.build.cache.processPlugins`          | `true`                                                 | boolean    | — (POM property per-project)                       |
| `maven.build.cache.input.glob`              | —                                                      | glob       | `<input><global><glob>` (per-project override)     |
| `maven.build.cache.input.*`                 | —                                                      | path       | `<input><global><includes>` (per-project)          |
| `maven.build.cache.exclude.*`               | —                                                      | path/glob  | `<input><global><excludes>` (per-project)          |

### Key XML-only elements

| XML Element                                                           | Description                                                                           |
|-----------------------------------------------------------------------|---------------------------------------------------------------------------------------|
| `<configuration><hashAlgorithm>`                                      | `XX` (default), `XXMM`, `METRO`, `METRO+MM`, `SHA-1`, `SHA-256`, `SHA-384`, `SHA-512` |
| `<configuration><attachedOutputs><dirName glob="…">`                  | Extra output dirs to capture/restore beyond primary artifact                          |
| `<configuration><attachedOutputs preservePermissions="…">`            | Restore POSIX permissions (default `true`)                                            |
| `<configuration><local><maxBuildsCached>`                             | LRU limit on local cache entries per project                                          |
| `<configuration><multiModule><scanProfiles>`                          | Profile names to include in multi-module reactor key                                  |
| `<configuration><projectVersioning><adjustMetaInf>`                   | Normalize `Implementation-Version` in MANIFEST.MF                                     |
| `<configuration><projectVersioning><calculateProjectVersionChecksum>` | Include project version in cache key                                                  |
| `<input><global><glob>`                                               | Global file glob for source selection                                                 |
| `<input><global><includes>`                                           | Additional always-included paths                                                      |
| `<input><global><excludes>`                                           | Always-excluded paths                                                                 |
| `<input><plugins><plugin><dirScan>`                                   | Per-plugin directory scan override                                                    |
| `<input><plugins><plugin><effectivePom><excludeProperties>`           | POM properties excluded from plugin fingerprint                                       |
| `<input><plugins><plugin><excludeDependencies>`                       | Exclude plugin dependencies from fingerprint                                          |
| `<executionControl><runAlways><plugins>`                              | Plugins that always run regardless of cache hit                                       |
| `<executionControl><runAlways><goalsLists>`                           | Goals that always run                                                                 |
| `<executionControl><runAlways><executions>`                           | Execution IDs that always run                                                         |
| `<executionControl><ignoreMissing><plugins>`                          | Executions to silently skip if absent in cached build                                 |
| `<executionControl><reconcile><plugins>`                              | Tracked plugin parameters for key reconciliation                                      |
| `<executionControl><reconcile><logAllProperties>`                     | Dump all plugin parameters globally                                                   |
| `<output><exclude><patterns>`                                         | Regex patterns for files excluded from cache bundle                                   |

---
