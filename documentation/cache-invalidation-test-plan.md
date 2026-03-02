# Cache Invalidation Test Plan

> **Priority:** Correct cache invalidation after a Maven inputs change is the **HIGHEST CRITICALITY**
> feature. The cache MUST be invalidated whenever any underlying project input changes.
>
> **Scope:** Every unique project trait from `maven-test-projects-universe-v2.md` that contributes
> to the cache key. For each trait, a change must produce a cache miss on the next build.
>
> **Reference projects:** `src/test/projects/reference-test-projects/` (P01–P19).
> Tests that require a Maven home (multi-module, parallel, etc.) need a `@BeforeAll` that
> sets `System.setProperty("maven.home", …)` by scanning `target/maven3` or `target/maven4`.

---

## 1. Identified Inputs

The cache key is derived from a hash of all **Maven project inputs** for a given module.
The following input categories feed into that hash, grouped by the unique project traits
in the v2 spec.

### 1.1 Source & File Inputs

| Input | How fingerprinted | Trait source |
|-------|------------------|--------------|
| `src/main/java/**` content | File hash of every `.java` file | P01 (baseline) |
| `src/test/java/**` content | File hash of every test `.java` file | P01 (baseline) |
| `src/main/resources/**` content | File hash of every resource file | P01 (baseline) |
| `src/main/webapp/**` content | File hash of webapp assets (WAR packaging) | P17 |
| System-scope JAR file (`<systemPath>`) | Hash of the JAR bytes at the declared path | P01, P06 |

### 1.2 Project Descriptor (POM) Inputs

| Input | How fingerprinted | Trait source |
|-------|------------------|--------------|
| Effective POM content (semantics) | Canonical effective-POM hash (whitespace-normalised) | P01–P18 |
| Inline dependency coordinates + versions | Part of effective POM | P01, P07–P09 |
| Parent-managed dependency versions | Propagated into child effective POM | P02, P10 |
| BOM-managed dependency versions (single BOM) | Resolved version in effective POM | P05 |
| BOM-managed versions (multi-BOM mediation order) | First-wins resolution in effective POM | P06 |
| Exclusion declarations | Change resolved transitive classpath (effective POM) | P06 |
| Optional flag on a dependency | Changes dep propagation visible in effective POM | P06 |
| Classifier qualifier on a dependency | Changes resolved artifact coordinate | P06 |
| Plugin version (inline or managed) | Part of effective POM plugin config | P01, P02 |
| Plugin configuration parameters | Tracked params contribute to key | P07 |
| Plugin phase rebinding (`<phase>`) | Changes effective lifecycle map | P07 |
| Plugin classpath `<dependencies>` | Changes what's on compiler/plugin classpath | P07 |

### 1.3 Inheritance Chain Inputs

| Input | How fingerprinted | Trait source |
|-------|------------------|--------------|
| Local parent POM (managed deps/plugins/props) | Resolved into child effective POM | P02 |
| Remote parent POM version (empty `<relativePath/>`) | Fetched parent merged into effective POM | P03 |
| External parent (chained `<relativePath/>` empty) | Corp-parent config flows into all descendants | P10 |

### 1.4 Profile & Property Sources

| Input | How fingerprinted | Trait source |
|-------|------------------|--------------|
| POM `<properties>` values | Resolved into effective POM | P01–P18 |
| Profile-injected properties (when profile is active) | Effective POM includes active-profile contributions | P08 |
| Profile activation: property trigger (`-Denv=ci`) | Different active profiles → different effective POM | P08 |
| Profile activation: file-existence trigger | Presence/absence of `trigger.properties` → different effective POM | P08 |
| Profile activation: JDK range | Active on matching JDK → different effective POM | P08 |
| Profile activation: OS family | Active on matching OS → different effective POM | P08 |
| `activeByDefault` reset | Resets when any other profile activates | P08 |
| `settings.xml` profile properties | Contributes to effective POM via active settings profile | P08, P09 |
| CLI `-D` system properties | Override all property sources; change effective POM | P04, P08 |
| CI-friendly `${revision}`, `${sha1}`, `${changelist}` | Part of project version and POM properties | P04 |
| Environment variables `${env.*}` | Referenced in POM properties | P08 |

