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

## Overview

Cache configuration provides you additional control over the build-cache extension behavior. Follow it step-by-step to
understand how it works, and figure out an optimal config

### Minimal config

Minimal config

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<cache xmlns="http://maven.apache.org/BUILD-CACHE-CONFIG/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://maven.apache.org/BUILD-CACHE-CONFIG/1.0.0 https://maven.apache.org/xsd/build-cache-config-1.0.0.xsd">

    <configuration>
        <enabled>true</enabled>
        <hashAlgorithm>XX</hashAlgorithm>
    </configuration>

    <input>
        <global>
            <glob>{*.java,*.xml,*.properties}</glob>
        </global>
    </input>
</cache>
```

### Enabling remote cache

Just add `<remote>` section under `<configuration>`

```xml
    <configuration>
        <enabled>true</enabled>
        <hashAlgorithm>XX</hashAlgorithm>
        <remote>
            <url>https://yourserver:port</url>
        </remote>
    </configuration>
```

### Adding more file types to input

Add all the project-specific source code files in `<glob>`. Scala, in this case:

```xml
    <input>
        <global>
            <glob>{*.java,*.xml,*.properties,*.scala}</glob>
        </global>
    </input>
```

### Adding source directory for bespoke project layouts

In most cases, the build-cache extension automatically recognizes directories by introspecting the build. When it is not enough, adding additional directories with `<include>` is possible. Also, you can filter out undesirable dirs and files by using exclude tag.

```xml
    <input>
        <global>
            <glob>{*.java,*.xml,*.properties,*.scala}</glob>
            <includes>
                <include>importantdir/</include>
            </includes>
            <excludes>
                <exclude>tempfile.out</exclude>
            </excludes>
        </global>
    </input>
```

### Plugin property is environment-specific and yields different cache keys in different environments

Consider to exclude env specific properties:

```xml
    <input>
        <global>
            ...
        </global>
        <plugins>
            <plugin artifactId="maven-surefire-plugin">
                <effectivePom>
                    <excludeProperties>
                        <excludeProperty>argLine</excludeProperty>
                    </excludeProperties>
                </effectivePom>
            </plugin>
        </plugins>
    </input>
```

Implications - builds with different `argLine` will have an identical key. Validate that it is acceptable in terms of artifact equivalency.

### Plugin property points to a directory where only a subset of files is relevant

If the plugin configuration property points to `somedir`, it will be scanned with the default glob. You can tweak it with custom
processing rule

```xml
    <input>
        <global>
            ...
        </global>
        <plugins>
            <plugin artifactId="protoc-maven-plugin">
                <dirScan mode="auto">
                    <!--<protoBaseDirectory>${basedir}/..</protoBaseDirectory>-->
                    <tagScanConfigs>
                        <tagScanConfig tagName="protoBaseDirectory" recursive="false" glob="{*.proto}"/>
                    </tagScanConfigs>
                </dirScan>
            </plugin>
        </plugins>
    </input>
```

### Local repository is not updated because the `install` phase is cached

Add `executionControl/runAlways` section:

```xml
    <executionControl>
        <runAlways>
            <plugins>
                <plugin artifactId="maven-failsafe-plugin"/>
            </plugins>
            <executions>
                <execution artifactId="maven-dependency-plugin">
                    <execIds>
                        <execId>unpack-autoupdate</execId>
                    </execIds>
                </execution>
            </executions>
            <goalsLists>
                <goalsList artifactId="maven-install-plugin">
                    <goals>
                        <goal>install</goal>
                    </goals>
                </goalsList>
            </goalsLists>
        </runAlways>
    </executionControl>
```

### I occasionally cached build with `-DskipTests=true`, and tests do not run now

If you add command line flags to your build, they do not participate in effective pom - Maven defers the final value
resolution to plugin runtime. To invalidate the build if the filed value is different in runtime, add a reconciliation section
to `executionControl`:

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<cache xmlns="http://maven.apache.org/BUILD-CACHE-CONFIG/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://maven.apache.org/BUILD-CACHE-CONFIG/1.0.0 https://maven.apache.org/xsd/build-cache-config-1.0.0.xsd">
    <configuration>
        ...
    </configuration>
    <executionControl>
        <runAlways>
            ...
        </runAlways>
        <reconcile>
            <plugins>
                <plugin artifactId="maven-surefire-plugin" goal="test">
                    <reconciles>
                        <reconcile propertyName="skip" skipValue="true"/>
                        <reconcile propertyName="skipExec" skipValue="true"/>
                        <reconcile propertyName="skipTests" skipValue="true"/>
                        <reconcile propertyName="testFailureIgnore" skipValue="true"/>
                    </reconciles>
                </plugin>
            </plugins>
        </reconcile>
    </executionControl>
</cache>
```

Please notice the `skipValue` attribute. It captures the value that makes the plugin skip execution. Think of `skipProperty` as follows: if the build started with `-DskipTests=true`, restoring results from a build with completed tests is safe because the local run does not require completed tests. The same logic applies to any other plugin, not just Surefire.

### How to renormalize line endings in working copy after committing .gitattributes (git 2.16+)

Ensure you've committed (and ideally pushed everything) - no changes in the working copy. After that:

```shell
# Rewrite objects and update the index
git add --renormalize .
# Commit changes
git commit -m "Normalizing line endings."
# Remove working copy paths from the git cache
git rm --cached -r .
# Refresh with new line endings
git reset --hard
```

### I want to cache the interim build and override it later with the final version

Solution: set `-Dmaven.build.cache.remote.save.final=true` to nodes that produce final builds. Such builds will not be overridden
and eventually will replace all interim builds

### I want to disable dependencies checksum calculation of one plugin

Set attribute `excludeDependencies` to `true` in `input/plugins/plugin` section:

```xml
    <input>
      <plugins>
        <plugin artifactId="maven-surefire-plugin" excludeDependencies="true">
        </plugin>
      </plugins>
    </input>
```
