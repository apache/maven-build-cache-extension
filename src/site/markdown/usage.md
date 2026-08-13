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

## Usage

Once the extension is activated, the cache kicks in automatically. By default, compile-phase builds
(`mvn compile`, `mvn test-compile`) are cached in addition to `package` and later phases. Set
`-Dmaven.build.cache.cacheCompile=false` to restrict caching to the `package` phase and above.

### Single goal invocations

Goals invoked directly from the command line (e.g. `mvn compiler:compile`, `mvn resources:resources`) are also
cached when their default lifecycle phase is a real, post-clean phase — such an invocation behaves like running
that phase. Goals with no default phase (e.g. `jetty:run`, `exec:java`) and mixed lifecycle+CLI invocations
(e.g. `mvn package dependency:tree`) are left untouched and simply run. This can be disabled with
`-Dmaven.build.cache.cacheSingleGoal=false`.

### Forked lifecycle restore

Some goals fork a prerequisite lifecycle before running — for example `jetty:run` and `spring-boot:run` fork
`test-compile` so the application is compiled before the server starts. When such a goal is invoked directly, the
forked lifecycle restores previously cached outputs instead of recompiling (it never saves a cache entry of its
own). This is effective with the default `singlethreaded` builder and with `multithreaded` (`-T`); it does not
apply to the `concurrent` builder. Disable it with `-Dmaven.build.cache.restoreForkedExecutions=false`.

## Subtree builds

The build could be invoked on any module in the project and will try to discover the cache by introspecting
dependencies. In order
to identify which dependencies are cacheable, the cache introspects the following:

* Project root location passed with `-Dmaven.multiModuleProjectDirectory`
* Profiles that activate full graph in the project build. The cache uses this `full` mode to categorize project modules
  as cacheable or not:

```xml

<configuration>
    ...
    <multiModule>
        <discovery>
            <scanProfiles>
                <scanProfile>my-full-project-profile</scanProfile>
            </scanProfiles>
        </discovery>
    </multiModule>
    ...
</configuration>
```

## Disable cache

Disable in config:

```xml

<cache>
    <configuration>
        <enabled>false</enabled>
    </configuration>
</cache>
```

On the command line:

```
-Dmaven.build.cache.enabled=false
```

When a configuration is disabled by default in the config, it can be enabled via the command line with:

```
-Dmaven.build.cache.enabled=true
```

## IDE support

Build cache extension is generally compatible with IDEs with one limitation:

* The cache doesn't restore the entire project state. Compiled classes, unpacked artifacts, and similar ones typically
  will not be restored in the build directory (aka `target`). Configure your IDE to not use Maven
  build (`target`) directories for compilation and execution. In that case, IDE will provide fast compilation using
  native caches, and
  the build cache will supplement that with fast builds.
