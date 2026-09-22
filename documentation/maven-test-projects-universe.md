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

# Maven Reference Test Projects

This document defines the **18 reference project configurations** (P01–P18) used by the
Build Cache Extension integration tests. Each configuration exercises exactly one Maven
behavior dimension not covered by any other, so that a test failure points unambiguously
to the feature being tested.

Projects live in `src/test/projects/reference-test-projects/`. They are defined entirely
in terms of Maven features — dependency management strategy, parent resolution, profile
activation, reactor topology, packaging type, and so on. No build-cache-extension-specific
configuration appears in the projects themselves. Extension-specific scenarios (P19 and above)
are tracked separately in `maven-test-projects-ext-specific.md`.

**Design principle:** Every configuration adds exactly one new Maven behavior dimension.
No two configurations share the same primary unique dimension.

---

## 1. Maven Behavior Coverage Matrix

The tables below show which Maven behavior dimensions are covered by each project and at
which tier of importance. Use this to quickly find which project to target for a new test.

### Tier 1 — Critical (directly determines build correctness)

#### G — Property Sources & Precedence

| Sub-dimension                                         | Covered by       |
|-------------------------------------------------------|------------------|
| POM `<properties>`                                    | P01–P18 (all)    |
| Parent-inherited properties                           | P02, P04, P10    |
| Profile-activated properties                          | P08              |
| `settings.xml` profiles + properties                  | **P08**, **P09** |
| CLI `-D`                                              | P04, **P08**     |
| Environment variables `${env.*}`                      | **P08**          |
| CI-friendly `${revision}`, `${sha1}`, `${changelist}` | **P04**          |

#### A — Project Structure

| Sub-dimension              | Covered by                                            |
|----------------------------|-------------------------------------------------------|
| Single module              | P01, P03, P05, P06, P07, P08, P09, P13, P14, P15, P16 |
| Multi-module flat          | P02, P04, P11, P12, P16, P17, P18                     |
| Multi-module nested        | **P10**                                               |
| Mixed packaging in reactor | P10, P17                                              |

#### B — Parent Resolution

| Sub-dimension                           | Covered by                                       |
|-----------------------------------------|--------------------------------------------------|
| Super POM only                          | P01, P03, P05, P06, P07, P08, P09, P13, P14, P15 |
| Local reactor parent                    | P02, P04, P10–P12, P16–P18                       |
| Remote parent (empty `<relativePath/>`) | **P03**                                          |
| Inherited groupId/version from parent   | P02, P04                                         |
| Chained parents (external → reactor)    | **P10**                                          |

#### C — Dependency Management

| Sub-dimension                           | Covered by                      |
|-----------------------------------------|---------------------------------|
| No management (inline versions)         | P01, P05, P07, P08              |
| Parent-defined `<dependencyManagement>` | P02, P03, P04, P10–P12, P16–P18 |
| Single BOM import                       | **P05**                         |
| Multiple BOMs + mediation               | **P06**                         |
| Layered parent + child override         | P04, P10                        |
| SNAPSHOT inside reactor                 | **P16**                         |
| System scope                            | **P06**                         |
| Optional dependency                     | **P06**                         |
| Classifier usage                        | **P06**                         |
| Layered `<exclusions>`                  | **P06**                         |
| Dependency convergence conflict         | **P06**                         |

#### H — Reactor Behavior

| Sub-dimension                 | Covered by                 |
|-------------------------------|----------------------------|
| Full reactor                  | P02, P04, P10–P12, P16–P18 |
| Partial build (`-pl :m -am`)  | **P10**                    |
| Partial build (`-pl :m` only) | **P10**                    |
| Resume (`-rf :m`)             | **P10**                    |
| Parallel (`-T N`)             | **P11**                    |
| Build order                   | P02, P04, **P10**          |

### Tier 2 — High (significantly affects build behavior)

#### D — Plugin Configuration

| Sub-dimension                     | Covered by                           |
|-----------------------------------|--------------------------------------|
| Default lifecycle (Super POM)     | P01                                  |
| `<pluginManagement>`              | P02, P04, P07, P08, P11–P12, P17–P18 |
| Child override of managed plugin  | P02, P04                             |
| Lifecycle phase rebinding         | **P07**                              |
| Plugin classpath `<dependencies>` | **P07**                              |
| Enforcer rules                    | **P06**                              |

#### E — Profiles

| Sub-dimension           | Covered by |
|-------------------------|------------|
| Property activation     | **P08**    |
| JDK activation          | **P08**    |
| OS activation           | **P08**    |
| File activation         | **P08**    |
| `activeByDefault` reset | **P08**    |
| `settings.xml` profiles | **P08**    |
| CLI `-P`                | **P08**    |
| Merge & precedence      | **P08**    |

#### I — Extensions & Packaging