### 1.5 Multi-Module Reactor Inputs

| Input | How fingerprinted | Trait source |
|-------|------------------|--------------|
| Upstream module output (inter-module dependency) | Downstream receives new artifact → different input hash | P02, P10, P11 |
| Reactor SNAPSHOT module source change | Same as upstream module output; no remote resolution | P16 |
| External SNAPSHOT dependency content | Remotely resolved artifact; `updatePolicy=always` | P16 |

### 1.6 Packaging & Lifecycle

| Input | How fingerprinted | Trait source |
|-------|------------------|--------------|
| `<packaging>jar</packaging>` | Standard JAR lifecycle default bindings | P01 |
| `<packaging>war</packaging>` | WAR lifecycle bindings; webapp dir included | P17 |
| `<packaging>maven-plugin</packaging>` | Custom lifecycle; descriptor + JAR | P07 |
| `<packaging>pom</packaging>` BOM aggregator | No compiled sources; POM artifact | P02, P05, P10 |

---

## 2. Test Cases

**Legend:**
- ✅ **Covered** — existing test class fully verifies this case
- ⚠️ **Partial** — test exists but does not cover this specific mutation
- ❌ **Not covered** — no test exists; new test required

### 2.1 Source File Inputs

| Case Name | Project to Use | Status | Description |
|-----------|---------------|--------|-------------|
| `SourceChangeInvalidatesCache` | `checksum-correctness` | ✅ | Build 1 cold cache. Append comment to `Hello.java`. Build 2 must be cache miss. **Ref:** `checksumcorrectness.SourceChangeInvalidatesCacheTest` |
| `AddedSourceFileInvalidatesCache` | `checksum-correctness` | ✅ | Build 1 cold. Create new `World2.java` in `src/main/java/...`. Build 2 must miss. **Ref:** `checksumcorrectness.AddedSourceFileInvalidatesCacheTest` |
| `DeletedSourceFileInvalidatesCache` | `checksum-correctness` | ✅ | Build 1 cold. Delete `World.java`. Build 2 must miss. **Ref:** `checksumcorrectness.DeletedSourceFileInvalidatesCacheTest` |
| `TestSourceChangeInvalidatesCache` | `checksum-correctness` | ✅ | Build 1 cold. Append comment to `HelloTest.java`. Build 2 must miss. **Ref:** `checksumcorrectness.TestSourceChangeInvalidatesCacheTest` |
| `ResourceChangeInvalidatesCache` | `checksum-correctness` | ✅ | Build 1 cold. Modify `src/main/resources/config.properties`. Build 2 must miss. **Ref:** `checksumcorrectness.ResourceChangeInvalidatesCacheTest` |
| `WebappFileChangeInvalidatesCache` | `p17-war-webapp` | ⚠️ | Build 1 cold (WAR+JAR modules saved). Modify `webapp-war/src/main/webapp/index.html`. Build 2: `webapp-war` module must miss; `webapp-lib` (unchanged) may hit. `WarPackagingTest` covers round-trip but not this mutation. New `@Test` in `projecttypes.WarPackagingTest` or new class `projecttypes.WebappFileChangeInvalidatesCacheTest`. Use `ReferenceProjectBootstrap.prepareProject(p17)`. |
| `SystemScopeJarChangeInvalidatesCache` | `p01-superpom-minimal` | ❌ | Build 1 cold (system-scope `lib/placeholder.jar` present). Replace `lib/placeholder.jar` with a different valid JAR (e.g. a new empty ZIP with a different byte). Build 2 must miss. Implementation: write a new non-empty ZIP to replace `placeholder.jar` using `Files.write`. Use `@IntegrationTest("src/test/projects/reference-test-projects/p01-superpom-minimal")`. |

### 2.2 POM & Descriptor Changes

