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

## Build Cache Parameters

This document contains various configuration parameters supported by the cache engine.

### Command line flags

| Parameter                                                  | Description                                                                                                                                                                                                                                           | Usage Scenario                                                                                  |
|------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------|
| `-Dmaven.build.cache.configPath=path to file`              | Location of cache configuration file                                                                                                                                                                                                                  | Cache config is not in default location                                                         |
| `-Dmaven.build.cache.location=<path>`                      | Override the local cache repository directory (default: `build-cache` directory sibling to the local Maven repository)                                                                                                                                | Store the local cache on a faster disk or a shared network path                                 |
| `-Dmaven.build.cache.enabled=(true/false)`                 | Cache and associated features disabled/enabled                                                                                                                                                                                                        | To remove noise from logs when the remote cache is not available                                |
| `-Dmaven.build.cache.remote.enabled=(true/false)`          | Checks and downloads artifacts from the remote cache (overrides <remote enabled=("true"/"false")>)                                                                                                                                                    | To control remote cache access by node, if, say, some nodes lack reliable access                |
| `-Dmaven.build.cache.remote.save.enabled=(true/false)`     | Remote cache save allowed or not                                                                                                                                                                                                                      | To designate nodes which allowed to push in remote shared cache                                 |
| `-Dmaven.build.cache.remote.save.final=(true/false)`       | Prohibit to override remote cache                                                                                                                                                                                                                     | Prevents cache records from being overridden by subsequent builds                               |
| `-Dmaven.build.cache.remote.url=`                          | Url of the remote cache (overrides  <remote><url></url></remote>)                                                                                                                                                                                     | To override url of remote cache from command line                                               |
| `-Dmaven.build.cache.remote.server.id=`                    | Id of the remote cache server (overrides  <remote id=""></remote>)                                                                                                                                                                                    | To override id of remote cache server from command line                                         |
| `-Dmaven.build.cache.failFast=(true/false)`                | Fail on the first module which cannot be restored from cache                                                                                                                                                                                          | Remote cache setup/tuning/troubleshooting                                                       |
| `-Dmaven.build.cache.baselineUrl=<http url>`               | Location of baseline build for comparison                                                                                                                                                                                                             | Remote cache setup/tuning/troubleshooting                                                       |
| `-Dmaven.build.cache.lazyRestore=(true/false)`             | Restore artifacts from remote cache lazily                                                                                                                                                                                                            | Performance optimization                                                                        |
| `-Dmaven.build.cache.restoreGeneratedSources=(true/false)` | Restore generated sources and directly attached files in the corresponding project directories. (default is true)                                                                                                                                     | Performance optimization                                                                        |
| `-Dmaven.build.cache.restoreOnDiskArtifacts=(true/false)`  | Restore generated artifacts in the project build directory. (default is true)                                                                                                                                                                         | Performance optimization                                                                        |
| `-Dmaven.build.cache.alwaysRunPlugins=<list of plugins>`   | Comma separated list of plugins to always run regardless of cache state. An example: `maven-deploy-plugin:*,maven-install-plugin:install`                                                                                                             | Remote cache setup/tuning/troubleshooting                                                       |
| `-Dmaven.build.cache.skipCache=(true/false)`               | Skip looking up artifacts in caches. Does not affect writing artifacts to caches, disables only reading when set to `true`                                                                                                                            | May be used to trigger a forced rebuild when matching artifacts do exist in caches              |
| `-Dmaven.build.cache.skipSave=(true/false)`                | Skip writing build result in caches. Does not affect reading from the cache.                                                                                                                                                                          | Configuring MR builds to benefits from the cache, but restricting writes to the `master` branch |
| `-Dmaven.build.cache.mandatoryClean=(true/false)`          | Enable or disable the necessity to execute the `clean` phase in order to store the build result in cache. Default: `false`                                                                                                                            | Reducing the risk to save "wrong" files in cache in a local dev environment                     |
| `-Dmaven.build.cache.cacheCompile=(true/false)`            | Cache compile phase outputs (classes, test-classes, generated sources). When enabled (default), compile-only builds create cache entries that can be restored by subsequent builds. When disabled, caching only occurs during package phase or later. | Performance optimization for incremental builds                                                 |

### Project-level properties

Project-level parameters allowing to override global parameters on the project level. Must be specified as prefixed
project properties:

```xml

<pom>
    ...
    <properties>
        <maven.build.cache.input.glob>{*.css}</maven.build.cache.input.glob>
    </properties>
</pom>
```