| Sub-dimension                          | Covered by                     |
|----------------------------------------|--------------------------------|
| Build extension                        | P07, **P12**                   |
| Core extension (`.mvn/extensions.xml`) | **P12**                        |
| `jar`                                  | P01–P06, P08–P12, P14–P16, P18 |
| `war`                                  | **P17**                        |
| `pom` / BOM aggregator                 | P02, P04, P10                  |
| `bom` (Maven 4 native)                 | **P18**                        |
| `maven-plugin` (custom lifecycle)      | **P07**                        |
| Lifecycle participant                  | **P07**, **P12**               |

### Tier 3 — Medium (resolution policies, delivery)

#### F — Repositories & Resolution

| Sub-dimension                           | Covered by     |
|-----------------------------------------|----------------|
| Default Central                         | All (implicit) |
| POM-defined `<repositories>`            | **P09**        |
| `<pluginRepositories>`                  | **P09**        |
| `settings.xml` mirrors                  | **P09**        |
| SNAPSHOT update policies                | **P09**, P16   |
| Snapshot vs release repository policies | **P14**        |

#### J — Toolchains

| Sub-dimension                               | Covered by |
|---------------------------------------------|------------|
| External JDK selection via `toolchains.xml` | **P13**    |

#### K — Distribution & Publication

| Sub-dimension                      | Covered by |
|------------------------------------|------------|
| `<distributionManagement>`         | **P14**    |
| SNAPSHOT deploy                    | **P14**    |
| Release deploy                     | **P14**    |
| Deploy lifecycle (`deploy:deploy`) | **P14**    |
| Artifact relocation                | **P15**    |

---

## 2. Configuration Reference Table

Quick-reference table of all 18 projects. The **Unique Maven behavior** column is the
primary dimension — the one that no other project exercises.

**Column legend**

| Column      | Values                                                                                                                                             |
|-------------|----------------------------------------------------------------------------------------------------------------------------------------------------|
| Struct      | `1` single module · `MM-flat` flat multi-module · `MM-nest` nested hierarchical                                                                    |
| Parent      | `super` Super POM only · `local` reactor parent · `remote` external artifact · `chain` chained external                                            |
| Dep Mgmt    | `inline` explicit versions · `inherit` parent-managed · `bom1` single BOM · `multi-bom` multiple BOMs · `mixed`                                    |
| Plugin Mgmt | `inline` · `pm` `<pluginManagement>` · `inherit`                                                                                                   |
| Profiles    | `—` none · `prop` property trigger · `os` OS · `file` file-exists · `jdk` JDK range · `abd` activeByDefault · `settings` settings.xml · `cli` `-P` |
| Maven       | `3` · `4` · `3+4` both                                                                                                                             |

| ID  | Short Name           | Struct    | Maven | Parent | Pkg     | Dep Mgmt  | Plugin Mgmt | Profiles                          | Unique Maven behavior introduced                                                                  |
|-----|----------------------|-----------|-------|--------|---------|-----------|-------------|-----------------------------------|---------------------------------------------------------------------------------------------------|
| P01 | superpom-minimal     | `1`       | `3+4` | super  | jar     | inline    | inline      | —                                 | Super POM defaults; default lifecycle bindings; system-scope dependency                           |
| P02 | local-parent-inherit | `MM-flat` | `3+4` | local  | jar     | inherit   | inherit     | —                                 | Local `relativePath`; inherited groupId/version/deps/plugins; topological build order             |
| P03 | remote-parent        | `1`       | `3`   | remote | jar     | inherit   | inherit     | —                                 | Empty `<relativePath/>` → parent resolved from repository                                         |
| P04 | ci-friendly          | `MM-flat` | `3+4` | local  | jar     | inherit   | pm          | —                                 | `${revision}` + `${sha1}` + `${changelist}`; flatten-maven-plugin normalizes installed POM        |
| P05 | bom-single           | `1`       | `3+4` | super  | jar     | bom1      | inline      | —                                 | Single BOM `scope=import`; version management without a parent                                    |
| P06 | dep-edge-cases       | `MM-flat` | `3+4` | local  | jar     | multi-bom | pm          | —                                 | Multi-BOM mediation order; system scope; optional; classifier; layered exclusions; enforcer       |
| P07 | plugin-rebinding     | `1`       | `3+4` | super  | plugin  | inline    | pm          | —                                 | `maven-plugin` packaging; lifecycle phase rebinding; plugin classpath `<dependencies>`            |
| P08 | profiles-all         | `1`       | `3`   | super  | jar     | inline    | pm          | prop+os+file+jdk+abd+settings+cli | All profile activation types; all property sources (POM/profile/settings/CLI/-D/env)              |
| P09 | repos-mirrors        | `1`       | `3+4` | super  | jar     | inline    | inline      | —                                 | POM `<repositories>`; `<pluginRepositories>`; `settings.xml` mirror; snapshot `updatePolicy`      |
| P10 | reactor-partial      | `MM-nest` | `3+4` | chain  | jar     | inherit   | pm          | —                                 | Nested reactor; `-pl -am`; `-pl`; `-rf` resume; chained external parent (`<relativePath/>` empty) |
| P11 | reactor-parallel     | `MM-flat` | `3+4` | local  | jar     | inherit   | inherit     | —                                 | `-T N` concurrent execution; thread-safety of independent module builds                           |
| P12 | core-ext-forked      | `MM-flat` | `3+4` | local  | jar     | inherit   | pm          | —                                 | Core extension (`.mvn/extensions.xml`); `maven-invoker-plugin` forked child Maven process         |
| P13 | toolchains-jdk       | `1`       | `3`   | super  | jar     | inline    | pm          | —                                 | `toolchains.xml`; external JDK selection; compiler toolchain interaction                          |
| P14 | distrib-deploy       | `1`       | `3+4` | super  | jar     | inline    | pm          | —                                 | `<distributionManagement>`; snapshot vs release repository selection; deploy lifecycle            |
| P15 | relocation           | `1`       | `3`   | super  | jar     | inline    | inline      | —                                 | Consuming a dependency that carries `<relocation>` metadata                                       |
| P16 | snapshot-reactor     | `MM-flat` | `3+4` | local  | jar     | mixed     | inherit     | —                                 | Reactor SNAPSHOT deps resolved from current build; external SNAPSHOT + update policy              |
| P17 | war-webapp           | `MM-flat` | `3+4` | local  | war+jar | inherit   | pm          | prop                              | WAR lifecycle; `src/main/webapp`; profile-filtered resource filtering; WAR artifact output        |
| P18 | maven4-native        | `MM-flat` | `4`   | local  | jar+bom | inherit   | pm          | m4-cond                           | `<subprojects>`; `<packaging>bom</packaging>`; consumer POM split; Maven 4 `<conditions>`         |