| Case Name | Project to Use | Status | Description |
|-----------|---------------|--------|-------------|
| `PomChangeInvalidatesCache` | `checksum-correctness` | ✅ | Build 1 cold. Change meaningful POM content (description or dep). Build 2 must miss. **Ref:** `checksumcorrectness.PomChangeInvalidatesCacheTest` |
| `WhitespaceOnlyPomChangeNoMiss` | `checksum-correctness` | ✅ | Build 1 cold. Add blank lines to POM. Build 2 must HIT (whitespace-normalised). **Ref:** `checksumcorrectness.WhitespaceOnlyPomChangeNoCacheMissTest` |
| `InlineDependencyVersionChangeInvalidates` | `p01-superpom-minimal` | ✅ | Build 1 cold (`junit:4.13.2`). Replace `4.13.2` → `4.12` in POM. Build 2 must miss. **Ref:** `checksumcorrectness.DependencyVersionChangeInvalidatesCacheTest` |
| `ParentManagedDepVersionChangeInvalidates` | `p02-local-parent-inherit` | ❌ | Build 1 cold (all modules saved). Change `junit` managed version in root `pom.xml` `<dependencyManagement>` from `4.13.2` → `4.12`. Build 2: all child modules that use junit must miss because their effective POM changed. Assert CACHE_MISS in log-2. This tests that parent-managed version changes propagate to child fingerprints. New class `checksumcorrectness.ParentManagedDepVersionChangeInvalidatesTest`. Use `ReferenceProjectBootstrap.prepareProject(p02)`. |
| `InheritedParentPropertyChangeInvalidates` | `p02-local-parent-inherit` | ❌ | Build 1 cold. Change `maven.compiler.source` property in root parent POM from `1.8` → `11`. Build 2: all children must miss because their effective POM changed (compiler source property is part of effective config). New class `checksumcorrectness.InheritedPropertyChangeInvalidatesTest`. |
| `RemoteParentVersionBumpInvalidates` | `p03-remote-parent` | ❌ | Requires a 2-step setup: (1) install `p03-remote-parent` as a standalone artifact at `1.0`, then install a modified version at `1.1` with a different managed dep version. Build 1 with parent `1.0` → cold cache. Bump `<parent><version>` in pom.xml to `1.1`. Build 2 must miss because the fetched remote parent is different. New class `checksumcorrectness.RemoteParentVersionBumpInvalidatesTest`. Use `ReferenceProjectBootstrap.prepareProject(p03)` preceded by installing the two parent versions via separate `Verifier` calls on the `_parent/` subdirectory. |
| `PluginVersionChangeInvalidates` | `p01-superpom-minimal` | ❌ | Build 1 cold. Change `maven-compiler-plugin` version in pom.xml from `3.13.0` → `3.12.1`. Build 2 must miss (plugin version is part of effective POM fingerprint). New class `checksumcorrectness.PluginVersionChangeInvalidatesTest`. |

### 2.3 Dependency Management Edge Cases (P06)

