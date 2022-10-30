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

This document describes a generic approach to remote cache setup. Due to Maven model limitations, the process is
semi-manual but allows you to achieve sufficient control and transparency over caching logic. The process implies good
knowledge of both Maven and the project.

### Before you start

Before you start, please keep in mind the basic principles:

* The cache is key based. Using the HashTree-like technique it produces the key by hashing every configured source code
  file, every dependency, and effective pom (including plugin parameters). Every element's hash contributes to the key.
  The engine must consume identical hashes to produce the same key.
* There is no built-in normalization of line endings in this implementation. File hash calculation is raw bytes based. A
  common pitfall is a Git checkout with CRLF on Win and LF on Linux. The same commit with different line endings will
  produce different hashes.
* Parameters of plugins are reconciled in runtime. For example, to avoid accidentally reusing builds that never run
  tests, track all critical surefire parameters (`skipTests` and similar) in config. The same applies to all other
  plugins.

## Step-By-Step

### Minimize the number of moving parts

* Run build with a single-threaded builder to make sure logs from different modules do not interfere
* Use the same branch which no one else commits to
* Designate a single agent/node for CI builds
* Preferably use the same OS between CI and local machine

### Fork branch for cache setup purposes

Fork stable code branch for cache setup purposes, as you will need source code that doesn't change over setup time.
Also, you likely need to make code changes as long as you go.

### Setup HTTP server to store artifacts

To share results, the cache needs shared storage. The simplest option is to set up an HTTP server that supports HTTP
PUT/GET/HEAD operations (Nginx, Apache, or similar). Add the URL to the config and
change `remote@enabled` to true:

```xml
<!--Default @id is 'cache'-->
<remote enabled="true" id="my-cache">
    <url>http://your-buildcache-url</url>
</remote>
```