---

## 3. Project Descriptions

### P01 — `superpom-minimal` · Absolute baseline

**Goal:** Establish the simplest possible JAR build with zero inheritance, zero profiles, and
zero configuration beyond what the Super POM provides by default.

**Unique dimension:** Super POM default lifecycle bindings; no explicit parent; system-scope
dependency.

**Setup:**

- Single module, `<packaging>jar</packaging>`
- All dependency versions inline in `<dependencies>`, no `<dependencyManagement>`
- Plugin versions pinned inline; no `<pluginManagement>`
- No profiles, no extensions, no `<parent>` element
- One `<dependency>` with `<scope>system</scope>` pointing to a local JAR

**What it verifies:**

- Super POM default lifecycle phase bindings execute the standard phases in the correct order
  (compile, test-compile, test, package)
- Plugin versions declared inline produce a deterministic effective-plugin configuration
- System-scoped dependency resolves from the declared local `<systemPath>`
- `<packaging>jar</packaging>` produces a `.jar` artifact in `target/`

---

### P02 — `local-parent-inherit` · Full inheritance from local reactor parent

**Goal:** Baseline for all multi-module tests. Verify every inheritance channel — dependency
versions, plugin configuration, properties, coordinates — flows from parent to child correctly.

**Unique dimension:** Local `relativePath` parent; inherited groupId/version; all management
channels simultaneously active.

**Setup:**

```
root/                    ← pom, lists <modules>
  ├── module-api/        ← jar, no internal deps
  ├── module-core/       ← jar, depends on module-api
  └── module-app/        ← jar, depends on module-core
```

- Root defines `<dependencyManagement>`, `<pluginManagement>`, `<properties>`
- Children declare no `<groupId>` or `<version>` (inherited from parent)
- Children declare no dependency versions (all managed by parent)

**What it verifies:**

- Children inherit `groupId` and `version` from the local reactor parent
- `<dependencyManagement>` in the parent controls all child dependency versions
- `<pluginManagement>` in the parent configures all child plugin invocations
- Properties declared in the parent are visible in all children's effective POM
- Build order is topologically determined: `module-api` builds before `module-core` which builds
  before `module-app`

---

### P03 — `remote-parent` · Parent resolved from repository (empty `<relativePath/>`)

**Goal:** Verify the repository-based parent resolution path when the parent is not a local file.

**Unique dimension:** `<relativePath/>` explicitly empty → parent fetched from local repository.

**Setup:**

- Single module, `<parent>` references a locally-installed artifact
- `<relativePath/>` is explicitly empty (prevents Maven from searching `../pom.xml`)
- Child inherits compiler settings and one dependency version from the remote parent

**What it verifies:**

- Maven resolves `<parent>` from the local repository when `<relativePath/>` is empty
- Child project inherits configuration (compiler settings, dependency versions) from the remote
  parent exactly as it would from a local one
- Build succeeds in offline mode (`--offline`) once the parent artifact is in the local repo
- Bumping `<parent><version>` requires the new parent to be available in the repository