| Case Name | Project to Use | Status | Description |
|-----------|---------------|--------|-------------|
| `BomVersionChangeAffectedDepInvalidates` | `p05-bom-single` | ❌ | Build 1 cold (BOM `junit-bom:5.10.0`; project declares `junit-jupiter-api` without a version → resolves to `5.10.0`). Change BOM version in pom.xml from `5.10.0` → `5.9.3` — this changes the **resolved version of a dep the project actually declares**. Build 2 must miss because the effective POM dependency version changed. New class `checksumcorrectness.BomVersionChangeAffectedDepInvalidatesTest`. Use `ReferenceProjectBootstrap.prepareProject(p05)`. Assert CACHE_MISS in log-2. |
| `BomVersionChangeUnusedDepNoMiss` | `p06-dep-edge-cases` | ❌ | **Negative test.** Validates that a BOM upgrade that touches only managed entries the project never declares does NOT invalidate the cache. Setup: P06 imports `guava-bom`. `module-main` declares guava (version from BOM). Also import a second BOM, say `commons-bom`, that manages `commons-io` — but `module-main` does NOT declare `commons-io`. Upgrade `commons-bom` from version X to Y (only `commons-io` version changes; guava version unaffected). Build 2 must HIT because `module-main`'s effective POM is unchanged. New class `checksumcorrectness.BomVersionChangeUnusedDepNoMissTest`. Note: requires adding a second BOM import (`commons-bom`) to P06 root pom.xml in the test copy. |
| `BomImportOrderChangeInvalidates` | `p06-dep-edge-cases` | ❌ | Build 1 cold (BOM-A `guava-bom:31.1-jre` listed first, wins; `module-main` declares guava → resolves to `31.1-jre`). Swap BOM-A and BOM-B import order in root `pom.xml` so BOM-B (`30.1.1-jre`) is now listed first → guava resolves to `30.1.1-jre` in the effective POM. Build 2: `module-main` must miss because the **resolved version of guava (a dep it declares) changed**. Assert CACHE_MISS for `module-main`. New class `checksumcorrectness.BomImportOrderChangeInvalidatesTest`. |
| `ExclusionAddedChangesClasspathInvalidates` | `p06-dep-edge-cases` | ❌ | Build 1 cold. Remove the `<exclusion>` of `error_prone_annotations` from the guava dep in `module-main/pom.xml` (so the transitive dep is now present). Build 2 must miss because the effective classpath changed. New class `checksumcorrectness.ExclusionRemovedInvalidatesTest`. Inverse also tested: add exclusion → miss. |
| `OptionalFlagChangeInvalidates` | `p06-dep-edge-cases` | ❌ | Build 1 cold. Remove `<optional>true</optional>` from guava dep in `module-main/pom.xml`. Build 2 must miss (optional flag is part of effective POM dependency descriptor). New class `checksumcorrectness.OptionalFlagChangeInvalidatesTest`. |
| `ClassifierDepAddedInvalidates` | `p06-dep-edge-cases` | ❌ | Build 1 cold. Remove the `classifier=tests` dependency on `module-support` from `module-main/pom.xml`. Build 2 must miss. New class `checksumcorrectness.ClassifierDepChangeInvalidatesTest`. |

### 2.4 Plugin Configuration (P07)

| Case Name | Project to Use | Status | Description |
|-----------|---------------|--------|-------------|
| `TrackedPluginParamChangeMiss` | `tracked-properties` | ✅ | Build 1 cold. Change a tracked plugin parameter value. Build 2 must miss. **Ref:** `pluginexecution.TrackedPropertyMismatchCacheMissTest` |
| `PluginSkipValueAllowsReuse` | `tracked-properties-skip-value` | ✅ | Build 1 with skip=false (cache saved). Build 2 with skip=true still hits cache. **Ref:** `pluginexecution.TrackedPropertySkipValueAllowsReuseTest` |
| `PhaseRebindingChangeInvalidates` | `p07-plugin-rebinding` | ❌ | Build 1 cold (`maven-plugin-plugin:descriptor` bound to `process-sources`). Change the `<phase>` binding from `process-sources` back to `generate-sources` (the default) in pom.xml. Build 2 must miss because the effective plugin lifecycle map changed. New class `pluginexecution.PhaseRebindingChangeInvalidatesTest`. Use `ReferenceProjectBootstrap.prepareProject(p07)`. |
| `PluginClasspathDepChangeInvalidates` | `p07-plugin-rebinding` | ❌ | Build 1 cold (compiler plugin has `maven-plugin-annotations:3.13.1` in `<plugin><dependencies>`). Change the version to `3.12.0`. Build 2 must miss because the compiler tool classpath changed (part of effective plugin config). New class `pluginexecution.PluginClasspathDepChangeInvalidatesTest`. |
| `PluginGoalPrefixChangeInvalidates` | `p07-plugin-rebinding` | ❌ | Build 1 cold. Change `<goalPrefix>p07example</goalPrefix>` to `<goalPrefix>p07changed</goalPrefix>` in pom.xml. Build 2 must miss (plugin descriptor config changed). New class `pluginexecution.PluginGoalPrefixChangeInvalidatesTest`. |

### 2.5 Profile Activation & Property Sources (P08)