| Parameter                                   | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
|---------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `maven.build.cache.input.glob`              | Project specific <a href="build-cache-config.html#global">glob</a> to select sources. Overrides the global glob.                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| `maven.build.cache.input`                   | Additional <a href="build-cache-config.html#include">inputs</a><br/><br/>Example :<br/>```<maven.build.cache.input.1>src/main/scala<maven.build.cache.input.1>```<br/>```<maven.build.cache.input.2>assembly-conf<maven.build.cache.input.2>```                                                                                                                                                                                                                                                                                                                     |
| `maven.build.cache.exclude.xxx`             | Additional <a href="build-cache-config.html#class_exclude">exclusion</a>. <br/><br/>Example :<br/>```<maven.build.cache.exclude.value.1>src/main/java/package-info.java<maven.build.cache.exclude.value.1>```<br/>```<maven.build.cache.exclude.value.2>src/main/resources<maven.build.cache.exclude.value.2>```<br/>```<maven.build.cache.exclude.glob.2>*.md<maven.build.cache.exclude.glob/2>```<br/>Produce two project exclusions :  <br/>```<exclude>src/main/java/package-info.java</exclude>```<br/>```<exclude glob="*.md">src/main/resources</exclude>``` |
| `maven.build.cache.processPlugins`          | Introspect plugins to find inputs or not. The default value is true.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| `maven.build.cache.skipCache`               | Skip looking up artifacts for a particular project in caches. The default value is false.                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| `maven.build.cache.restoreGeneratedSources` | Restore generated sources and directly attached files in the corresponding project directories. The default value is true.                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| `maven.build.cache.restoreOnDiskArtifacts`  | Restore generated artifacts in the project build directory. The default value is true.                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |

### XML configuration reference

The following elements are supported in `maven-build-cache-config.xml` but have no command-line equivalent.

#### `<local>` — local cache storage options

```xml

<configuration>
    <local>
        <maxBuildsCached>3</maxBuildsCached>
        <location>/path/to/local-cache</location>
    </local>
</configuration>
```

| Element           | Default                                                 | Description                                                                                 |
|-------------------|---------------------------------------------------------|---------------------------------------------------------------------------------------------|
| `maxBuildsCached` | `3`                                                     | Maximum number of cached build records retained per project.                                |
| `location`        | `build-cache` dir sibling to the local Maven repository | Custom path for the local cache repository. Overridable via `-Dmaven.build.cache.location`. |

#### `<remote>` — transport and identity attributes

```xml

<configuration>
    <remote enabled="true" id="my-cache" transport="resolver">
        <url>http://your-buildcache-url</url>
    </remote>
</configuration>
```

| Attribute      | Default    | Description                                                                                                                                 |
|----------------|------------|---------------------------------------------------------------------------------------------------------------------------------------------|
| `id`           | `cache`    | Matches a `<server>` entry in `settings.xml` for authentication. Overridable via `-Dmaven.build.cache.remote.server.id`.                    |
| `transport`    | `resolver` | Transport layer. Currently only `resolver` (Maven Resolver / Aether) is supported.                                                          |
| `saveToRemote` | `false`    | Save build outputs to the remote cache. Recommended to enable on CI agents only. Overridable via `-Dmaven.build.cache.remote.save.enabled`. |

#### `<attachedOutputs>` — extra output directories and permissions

```xml

<configuration>
    <attachedOutputs preservePermissions="true">
        <dirNames>
            <dirName>generated-sources/apt</dirName>
        </dirNames>
    </attachedOutputs>
</configuration>
```

| Element/Attribute     | Default | Description                                                                                              |
|-----------------------|---------|----------------------------------------------------------------------------------------------------------|
| `preservePermissions` | `true`  | Preserve Unix file-system permissions when restoring artifacts from cache. Requires a POSIX file system. |
| `dirNames/dirName`    | —       | Additional output directories to include in cached artifacts beyond standard Maven output directories.   |

#### `<projectVersioning>` — version handling in cached artifacts

```xml

<configuration>
    <projectVersioning adjustMetaInf="true" calculateProjectVersionChecksum="false"/>
</configuration>
```

| Attribute                         | Default | Description                                                                                               |
|-----------------------------------|---------|-----------------------------------------------------------------------------------------------------------|
| `adjustMetaInf`                   | `false` | Auto-correct `Implementation-Version` in `MANIFEST.MF` when restoring artifacts. Adds repacking overhead. |
| `calculateProjectVersionChecksum` | `false` | Include the project version string in the cache key, making the cache version-aware.                      |

#### `<debugs>` — diagnostic metadata in build records

```xml

<configuration>
    <debugs>
        <debug>FileHash</debug>
        <debug>EffectivePom</debug>
    </debugs>
</configuration>
```

| Value          | Description                                                                                                |
|----------------|------------------------------------------------------------------------------------------------------------|
| `FileHash`     | Records per-file hashes in `buildinfo.xml`. Useful for diagnosing cache misses.                            |
| `EffectivePom` | Embeds the full effective POM XML in `buildinfo.xml`. Useful for diagnosing effective-pom hash mismatches. |

#### `<executionControl>/<ignoreMissing>` — gracefully skip optional plugins

```xml

<executionControl>
    <ignoreMissing>
        <plugins>
            <plugin artifactId="some-optional-plugin"/>
        </plugins>
    </ignoreMissing>
</executionControl>
```

Plugins listed under `ignoreMissing` are skipped gracefully when they are not found in the cache, instead of
causing a cache miss failure. Useful for optional plugins that run only in specific environments (e.g.,
CI-only reporting plugins).