---

### P04 — `ci-friendly` · CI-friendly version placeholders (`${revision}`)

**Goal:** Verify that the three CI-friendly property placeholders resolve correctly and that
`flatten-maven-plugin` produces normalized installed POMs.

**Unique dimension:** `${revision}`, `${sha1}`, `${changelist}`; `flatten-maven-plugin`.

**Setup:**

- Multi-module flat reactor; root and children use `<version>${revision}${sha1}${changelist}</version>`
- `flatten-maven-plugin` bound to `validate` phase; `.flattened-pom.xml` written per module
- `.mvn/maven.config`: `revision=1.0 sha1= changelist=-SNAPSHOT`
- CLI override: `-Drevision=2.0 -Dchangelist=` for release builds

**What it verifies:**

- `${revision}` resolves from `.mvn/maven.config`; CLI `-D` overrides `.mvn/maven.config`
- `flatten-maven-plugin` produces an installed POM with fully-resolved coordinates
  (no `${revision}` placeholder in the installed artifact)
- All modules use the same resolved version string in their artifacts and inter-module dependencies
- The flattened `.flattened-pom.xml` in `target/` is distinct from the original `pom.xml`

---

### P05 — `bom-single` · Single external BOM import, no parent

**Goal:** Verify that BOM-managed dependency versions resolve correctly when there is no
`<parent>` POM at all.

**Unique dimension:** `scope=import, type=pom`; version management without a parent.

**Setup:**

- Single module, no `<parent>` element
- `<dependencyManagement>` has one `scope=import` BOM (e.g. `junit-bom`)
- No explicit `<version>` on any dependency declared under `<dependencies>`

**What it verifies:**

- BOM `scope=import` pulls in dependency version management without requiring a parent
- Dependencies declared without `<version>` resolve to the BOM-managed version
- Build succeeds with Super POM only (no user-defined parent)
- Upgrading the BOM version changes the resolved dependency versions

---

### P06 — `dep-edge-cases` · Multiple BOMs, convergence, all dependency modifiers

**Goal:** Exercise every dependency management edge case in a single project: multi-BOM
mediation order, convergence enforcement, and all three dependency modifiers (system scope,
optional flag, classifier).

**Unique dimension:** Multi-BOM mediation; system scope; optional; classifier; layered
exclusions; convergence + enforcer.

**Setup:**

```xml

<dependencyManagement>
    <!-- BOM-A manages guava:31.0 — first, wins -->
    <dependency>…guava-bom…31.0-jre…import…</dependency>
    <!-- BOM-B manages guava:30.0 — second, loses -->
    <dependency>…legacy-bom…1.0…import…</dependency>
</dependencyManagement>

<dependencies>
<!-- Triggers convergence: transitive dep pulls guava:29.0 -->
<dependency>…lib-old…
    <exclusions>
        <exclusion>…guava…</exclusion>
    </exclusions>
</dependency>
<dependency>…optional-lib…
    <optional>true</optional>
</dependency>
<dependency>…core…
    <classifier>tests</classifier>
    <scope>test</scope>
</dependency>
<dependency>…ojdbc…
    <scope>system</scope>
    <systemPath>${project.basedir}/lib/ojdbc.jar</systemPath>
</dependency>
</dependencies>
```

`maven-enforcer-plugin` with `requireUpperBoundDeps` in `<pluginManagement>`.

**What it verifies:**

- First BOM listed in `<dependencyManagement>` wins when the same GAV appears in multiple BOMs
- Swapping BOM import order changes the winning version (mediation order is declaration-order)
- Adding/removing `<exclusion>` changes the resolved transitive dependency set
- `maven-enforcer-plugin` with `requireUpperBoundDeps` detects and fails on version convergence
  violations
- System-scoped dependency resolves from the declared local `<systemPath>`
- Optional dependency does not propagate to modules that depend on the declaring module
- Classifier-qualified dependency coordinate requires both `artifactId` and `classifier` to match

---

### P07 — `plugin-rebinding` · Custom packaging, lifecycle rebinding, plugin classpath

**Goal:** Verify that `maven-plugin` packaging, explicit phase rebinding, and plugin-level
classpath additions all behave as specified by the POM configuration.

**Unique dimension:** `maven-plugin` packaging; `<phase>` rebinding; `<plugin><dependencies>`.

**Setup:**

- `<packaging>maven-plugin</packaging>`; single Mojo class with `@Parameter`
- `maven-plugin-plugin:descriptor` rebound from default `generate-sources` to `process-sources`
- `maven-compiler-plugin` has `<dependencies>` adding an annotation-processor JAR
- `maven-plugin-plugin` configured with `goalPrefix`

**What it verifies:**

- `maven-plugin` packaging triggers the plugin lifecycle (descriptor generation, plugin testing)
  instead of the standard JAR lifecycle