If proxy or authentication is required to access the remote cache, add server record to settings.xml as described
in [Servers](https://maven.apache.org/settings.html#Servers). Reference the server in the cache config:

```xml

<servers>
    <server>
        <!-- Should match id attribute from the 'remote' tag in cache config or should be 'cache' by default -->
        <id>my-cache</id>
        <username>[cache user]</username>
        <password>[cache password]</password>
    </server>
</servers>
```

Besides the HTTP server, the remote cache could be configured using any storage supported
by [Maven Resolver](https://maven.apache.org/resolver/). That includes a broad set of options, including SSH, FTP, and
many others, using [Maven Wagon](https://maven.apache.org/wagon/). See Wagon documentation for a complete list of
options and other details.

### Build selection

Build stored in cache ideally should be assembled in the most correct, comprehensive, and complete way. Pull request
builds are typically good candidates to populate the cache because this is where quality safeguards are commonly
applied.

### CI Build setup to seed shared cache

To allow writes in the remote cache, add JVM property to designated CI builds.

```
-Dmaven.build.cache.remote.save.enabled=true
```

Run the build, review the log, and verify the successful upload of cache artifacts to the remote cache. Now, rerun the
build and ensure that it
completes almost instantly because it is fully cached.

### Make remote cache portable

As practice shows, builds that run on local workstations and CI environments differ. A straightforward attempt to
reuse cache produced in different environments usually results in cache misses. Out-of-the-box caches are rarely
portable because of differences in CI jobs, environment specifics, and project configurations. Making the cache portable
is typically the most challenging and time-consuming part of the setup. Follow the steps below to achieve a portable
configuration iteratively.

* Enable fail-fast mode to fail the build on the first discrepancy between the remote and local data
* Provide reference to the CI build as a baseline for comparing your local and remote builds. Go to the reference CI
  build log, and in the end, find the line about saving `build-cache-report.xml`:

```
[INFO] [CACHE] Saved to remote cache https://your-cache-url/<...>/915296a3-4596-4eb5-bf37-f6e13ebe087e/build-cache-report.xml
```

Copy the link to a `build-cache-report.xml` and provide it to your local build as a baseline for comparison.

* Run local build. The command line should look similar to this:

```bash
mvn verify -Dmaven.build.cache.failFast=true -Dmaven.build.cache.baselineUrl=https://your-cache-url/<...>/915296a3-4596-4eb5-bf37-f6e13ebe087e/build-cache-report.xml
```

Once a discrepancy between remote and local builds is detected, the cache will fail and write report in the
project's `target/incremental-maven` directory:

```
* buildinfo-baseline-3c64673e23259e6f.xml - build specification from baseline build
* buildinfo-db43936e0666ce7.xml - build specification of local build
* buildsdiff.xml - comparison report with list of discrepancies 
```

Review `buildsdiff.xml` file and eliminate detected discrepancies. You can also diff build-info files directly to get
low-level insights. See techniques to configure cache in [How-To](how-to.md) and troubleshooting of typical issues
in the section below. Also, it is possible to diff remote and local `buildInfo.xml` files directly using any tool of
your preference.

## Common issues

### Issue 1: Local checkout with different line endings

Solution: normalize line endings. The current implementation doesn't have built-in line endings normalization, need to
apply it manually. In git, using the `.gitattributes` file is the recommended way to establish consistent line endings
across all environments.

### Issue 2: Effective poms mismatch because of plugins injection by profiles

Different profiles between remote and local builds are likely to result in different content of effective poms.
Effective pom contributes hash value to the key that could lead to cache misses. Solution: instead of adding/removing
specific plugins by profiles, set the default value of the plugin's `skip` or `disabled` flag in profile properties
instead. Instead of:

```xml

<profiles>
    <profile>
        <id>run-plugin-in-ci-only</id>
        <build>
            <plugins>
                <plugin>
                    <artifactId>surefire-report-maven-plugin</artifactId>
                    <configuration>
                        <!-- my configuration -->
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

Use:

```xml

<properties>
    <!-- default value -->
    <skip.plugin.property>true</skip.plugin.property>
</properties>
<build>
<plugins>
    <plugin>
        <artifactId>maven-surefire-plugin</artifactId>
        <configuration>
            <!--  flag to control the plugin behavior - skip or run -->
            <skip>${skip.plugin.property}</skip>
        </configuration>
    </plugin>
</plugins>
</build>
<profiles>
<profile>
    <id>run-plugin-in-ci-only</id>
    <properties>
        <!-- override to run the plugin in reference ci build -->
        <skip.plugin.property>false</skip.plugin.property>
    </properties>
</profile>
</profiles>
```

Check effective pom in `buildinfo` files under `/build/projectsInputInfo/item[@type='pom']` xpath to see the content.

### Issue 3: Effective pom mismatch because of environment-specific properties

Sometimes it is impossible to avoid discrepancies in different environments - for example, if a plugin
takes the command line as a parameter, it likely spells differently on Win and Linux. Such commands will appear in the
effective
pom as different literal values, resulting in a different effective pom hash and cache key mismatch. Solution:
Filter out environment-specific properties from the effective pom model:

```xml

<input>
    <global>
        ...
    </global>
    <plugin artifactId="maven-surefire-plugin">
        <effectivePom>
            <excludeProperty>argLine</excludeProperty>
        </effectivePom>
    </plugin>
</input>
```

### Issue 4: Unexpected or transient files in cache key calculation

Potential reasons: plugins or tests emit temporary files (logs and similar) in non-standard locations. Solution: adjust
global exclusions list to filter out the unexpected files:

```xml

<global>
    <exclude>tempfile.out</exclude>
</global>
```

See the sample config for the exact syntax.

### Issue 5: Difference in tracked plugin properties

Tracked properties ensure consistent values with the cache record. Discrepancies could happen
for any plugin for many reasons. Example: local build is using java target 1.6, remote: 1.8. `buildsdiff.xml` will
produce something like

```xml

<mismatch item="target"
          current="1.8"
          baseline="1.6"
          reason="Plugin: default-compile:compile:compile:maven-compiler-plugin:org.apache.maven.plugins:3.8.1 has mismatch in tracked property and cannot be reused"
          resolution="Align properties between remote and local build or remove property from tracked list if mismatch could be tolerated. In some cases it is possible to add skip value to ignore lax mismatch"/>
```

The solution is at your discretion. For a tracked property, out-of-date status is fair and expected. If you want to
relax consistency rules in favor of compatibility, remove property from the reconciliations list
