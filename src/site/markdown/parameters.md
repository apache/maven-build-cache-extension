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

| Parameter                                                  | Description                                                                                                                              | Usage Scenario                                                                     |
|------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------|
| `-Dmaven.build.cache.configPath=path to file`              | Location of cache configuration file                                                                                                     | Cache config is not in default location                                            |
| `-Dmaven.build.cache.enabled=(true/false)`                 | Cache and associated features disabled/enabled                                                                                           | To remove noise from logs when the remote cache is not available              |
| `-Dmaven.build.cache.remote.enabled=(true/false)`          | Checks and downloads artifacts from the remote cache (overrides <remote enabled=("true"/"false")>)                                       | To control remote cache access by node, if, say, some nodes lack reliable access   |
| `-Dmaven.build.cache.remote.save.enabled=(true/false)`     | Remote cache save allowed or not                                                                                                         | To designate nodes which allowed to push in remote shared cache                    |
| `-Dmaven.build.cache.remote.save.final=(true/false)`       | Prohibit to override remote cache                                                                                                        | Prevents cache records from being overridden by subsequent builds                      |
| `-Dmaven.build.cache.remote.url=`                          | Url of the remote cache (overrides  <remote><url></url></remote>)                                                                        | To override url of remote cache from command line                                  |
| `-Dmaven.build.cache.remote.server.id=`                    | Id of the remote cache server (overrides  <remote id=""></remote>)                                                                       | To override id of remote cache server from command line                            |
| `-Dmaven.build.cache.failFast=(true/false)`                | Fail on the first module which cannot be restored from cache                                                                             | Remote cache setup/tuning/troubleshooting                                          |
| `-Dmaven.build.cache.baselineUrl=<http url>`               | Location of baseline build for comparison                                                                                                | Remote cache setup/tuning/troubleshooting                                          |
| `-Dmaven.build.cache.lazyRestore=(true/false)`             | Restore artifacts from remote cache lazily                                                                                               | Performance optimization                                                           |
| `-Dmaven.build.cache.restoreGeneratedSources=(true/false)` | Do not restore generated sources and directly attached files                                                                             | Performance optimization                                                           |
| `-Dmaven.build.cache.alwaysRunPlugins=<list of plugins>`   | Comma separated list of plugins to always run regardless of cache state. An example: `maven-deploy-plugin:*,maven-install-plugin:install` | Remote cache setup/tuning/troubleshooting                                          |
| `-Dmaven.build.cache.skipCache=(true/false)`               | Skip looking up artifacts in caches. Does not affect writing artifacts to caches, disables only reading when set to `true`               | May be used to trigger a forced rebuild when matching artifacts do exist in caches |

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

| Parameter                                   | Description                                                                                                                                                                                                                                                                          |
|---------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `maven.build.cache.input.glob`              | Project specific glob to select sources. Overrides the global glob.                                                                                                                                                                                                                  |
| `maven.build.cache.input`                   | Additional source code locations. Relative paths calculated from the current project/module root<br/>Example :<br/>```<maven.build.cache.input.1>src/main/scala<maven.build.cache.input.1>```<br/>```<maven.build.cache.input.2>assembly-conf<maven.build.cache.input.2>```          |
| `maven.build.cache.exclude`                 | Paths to exclude from source code search. Relative paths calculated from the current project/module root<br/>Example :<br/>```<maven.build.cache.exclude.1>dist<maven.build.cache.exclude.1>```<br/>```<maven.build.cache.exclude.2>src/main/javagen<maven.build.cache.exclude.2>``` |
| `maven.build.cache.processPlugins`          | Introspect plugins to find inputs or not. The default value is true.                                                                                                                                                                                                                 |
| `maven.build.cache.skipCache`               | Skip looking up artifacts for a particular project in caches. The default value is false.                                                                                                                                                                                            |
| `maven.build.cache.restoreGeneratedSources` | Restore generated sources and directly attached files. The default value is true.                                                                                                                                                                                                    |