| Case Name | Project to Use | Status | Description |
|-----------|---------------|--------|-------------|
| `ProfileCliPropertyActivationInvalidates` | `p08-profiles-all` | ✅ | Build 1 cold (no profile active). Build 2 with `-Denv=ci` activates `by-property` profile → effective POM adds `profile.by-property=active` property → cache miss. **Ref:** `inputfiltering.ProfileCliActivationInvalidatesTest` |
| `ProfileFileActivationInvalidates` | `p08-profiles-all` | ❌ | Build 1 cold (no `trigger.properties` → `by-file` profile inactive). Create `trigger.properties` file in project root. Build 2: `by-file` profile activates → `profile.by-file=active` added to effective POM → must miss. Teardown: delete `trigger.properties` after test. New class `inputfiltering.ProfileFileActivationInvalidatesTest`. Use `ReferenceProjectBootstrap.prepareProject(p08)`. |
| `SettingsProfilePropertyChangeInvalidates` | `p08-profiles-all` | ❌ | Build 1 cold with `test-settings.xml` (has `settings-profile` contributing `settings.prop=settings-value`). Modify `test-settings.xml` to change `settings.prop` to `settings-value-v2`. Build 2 must miss. The effective POM contains the settings-profile property. New class `inputfiltering.SettingsProfilePropertyChangeInvalidatesTest`. Pass `-s test-settings.xml` via `verifier.addCliOption("-s", "test-settings.xml")`. |
| `EnvVariableChangeInvalidates` | `p08-profiles-all` | ❌ | This is a limitation: the JVM process cannot arbitrarily change environment variables between test builds. Instead, verify indirectly: if a POM property references `${env.USER}` (or similar env var available in CI), run build 1 with env var set to value A, then confirm build 2 with the same value hits cache. Documenting that full env-var mutation is not directly testable within the verifier model. **Action:** Add a note/`@Disabled` test with a comment explaining the limitation. Alternatively test that the effective POM includes the expanded value of `${env.CI_BUILD_TAG:-local}` by verifying the log with `logAllProperties`. |
| `ActiveByDefaultResetOnProfileActivation` | `p08-profiles-all` | ❌ | Build 1 cold (no explicit profiles → `default-on` is active, contributes `profile.default=active` and `build.env=default`). Build 2 with `-Denv=ci` activates `by-property` → `default-on` resets (no longer active) → effective POM changes → must miss. Validates that `activeByDefault` semantics are correctly captured in fingerprint. New class `inputfiltering.ActiveByDefaultResetInvalidatesTest`. |

### 2.6 CI-Friendly Versioning (P04)

| Case Name | Project to Use | Status | Description |
|-----------|---------------|--------|-------------|
| `CIFriendlyRevisionChangeMiss` | `p04-ci-friendly` | ✅ | Build 1 `-Drevision=1.0` → saved. Build 2 `-Drevision=1.0` → HIT. Build 3 `-Drevision=2.0` → must miss. **Ref:** `versioning.CIFriendlyRevisionVersionTest` |
| `CIFriendlyChangelistChangeMiss` | `p04-ci-friendly` | ❌ | Build 1 with `-Drevision=1.0 -Dchangelist=-SNAPSHOT` (cold). Build 2 same params → HIT. Build 3 with `-Drevision=1.0 -Dchangelist=` (empty changelist = release version) → must miss because the resolved `<version>` in the effective POM changes from `1.0-SNAPSHOT` to `1.0`. New class `versioning.CIFriendlyChangelistChangeInvalidatesTest`. Pattern: create separate `Verifier` for build 3 with updated CLI options, as in `CIFriendlyRevisionVersionTest`. |

### 2.7 Multi-Module Reactor (P02, P10, P11, P16)