- Plugin descriptor (`META-INF/maven/plugin.xml`) is generated and packaged in the output JAR
- Rebinding `plugin:descriptor` to `process-sources` causes it to execute earlier than the default
  phase order
- Annotation-processor JAR added via `<plugin><dependencies>` is available on the compiler
  classpath; changing it requires a re-compile
- `goalPrefix` in the plugin descriptor controls how users invoke the plugin's goals

---

### P08 — `profiles-all` · Every profile activation type + all property sources

**Goal:** Exercise every distinct profile activation mechanism and every property injection
source in a single module, verifying that each independently affects the effective POM.

**Unique dimension:** All six activation types combined; all property source types in one project.

**Setup:**

- Profile `by-property`: `<activation><property><name>env</name><value>ci</value>`
- Profile `by-os`: `<activation><os><family>linux</family>`
- Profile `by-file`: `<activation><file><exists>trigger.properties</exists>`
- Profile `by-jdk`: `<activation><jdk>11</jdk>`
- Profile `default-on` with `<activeByDefault>true</activeByDefault>` (resets when any other activates)
- A test-local `settings.xml` defines `<profile id="settings-profile">` with a property;
  `<activeProfiles>` activates it unconditionally
- POM property, profile property, `settings.xml` property, CLI `-Denv=ci`, and `${env.CI_BUILD_TAG:-local}`
  all present simultaneously

**What it verifies:**

- Property activation (`-Denv=ci`) activates the matching profile
- JDK activation fires when the running JDK version matches the declared range
- OS activation fires based on the OS family of the current build machine
- File activation fires when `trigger.properties` exists in the project directory
- `activeByDefault` profile deactivates when any other profile in the POM is activated
- Settings profiles contribute properties visible in the effective POM
- CLI `-D` system properties override all other property sources (POM, settings, profile)
- All six mechanisms active simultaneously produce one composite effective POM

---

### P09 — `repos-mirrors` · POM repos, `pluginRepositories`, settings mirrors, snapshot policy

**Goal:** Verify that custom repository configuration in POM and settings correctly affects
artifact resolution.

**Unique dimension:** POM `<repositories>`; `<pluginRepositories>`; `settings.xml` mirror;
snapshot `updatePolicy`.

**Setup:**

- Single module
- POM declares `<repositories>` pointing to a local file-system mock repository
- POM declares `<pluginRepositories>` pointing to a separate local mock repository
- A test `settings.xml` defines a `<mirror>` redirecting `central` to a local mock
- One dependency resolved from the mock repository (a locally installed test JAR)
- The snapshot repository entry has `<updatePolicy>always</updatePolicy>`

**What it verifies:**

- Maven resolves artifacts from `<repositories>` declared in the POM before falling back to
  Central
- `<pluginRepositories>` is checked separately from `<repositories>` for plugin artifact
  resolution
- A `<mirror>` in `settings.xml` intercepts requests matching its `<mirrorOf>` pattern and
  redirects them to the declared mirror URL
- `<updatePolicy>always</updatePolicy>` causes Maven to re-check the remote repository for
  SNAPSHOT artifacts on every invocation

---

### P10 — `reactor-partial` · Nested reactor with partial builds and resume

**Goal:** Verify that partial reactor builds on a deeply nested module graph produce correct
results for both the explicitly-requested and the automatically-included modules.

**Unique dimension:** Nested (hierarchical) multi-module; `-pl -am`; `-pl`; `-rf` resume;
chained external parent.

**Setup:**

```
corp-parent                  ← external artifact (empty <relativePath/>)
  └── project-parent/        ← reactor, pom
        ├── module-api/      ← jar
        ├── module-core/     ← jar, depends on module-api
        ├── module-service/  ← jar, depends on module-core
        └── module-app/      ← jar, depends on module-service
```

**Build sequence:**

1. `mvn verify` → all modules built
2. Modify `module-core` source
3. `mvn verify -pl :module-service -am` → `module-core` and `module-service` rebuild;
   `module-api` not rebuilt; `module-app` not in scope
4. `mvn verify -pl :module-app` → `module-service` already installed; rebuilds `module-app`
5. Simulate `module-service` failure → `mvn verify -rf :module-service` resumes correctly

**What it verifies:**

- `-am` automatically expands the build set to include all upstream dependencies of the
  specified module
- `-pl :module-app` restricts the build to exactly the named module without automatic expansion
- `-rf :module-service` resumes the build starting from the specified module, skipping modules
  that already completed
- Nested directory hierarchy resolves the same topological build order as a flat layout
- Chained external parent (corp-parent, empty `<relativePath/>`) provides configuration to all
  descendants

---

### P11 — `reactor-parallel` · `-T N` concurrent execution

**Goal:** Verify that multi-threaded builds respect module dependency ordering and produce
output identical to a sequential build.

**Unique dimension:** `-T N` parallel reactor; independent modules built concurrently.

**Setup:**

