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

# Reference Test Projects — Usage Guide

[reference-test-projects](reference-test-projects) directory contains **18 orthogonal Maven project configurations** (
P01–P18) that serve as
the shared fixture set for all integration tests of the Maven Build Cache
Extension.See [maven-test-projects-universe.md](../../../documentation/maven-test-projects-universe.md)
for design rationale, full test dimensions matrix.

New generic features are expected to pass against all the Maven reference projects.
If test need some unique Maven project configuration (unlikely), create a new project or extend on ot the most matching
reference projects.

---

## Design Principles

**One dimension per project.** Each configuration introduces exactly one Maven behavior that
is not covered by any other project. This orthogonality keeps test failures attributable — if a
test fails on P07 but not on P02, the bug is in the `maven-plugin` lifecycle handling, not in multi-module
inheritance.

**Extension-agnostic sources.** The Maven sources inside each project (pom.xml, Java classes)
are intentionally plain Maven projects. The build-cache extension is loaded only via the
`.mvn/extensions.xml` or the `<build><extensions>` stanza — never hard-coded into application
logic.

**Stable, self-contained.** Each project builds cleanly with a vanilla `mvn verify` (modulo
documented prerequisites). Tests never rely on a pre-populated local repository beyond what
Maven downloads in a normal build.

**Use parent version properties for plugin versions.** In ITs use the version properties for plugins defined in
parent to keep them updated in ITs without additional work.
If there is no version property defined for a certain plugin, then define one in the parent of this extension.
**DO NOT USE FIXED VERSIONS IN ITs**.

---

## Directory Layout Conventions

```
pNN-short-name/
  readme.md                       ← purpose, unique dimension, how-to-run
  pom.xml                         ← root Maven POM
  .mvn/
    extensions.xml                ← core extension registration (present in most projects)
    maven-build-cache-config.xml  ← extension configuration defaults for this project
    maven.config                  ← default CLI flags (only P04, P18)
    toolchains.xml                ← JDK toolchains (only P13)
  src/main/java/...               ← minimal application source (single-module projects)
  module-*/  or  <name>/          ← child modules (multi-module projects)
  test-options.txt                ← extra CLI args to inject by ReferenceProjectBootstrap (optional)
  test-settings.xml               ← settings file passed via -s (optional, P08, P09)
  trigger.properties              ← file-activation trigger for profile tests (P08 only)
  pre-install-dirs.txt            ← sub-directories to `mvn install` before the main build (optional)
  _<helper-name>/                 ← pre-requisite helper projects (P10: _corp-parent, P15: _new-artifact etc.)
```

### Special files explained

| File                   | Projects      | Purpose                                                                                                                                  |
|------------------------|---------------|------------------------------------------------------------------------------------------------------------------------------------------|
| `test-options.txt`     | P08, P09, P13 | One CLI token per line (e.g. `-s test-settings.xml`). Read by `ReferenceProjectBootstrap` and added as CLI options to the `Verifier`.    |
| `test-settings.xml`    | P08, P09      | Custom Maven settings file referenced from `test-options.txt`.                                                                           |
| `pre-install-dirs.txt` | P10, P15      | Subdirectory names (one per line) to install before the main project. Bootstrap runs `mvn install` on each listed subdirectory in order. |
| `trigger.properties`   | P08           | Exists in VCS; its presence activates the `by-file` Maven profile.                                                                       |
| `.mvn/toolchains.xml`  | P13           | Defines local JDK paths. Contains `@JAVA_HOME@` / `@JDK_MAJOR_VERSION@` tokens replaced at test runtime.                                 |
| `.mvn/maven.config`    | P04, P18      | Default CLI arguments applied to every Maven invocation in that directory.                                                               |

### Token substitution

`ReferenceProjectBootstrap` performs a text-based substitution on all `.xml`, `.properties`,
`.txt`, and `.java` files after copying the project to the test work directory:

| Token                 | Replaced with                                                 |
|-----------------------|---------------------------------------------------------------|
| `@JAVA_HOME@`         | `System.getProperty("java.home")`                             |
| `@JDK_MAJOR_VERSION@` | Major version from `java.specification.version` (e.g. `"21"`) |

Binary files are skipped. Use these tokens in `toolchains.xml` or other config files that
reference the local JDK installation.

---

## Project Catalogue

See `documentation/maven-test-projects-universe.md` and readme file in project directories for details.

## Test Infrastructure

### How a reference project becomes a test

```
CacheBaseBehaviorParametrizedTest
  @ForEachReferenceProject                      ← annotation on test method
  void base01FirstBuildSavesSecondHits(Path projectDir)
      Verifier v = ReferenceProjectBootstrap.prepareProject(projectDir, "BASE01");
      v.executeGoal("verify");
      v.verifyTextInLog(CACHE_SAVED);
```

1. `@ForEachReferenceProject` → `ProjectsArgumentsProvider` → `ReferenceProjectBootstrap.listProjects()`
   enumerates all eligible projects from this directory.
2. `ReferenceProjectBootstrap.prepareProject(projectDir, qualifier)`:
    - Copies the project to `target/mvn-cache-tests/ReferenceProjectTest/<projectId>-<qualifier>/project/`
    - Substitutes `@JAVA_HOME@` / `@JDK_MAJOR_VERSION@` tokens in text files
    - Runs `mvn install` on any directories listed in `pre-install-dirs.txt`
    - Adds `-Dmaven.build.cache.location=...` pointing at an isolated cache directory
    - Adds CLI options from `test-options.txt`
    - Returns a configured `Verifier`
3. The test body executes one or more Maven builds via the `Verifier` and asserts on log output.

### Key test infrastructure classes

