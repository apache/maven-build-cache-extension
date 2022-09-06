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

This documents contains various configuration parameters supported by cache engine

### Command line flags

| Parameter                                                  | Description                                                                                                                              | Usage Scenario |
|------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------| ----------- |
| `-Dmaven.build.cache.configPath=path to file`              | Location of cache configuration file                                                                                                     | Cache config is not in default location |
| `-Dmaven.build.cache.enabled=(true/false)`                 | Cache and associated features disabled/enabled                                                                                           | To remove noise from logs then remote cache is not available |
| `-Dmaven.build.cache.remote.save.enabled=(true/false)`     | Remote cache save allowed or not                                                                                                         | To designate nodes which allowed to push in remote shared cache |
| `-Dmaven.build.cache.remote.save.final=(true/false)`       | Prohibit to override remote cache                                                                                                        | To ensure that reference build is not overridden by interim build |
| `-Dmaven.build.cache.failFast=(true/false)`                | Fail on the first module which cannot be restored from cache                                                                             | Remote cache setup/tuning/troubleshooting |
| `-Dmaven.build.cache.baselineUrl=<http url>`               | Location of baseline build for comparison                                                                                                | Remote cache setup/tuning/troubleshooting |
| `-Dmaven.build.cache.lazyRestore=(true/false)`             | Restore artifacts from remote cache lazily                                                                                               | Performance optimization |
| `-Dmaven.build.cache.restoreGeneratedSources=(true/false)` | Do not restore generated sources and directly attached files                                                                             | Performance optimization |
| `-Dmaven.build.cache.alwaysRunPlugins=<list of plugins>`   | Comma seprated list of plugins to always run regardless of cache state. An example: `maven-deploy-plugin:*,maven-install-plugin:install` | Remote cache setup/tuning/troubleshooting |
| `-Dmaven.build.cache.skipLookup=(true/false)`              | Skip looking up artifacts in caches. Does not affect writing artifacts to caches, disables only reading when set to `true`               | May be used to trigger a forced rebuild when maching artifatcs do exist in caches|

### Project level properties

Project level parameters allow overriding global parameters on project level Must be specified as project properties:

```xml
<pom>
    ...
    <properties>
        <maven.build.cache.input.glob>{*.css}</maven.build.cache.input.glob>
    </properties>
</pom>
```

| Parameter                                   | Description                                                                                                                                                                                |
|---------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `maven.build.cache.input.glob`              | Project specific glob to select sources. Overrides global glob.                                                                                                                            |
| `maven.build.cache.input`                   | Property prefix to mark paths which must be additionally scanned for source code. Value of property starting with this prefix will be treated as path relatively to current project/module |
| `maven.build.cache.exclude`                 | Property prefix to mark paths which must be excluded from source code search. Value of property starting with this prefix will be treated as path to current project/module                |
| `maven.build.cache.processPlugins`          | Introspect plugins to find inputs or not. Default is true.                                                                                                                                 |
| `maven.build.cache.skipLookup`              | Skip looking up artifacts for a particular project in caches. Default is false.                                                                                                            |
| `maven.build.cache.restoreGeneratedSources` | Restore generated sources and directly attached files. Default is true.                                                                                                                    |