| Case Name | Project to Use | Status | Description |
|-----------|---------------|--------|-------------|
| `UpstreamModuleChangeInvalidatesDownstream` | `p02-local-parent-inherit` | ✅ | Build 1 all modules saved. Modify `module-api/src/main/java/.../Api.java`. Build 2: `module-api`, `module-core`, `module-app` must miss. **Ref:** `multimodule.UpstreamModuleChangeDownstreamMissTest` |
| `UnchangedModuleStaysInCache` | `p02-local-parent-inherit` | ❌ | Complement of the above. Build 1 all modules saved. Modify only `module-app/src/main/java/.../App.java`. Build 2: `module-api` and `module-core` must HIT; `module-app` must miss. Validates precision: unchanged upstream modules are not evicted when only a leaf changes. New class `multimodule.UnchangedModuleStaysInCacheTest`. Assert `CACHE_HIT` appears in log for both `module-api` and `module-core`. |
| `PartialBuildPlSucceeds` | `p10-reactor-partial` | ✅ | Build 1 full reactor saved. Build 2 `-pl module-app` succeeds without errors. **Ref:** `multimodule.MultiModulePartialBuildTest` |
| `PartialBuildPlAmRestoresUpstream` | `p10-reactor-partial` | ✅ | Build 1 full reactor saved. Build 2 `-pl module-app -am` restores upstream modules from cache, then builds module-app. **Ref:** `multimodule.MultiModulePartialWithAmTest` |
| `ParallelBuildCacheRoundTrip` | `p11-reactor-parallel` | ✅ | Build 1 `-T 2` all modules saved. Build 2 `-T 2` all HIT. **Ref:** `multimodule.ParallelBuildTest` |
| `ParallelBuildModuleChangeInvalidates` | `p11-reactor-parallel` | ❌ | Build 1 `-T 2` all modules saved. Modify `util/src/main/java/.../Util.java`. Build 2 `-T 2`: `util` and `service-a` (which depends on util) and `app` must miss; `model` and `service-b` must HIT. Validates that parallel execution correctly isolates per-module cache decisions. New class `multimodule.ParallelBuildModuleChangeInvalidatesTest`. |
| `SnapshotVersionBumpCacheHit` | `p16-snapshot-reactor` | ✅ | Bump all POMs from `1.0-SNAPSHOT` to `1.1-SNAPSHOT` between builds. Build 2 must HIT (SNAPSHOT qualifier normalised in cache key). **Ref:** `versioning.SnapshotVersionBumpCacheHitTest` |
| `ReactorSnapshotSourceChangeInvalidates` | `p16-snapshot-reactor` | ❌ | Build 1 all 3 modules saved. Modify `module-api/src/main/java/.../Api.java`. Build 2: `module-api` and `module-core` (which depends on `module-api:1.0-SNAPSHOT`) and `module-app` must all miss. Validates that reactor SNAPSHOT resolution correctly propagates changes through the dependency chain. New class `multimodule.ReactorSnapshotSourceChangeInvalidatesTest`. Use `ReferenceProjectBootstrap.prepareProject(p16)`. |

### 2.8 Packaging Type & Lifecycle (P07, P17)

| Case Name | Project to Use | Status | Description |
|-----------|---------------|--------|-------------|
| `WarPackagingCacheRoundTrip` | `p17-war-webapp` | ✅ | Build 1 WAR+JAR modules saved. Build 2 WAR+JAR modules HIT. **Ref:** `projecttypes.WarPackagingTest` |
| `WarWebappFileChangeInvalidates` | `p17-war-webapp` | ❌ | Build 1 cold (both modules saved). Modify `webapp-war/src/main/webapp/index.html` content. Build 2: `webapp-war` must miss; `webapp-lib` must HIT (its source unchanged). This verifies that `src/main/webapp` is part of the WAR module's fingerprint. New class `projecttypes.WarWebappFileChangeInvalidatesTest`. |
| `WarProfileFilterChangeInvalidates` | `p17-war-webapp` | ❌ | Build 1 cold (default-config profile active). Build 2 with `-P dev` activating the dev profile → resource filtering applies different filter file → effective POM and resource outputs differ → WAR module must miss. New class `projecttypes.WarProfileFilterChangeInvalidatesTest`. |
| `MavenPluginPackagingCacheRoundTrip` | `p07-plugin-rebinding` | ❌ | Build 1 cold. Build 2 same inputs → both must produce cache HIT. Validates that `maven-plugin` packaging (custom lifecycle: descriptor generation, plugin testing) is cached correctly. New class `projecttypes.MavenPluginPackagingCacheRoundTripTest`. Use `ReferenceProjectBootstrap.prepareProject(p07)`. |

### 2.9 Extension Loading (P01, P12)