```
root/
  ├── util/       ← jar (no internal deps)
  ├── model/      ← jar (no internal deps)
  ├── service-a/  ← jar (depends on util)
  ├── service-b/  ← jar (depends on model)
  └── app/        ← jar (depends on service-a + service-b)
```

**Build sequence:**

1. `mvn verify -T 4` → all modules built; `util` and `model` in parallel, then their dependents
2. Modify `util` source only
3. `mvn verify -T 4` → `util` + `service-a` rebuild; `model` + `service-b` unchanged; `app` rebuilds
4. `mvn verify -T 1` → identical output to step 3

**What it verifies:**

- `-T 4` builds modules with no dependencies on each other in parallel on separate threads
- Modules that depend on other modules build only after all their dependencies complete
- `-T 4` and `-T 1` produce byte-for-byte identical output artifacts
- The build completes without race conditions or file-locking conflicts

---

### P12 — `core-ext-forked` · Core extension + `maven-invoker-plugin` forked process

**Goal:** Verify that a forked child Maven process started by `maven-invoker-plugin` has its
own independent session, repository, and extension state, and that the parent's invoker-plugin
execution is correctly configured.

**Unique dimension:** Core extension (`.mvn/extensions.xml`); forked child Maven process;
session independence between parent and child.

**Setup:**

- Parent project (multi-module flat) triggers `maven-invoker-plugin:run` in `integration-test` phase
- Child project in `src/it/child-build/` has its own `.mvn/extensions.xml` loading an extension
  as a **core extension**
- Child project has its own `settings.xml` pointing to an isolated local repository

**What it verifies:**

- `maven-invoker-plugin` forks a new Maven JVM process for each test project in `src/it/`
- The forked process has its own session, local repository, and loaded extension set
- Core extension loaded via `.mvn/extensions.xml` in the child process is independent of the
  parent session; changes to parent state do not affect child
- `maven-invoker-plugin` `<goals>` and `<properties>` control what the child process executes
- Changing the child project source triggers a re-execution of the invoker plugin's test

---

### P13 — `toolchains-jdk` · External JDK via `toolchains.xml`

**Goal:** Verify that selecting a non-default JDK via Maven toolchains compiles the project
with the declared JDK and that the toolchain selection is stable across builds.

**Unique dimension:** `toolchains.xml`; `maven-toolchains-plugin`; external JDK selection.

**Setup:**

- Single module
- `toolchains.xml` (provided via `-t` or `.mvn/toolchains.xml`) listing JDK 11 and JDK 17
  installations
- `maven-toolchains-plugin` bound to `validate` with `<jdkVersion>11</jdkVersion>`
- `maven-compiler-plugin` configured with `--release 11` flag through toolchain

**What it verifies:**

- `maven-toolchains-plugin` at `validate` selects the JDK entry matching `<jdkVersion>11`
- `maven-compiler-plugin` uses the toolchain-provided JDK, not the JVM running Maven
- `--release 11` flag targets the declared bytecode compatibility level
- Switching the `<jdkVersion>` declaration to `17` selects the JDK 17 toolchain entry

---

### P14 — `distrib-deploy` · `distributionManagement` and deploy lifecycle

**Goal:** Verify that `<distributionManagement>` configures the deploy target repositories
correctly and that `maven-deploy-plugin` selects snapshot vs release repo based on the
project version.

**Unique dimension:** `<distributionManagement>`; snapshot vs release repo selection; deploy
lifecycle.

**Setup:**

- Single module
- `<distributionManagement>` defines both a snapshot repository and a release repository with
  explicit policies
- `maven-deploy-plugin` configured with `altSnapshotDeploymentRepository` and
  `altReleaseDeploymentRepository`
- Build sequence: `mvn verify` → `mvn deploy`

**What it verifies:**

- `<distributionManagement>` configures the target repositories for the `deploy` phase
- `maven-deploy-plugin` selects the snapshot repository when the version ends in `-SNAPSHOT`
- `maven-deploy-plugin` selects the release repository for non-SNAPSHOT versions
- The `deploy` phase executes after all prior standard lifecycle phases complete
- `<snapshotPolicy>` and `<releasePolicy>` in each repository descriptor are respected

---

### P15 — `relocation` · Artifact relocation (`<relocation>` metadata)

**Goal:** Verify that consuming a dependency whose POM contains `<relocation>` metadata
resolves transparently to the new artifact coordinates.

**Unique dimension:** `<relocation>` in a dependency's published POM; transitive resolution
through relocation chain.

**Setup:**

- Single module declares a dependency on `test.local:old-artifact:1.0`
- `old-artifact/pom.xml` (locally installed) contains:
  ```xml
  <distributionManagement>
    <relocation>
      <groupId>test.local</groupId>
      <artifactId>new-artifact</artifactId>
      <version>2.0</version>
    </relocation>
  </distributionManagement>
  ```