| Class                       | Location     | Role                                                                 |
|-----------------------------|--------------|----------------------------------------------------------------------|
| `ReferenceProjectBootstrap` | `its/`       | Copies, tokenizes, and prepares a project for a `Verifier`           |
| `MavenSetup`                | `its/`       | Locates the Maven home (`target/maven3` or `target/maven4`)          |
| `IntegrationTestExtension`  | `its/junit/` | JUnit 5 extension for `@IntegrationTest`-annotated tests             |
| `ForEachReferenceProject`   | `its/junit/` | Meta-annotation that drives parametrized iteration over all projects |
| `ProjectsArgumentsProvider` | `its/junit/` | `ArgumentsProvider` backing `@ForEachReferenceProject`               |

### Parametrized vs targeted tests

**Parametrized (cross-project):** Use `@ForEachReferenceProject`. The same assertion runs against
every eligible project. Examples: `CacheBaseBehaviorParametrizedTest` (BASE-01 through BASE-07),
`CoreExtensionTest`.

**Targeted (single project):** Use
`ReferenceProjectBootstrap.prepareProject(Paths.get("src/test/projects/reference-test-projects/pNN-name"))` directly.
Examples: `CacheInvalidationProjectTraitsTest`, `SnapshotVersionBumpCacheHitTest`.

### Project exclusion rules

The following exclusions are enforced automatically by `ReferenceProjectBootstrap.listProjects()`:

| Project                    | Exclusion type      | Reason                                                                     |
|----------------------------|---------------------|----------------------------------------------------------------------------|
| P13 (`p13-toolchains-jdk`) | Always excluded     | Requires locally-installed JDKs registered in `toolchains.xml`             |
| P18 (`p18-maven4-native`)  | Excluded on Maven 3 | Uses Maven 4-only features (`<subprojects>`, `<packaging>bom</packaging>`) |

Individual tests can exclude additional projects per-method via
`@ForEachReferenceProject(exclude = {"p03-remote-parent"})`.

---

## How to Add a New Reference Project

Follow these steps to add a new project **pNN-my-feature**:

1. **Create the directory** `src/test/projects/reference-test-projects/pNN-my-feature/`.

2. **Write `readme.md`** following the convention:
   ```markdown
   # PNN — my-feature
   **Unique behavior:** One sentence describing the single Maven dimension this adds.
   ## Setup
   ## What it verifies
   ## How to run
   ```

3. **Write `pom.xml`** exercising the single new dimension. Keep everything else as
   simple as possible — prefer the pattern of the nearest existing project.

4. **Add `.mvn/extensions.xml`** to load the build-cache extension as a core extension
   (copy from any existing project and adjust the version placeholder `${projectVersion}`).

5. **Add `.mvn/maven-build-cache-config.xml`** with the minimal configuration needed.

6. **Add `test-options.txt`** if the project requires extra CLI options (e.g. `-s test-settings.xml`).

7. **Add `pre-install-dirs.txt`** if the project requires prerequisite helper artifacts.

8. **Update `ReferenceProjectBootstrap`** only if the new project needs a new exclusion rule
   (e.g. it requires a specific Maven version or external tool).

9. **Verify** that `CacheBaseBehaviorParametrizedTest` picks up the new project and all
   BASE-01 through BASE-07 scenarios pass:
   ```
   mvn verify -Prun-its -Dit.test="CacheBaseBehaviorParametrizedTest#base01FirstBuildSavesSecondHits[pNN-my-feature]"
   ```

10. **Add a targeted test** (if needed) in the appropriate test class under
    `src/test/java/org/apache/maven/buildcache/its/` that exercises the specific cache
    behavior unique to the new project.

### Naming convention

| Slot         | Convention                            | Example                               |
|--------------|---------------------------------------|---------------------------------------|
| Directory    | `pNN-short-kebab-name`                | `p19-cache-lifecycle`                 |
| Java package | `org.apache.maven.caching.test.pNN.*` | `org.apache.maven.caching.test.p19.*` |
| GroupId      | `org.apache.maven.caching.test.pNN`   | `org.apache.maven.caching.test.p19`   |
| ArtifactId   | `pNN-short-name`                      | `p19-cache-lifecycle`                 |

---

## Frequently Asked Questions

**Q: Can I add a second source file to an existing project to test a new scenario?**
A: Only if the extra file is strictly required by the project's unique Maven dimension. For
cache-invalidation scenarios it is almost always better to add a separate targeted test that
copies the existing project and then mutates it.

**Q: Why does `@ForEachReferenceProject` run 7 × N test cases?**
A: `CacheBaseBehaviorParametrizedTest` defines 7 test methods (BASE-01 through BASE-07) and
each method runs once per eligible project. With 17 eligible projects (P01–P17, excluding P13),
that is 119 test invocations.

**Q: How do I run only the tests for one project?**

```
mvn verify -Prun-its -Dit.test="CacheBaseBehaviorParametrizedTest#base01FirstBuildSavesSecondHits[p05-bom-single]"
```

**Q: How do I run all parametrized tests?**

```
mvn verify -Prun-its -Dgroups=project-parametrized
```

**Q: How do I debug a reference project manually?**
Run it directly from its source directory after copying it somewhere temporary:

```bash
cd src/test/projects/reference-test-projects/p02-local-parent-inherit
mvn verify
mvn verify   # should be a cache hit
```

Note: A manual run uses your global Maven settings and local repository; test runs use the
isolated `Verifier`-managed local repository.

**Q: My new project needs a specific Maven version — how do I skip it on the other version?**
Add a constant and a filter in `ReferenceProjectBootstrap.listProjects()` mirroring the
existing `MAVEN4_ONLY_PROJECT` pattern. Document the exclusion in this file under
[Project exclusion rules](#project-exclusion-rules).