| Case Name | Project to Use | Status | Description |
|-----------|---------------|--------|-------------|
| `BuildExtensionCacheRoundTrip` | `build-extension` | ✅ | Basic build-extension loading; second build hits cache. **Ref:** `BuildExtensionTest` |
| `CoreExtensionCacheRoundTrip` | All P01–P19 | ✅ | Core extension loading via `.mvn/extensions.xml`; second build hits cache. **Ref:** `CoreExtensionTest` (dynamic test factory over all reference projects) |

### 2.10 Inheritance Chain (P02, P03, P10)

| Case Name | Project to Use | Status | Description |
|-----------|---------------|--------|-------------|
| `LocalParentDepMgmtChangeInvalidates` | `p02-local-parent-inherit` | ❌ | Build 1 all modules saved. Change the managed `junit` version in root pom.xml `<dependencyManagement>` from `4.13.2` → `4.12`. Build 2: all child modules that reference junit must miss because their effective POM (with resolved dep version) changed. New class `checksumcorrectness.ParentManagedDepVersionChangeInvalidatesTest`. |
| `LocalParentPropertyChangeInvalidates` | `p02-local-parent-inherit` | ❌ | Build 1 all modules saved. Change `maven.compiler.source` from `1.8` → `11` in root pom.xml `<properties>`. Build 2: all children must miss because inherited property value in their effective POM changed. New class `checksumcorrectness.InheritedPropertyChangeInvalidatesTest`. |
| `ExternalParentVersionBumpInvalidates` | `p10-reactor-partial` | ❌ | P10 uses a `_corp-parent` that must be installed first. The corp-parent provides managed plugin versions. Install v1 of corp-parent, build P10 (saved). Modify corp-parent managed dep version, install v2 of corp-parent, change `<parent><version>` in P10 root. Build 2: all modules must miss because the chained external parent config changed. New class `checksumcorrectness.ExternalParentVersionBumpInvalidatesTest`. Requires installing `_corp-parent` twice via a `Verifier` helper before the main test. |

---

## 3. Coverage Summary

| Category | Total Cases | ✅ Covered | ⚠️ Partial | ❌ Not Covered |
|----------|-------------|-----------|-----------|---------------|
| Source & File Inputs | 7 | 5 | 1 | 1 |
| POM & Descriptor Changes | 7 | 3 | 0 | 4 |
| Dependency Management Edge Cases | 6 | 0 | 0 | 6 |
| Plugin Configuration | 5 | 2 | 0 | 3 |
| Profile Activation & Properties | 5 | 1 | 0 | 4 |
| CI-Friendly Versioning | 2 | 1 | 0 | 1 |
| Multi-Module Reactor | 7 | 4 | 0 | 3 |
| Packaging Type & Lifecycle | 4 | 1 | 1 | 2 |
| Extension Loading | 2 | 2 | 0 | 0 |
| Inheritance Chain | 3 | 0 | 0 | 3 |
| **Total** | **48** | **19** | **2** | **27** |

**Priority order for new tests** (highest-value gaps first):
1. `LocalParentDepMgmtChangeInvalidates` — parent-managed dep version is the most common real-world cache invalidation scenario
2. `UnchangedModuleStaysInCache` — verifies precision; over-invalidation is as bad as under-invalidation
3. `ReactorSnapshotSourceChangeInvalidates` — SNAPSHOT propagation is HIGHEST CRITICALITY in multi-module builds
4. `BomVersionChangeInvalidates` — BOM-managed versions are widely used; must invalidate on BOM version bump
5. `WebappFileChangeInvalidates` — webapp assets are frequently edited; must invalidate WAR build
6. `ParallelBuildModuleChangeInvalidates` — parallel builds have unique race-condition risks
7. `ProfileFileActivationInvalidates` — file-activation is environment-sensitive; critical for correctness
8. `MavenPluginPackagingCacheRoundTrip` — `maven-plugin` packaging uses a custom lifecycle; must verify caching works
9. `ExclusionAddedChangesClasspathInvalidates` — classpath changes from exclusion edits must invalidate
10. `PhaseRebindingChangeInvalidates` — lifecycle changes must invalidate