- `new-artifact:2.0` is the actual JAR installed in the local repository

**What it verifies:**

- Maven logs a deprecation warning when it resolves a dependency with `<relocation>` metadata
- Resolution transparently continues to `new-artifact:2.0` without a build failure
- Declaring `new-artifact:2.0` directly as a dependency resolves to the same JAR
- Upgrading the relocation target version (`new-artifact:2.1`) changes the resolved classpath

---

### P16 — `snapshot-reactor` · Reactor SNAPSHOT deps + external SNAPSHOT + resolution policies

**Goal:** Verify that SNAPSHOT instability is handled correctly: reactor SNAPSHOTs resolve
from the current build; external SNAPSHOTs re-resolve on policy trigger.

**Unique dimension:** Reactor SNAPSHOT dependency; external SNAPSHOT from remote repo;
`updatePolicy` interaction.

**Setup:**

```
root/
  ├── module-api/      ← 1.0-SNAPSHOT
  ├── module-core/     ← 1.0-SNAPSHOT, depends on module-api:1.0-SNAPSHOT
  └── module-app/      ← 1.0-SNAPSHOT, depends on external-lib:2.0-SNAPSHOT (from mock repo)
```

- External mock repository serves `external-lib:2.0-SNAPSHOT` with different content on each call

**What it verifies:**

- Reactor SNAPSHOT (`module-api:1.0-SNAPSHOT`) resolves from the current reactor build, not
  from a potentially stale local repository entry
- `module-core` can declare a dependency on `module-api:1.0-SNAPSHOT` and receive the version
  built in the same reactor invocation
- External SNAPSHOT with `<updatePolicy>always</updatePolicy>` is re-checked from the remote
  repository on every Maven invocation
- Receiving new SNAPSHOT content from the remote repository changes the resolved classpath

---

### P17 — `war-webapp` · WAR packaging with profile-filtered resources

**Goal:** Verify that the WAR lifecycle correctly bundles web resources and that profile-based
resource filtering produces the appropriate output per activated profile.

**Unique dimension:** `<packaging>war</packaging>`; WAR-specific lifecycle phases;
`src/main/webapp` content; profile-filtered resource filtering.

**Setup:**

```
root/
  ├── webapp-war/     ← war packaging
  └── webapp-lib/     ← jar (shared logic)
```

- Profile `dev` (activated by `-P dev`): sets resource filter `dev.properties`
- Profile `prod` (activated by `-P prod`): different filter file + `maven-war-plugin`
  `<webXml>` override
- Profile `default-config` with `<activeByDefault>true</activeByDefault>` (resets when
  `-P dev` or `-P prod` is active)

**What it verifies:**

- `<packaging>war</packaging>` bundles `src/main/webapp` content (HTML, JSP, `WEB-INF/`)
  into the `.war` archive
- Resource filtering applies the values from the activated profile's `.properties` file to
  `src/main/resources` placeholders
- `maven-war-plugin` `<webXml>` override replaces the default `WEB-INF/web.xml` with
  a profile-specific one when `-P prod` is active
- `activeByDefault` profile deactivates when `-P dev` or `-P prod` is explicitly specified
- Changing `src/main/webapp/index.html` triggers a WAR rebuild; `webapp-lib` is unaffected

---

### P18 — `maven4-native` · Maven 4 native features

**Goal:** Verify the Maven 4 project model features that differ from Maven 3: `<subprojects>`
aggregation, `<packaging>bom</packaging>`, consumer POM split, and Maven 4 `<conditions>`
profile activation.

**Unique dimension:** `<subprojects>`; `<packaging>bom</packaging>`; consumer POM (stripped
artifact-only form); Maven 4 `<conditions>`.

**Setup:**

```
root/pom.xml             ← Maven 4: <subprojects> instead of <modules>
  ├── platform/pom.xml   ← <packaging>bom</packaging>
  └── app/pom.xml        ← jar, imports platform BOM
```

- Profile `m4-enhanced` activated by Maven 4 `<conditions>` syntax
- `.mvn/maven.config`: `--strict-checksums`
- After `install`, both a build POM (full plugin config) and a consumer POM (stripped) are
  written to the local repository

**What it verifies:**

- `<subprojects>` in Maven 4 declares child modules and determines the build order
- `<packaging>bom</packaging>` produces a BOM POM artifact with no compiled sources
- Maven 4 writes a **consumer POM** (stripped of build-specific configuration) to the local
  repository alongside the build POM
- Maven 4 `<conditions>` profile activates when running under Maven 4; does not activate under
  Maven 3
- `app` resolves dependency versions from the locally-installed BOM produced by `platform`

---

## 4. Coverage Summary

### 4.1 Maven behavior coverage

| Group            | Required Behavior        | ✔/✗                        | Config(s)                  |
|------------------|--------------------------|----------------------------|----------------------------|
| **Inheritance**  | Super POM                | ✔                          | P01, P05, P07–P09, P13–P15 |
|                  |                          | Local parent               | ✔                          | P02, P04, P10–P12, P16–P18     |
|                  |                          | Remote parent              | ✔                          | **P03**                        |
|                  |                          | Inherited coordinates      | ✔                          | P02, P04, P10                  |
| **Dep Mgmt**     | None (inline)            | ✔                          | P01, P05, P07–P09          |
|                  |                          | Parent-defined             | ✔                          | P02, P03, P04, P10–P12, P16–P18 |
|                  |                          | BOM import single          | ✔                          | **P05**                        |
|                  |                          | Multiple BOMs + mediation  | ✔                          | **P06**                        |
|                  |                          | SNAPSHOT inside reactor    | ✔                          | **P16**                        |
|                  |                          | System scope               | ✔                          | **P06**                        |
|                  |                          | Optional dependency        | ✔                          | **P06**                        |
|                  |                          | Classifier                 | ✔                          | **P06**                        |
|                  |                          | Layered exclusions         | ✔                          | **P06**                        |
|                  |                          | Convergence conflict       | ✔                          | **P06**                        |
| **Plugin**       | Default lifecycle        | ✔                          | P01                        |
|                  |                          | `<pluginManagement>`       | ✔                          | P02, P04, P06–P12, P17–P18     |
|                  |                          | Child override             | ✔                          | P02, P04                       |
|                  |                          | Lifecycle rebinding        | ✔                          | **P07**                        |
|                  |                          | Plugin classpath deps      | ✔                          | **P07**                        |
|                  |                          | Enforcer rules             | ✔                          | **P06**                        |
| **Profiles**     | Property activation      | ✔                          | **P08**, P17               |
|                  |                          | JDK activation             | ✔                          | **P08**                        |
|                  |                          | OS activation              | ✔                          | **P08**                        |
|                  |                          | File activation            | ✔                          | **P08**                        |
|                  |                          | `activeByDefault` reset    | ✔                          | **P08**, P17                   |
|                  |                          | `settings.xml` profiles    | ✔                          | **P08**                        |
|                  |                          | CLI `-P`                   | ✔                          | P08, P17                       |
|                  |                          | Merge & precedence         | ✔                          | **P08**                        |
| **Repositories** | Default Central          | ✔                          | All (implicit)             |
|                  |                          | POM `<repositories>`       | ✔                          | **P09**                        |
|                  |                          | `<pluginRepositories>`     | ✔                          | **P09**                        |
|                  |                          | `settings.xml` mirror      | ✔                          | **P09**                        |
|                  |                          | Snapshot update policy     | ✔                          | **P09**, P16                   |
| **Reactor**      | Full build               | ✔                          | P02, P04, P10–P12, P16–P18 |
|                  |                          | Partial (`-pl -am`)        | ✔                          | **P10**                        |
|                  |                          | Partial (`-pl` only)       | ✔                          | **P10**                        |
|                  |                          | Resume (`-rf`)             | ✔                          | **P10**                        |
|                  |                          | Parallel (`-T`)            | ✔                          | **P11**                        |
|                  |                          | Build order                | ✔                          | P02, P10, P11                  |
| **Extensions**   | Build extension          | ✔                          | P07, **P12**               |
|                  |                          | Core extension             | ✔                          | **P12**                        |
|                  |                          | Custom packaging           | ✔                          | **P07** (maven-plugin)         |
|                  |                          | Lifecycle participant      | ✔                          | **P07**, P12                   |
| **Properties**   | POM                      | ✔                          | P01–P18                    |
|                  |                          | Parent-inherited           | ✔                          | P02, P04, P10                  |
|                  |                          | Profile                    | ✔                          | P08, P17                       |
|                  |                          | CLI `-D`                   | ✔                          | P04, **P08**                   |
|                  |                          | Environment variables      | ✔                          | **P08**                        |
|                  |                          | CI-friendly placeholders   | ✔                          | **P04**                        |
| **Toolchains**   | External JDK             | ✔                          | **P13**                    |
| **Distribution** | `distributionManagement` | ✔                          | **P14**                    |
|                  |                          | Snapshot vs release deploy | ✔                          | **P14**                        |
|                  |                          | Deploy lifecycle           | ✔                          | **P14**                        |
|                  |                          | Artifact relocation        | ✔                          | **P15**                        |

**49 of 49 required Maven behaviors covered.**

### 4.2 Out of scope for this set

The following Maven behaviors are intentionally excluded from the current 18-project set.
Add a new project (P20+) if any becomes relevant to the extension.

| Future ID | Scenario                            |
|-----------|-------------------------------------|
| P20       | EAR packaging (legacy J2EE pattern) |
| P21       | Aggregator ≠ parent edge case       |
| P22       | Multi-release JAR (MRJar)           |
| P23       | Polyglot Maven (YAML/Groovy POM)    |
