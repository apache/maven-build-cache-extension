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
<cache xmlns="http://maven.apache.org/BUILD-CACHE-CONFIG/1.2.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://maven.apache.org/BUILD-CACHE-CONFIG/1.2.0 https://maven.apache.org/xsd/build-cache-config-1.2.0.xsd">

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

### Default Reconciliation Behavior

The build cache extension automatically tracks certain critical plugin properties by default, even without explicit
`executionControl` configuration. These defaults are loaded from `default-reconciliation/defaults.xml`:

* **maven-compiler-plugin** (`compile` and `testCompile` goals): Tracks `source`, `target`, and `release` properties
* **maven-install-plugin** (`install` goal): Tracked to ensure artifacts are installed when needed

This default behavior prevents common cache invalidation issues, particularly in multi-module JPMS (Java Platform Module System)
projects where compiler version changes can cause compilation failures.

**Overriding Defaults:** When you explicitly configure `executionControl` for a plugin, your explicit configuration completely
overrides the defaults for that plugin. For example, to track only the `release` property for maven-compiler-plugin instead
of the default `source`, `target`, and `release`:

```xml
<cache xmlns="http://maven.apache.org/BUILD-CACHE-CONFIG/1.2.0">
    <configuration>
        ...
    </configuration>
    <executionControl>
        <reconcile>
            <plugins>
                <plugin artifactId="maven-compiler-plugin" goal="compile">
                    <reconciles>
                        <reconcile propertyName="release"/>
                    </reconciles>
                </plugin>
            </plugins>
        </reconcile>
    </executionControl>
</cache>
```

This configuration in your `.mvn/maven-build-cache-config.xml` file replaces the built-in defaults. You can also define
reconciliation configurations for plugins that don't have built-in defaults using the same syntax.

### Parameter Validation and Categorization

The build cache extension includes a parameter validation system that categorizes plugin parameters and validates
reconciliation configurations against known parameter definitions.

#### Parameter Categories

All plugin parameters are categorized into two types:

* **Functional Parameters**: Affect the compiled output or build artifacts (e.g., `source`, `target`, `release`, `encoding`)
* **Behavioral Parameters**: Affect how the build runs but not the output (e.g., `verbose`, `fork`, `maxmem`, `skip`)

Only **functional** parameters should be tracked in reconciliation configurations, as behavioral parameters don't affect
the build output and shouldn't invalidate the cache.

#### Validation Features

The extension automatically validates reconciliation configurations and logs warnings/errors for:

* **Unknown parameters**: Parameters not defined in the plugin's parameter definition (ERROR level)
  - May indicate a plugin version mismatch or renamed parameter
  - Suggests updating parameter definitions or removing the parameter from reconciliation

* **Behavioral parameters in reconciliation**: Parameters categorized as behavioral (WARN level)
  - Suggests that the parameter likely shouldn't affect cache invalidation
  - Consider removing if it doesn't actually affect build output

#### Adding Parameter Definitions for New Plugins

Parameter definitions are stored in `src/main/resources/plugin-parameters/{artifactId}.xml`. To add validation for a new plugin:

1. Create an XML file following the schema in `plugin-parameters.xsd`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<plugin xmlns="http://maven.apache.org/PLUGIN-PARAMETERS/1.0.0"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://maven.apache.org/PLUGIN-PARAMETERS/1.0.0 plugin-parameters.xsd">
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-example-plugin</artifactId>

  <goals>
    <goal>
      <name>example-goal</name>
      <parameters>
        <parameter>
          <name>outputDirectory</name>
          <type>functional</type>
          <description>Directory where output is written</description>
        </parameter>
        <parameter>
          <name>verbose</name>
          <type>behavioral</type>
          <description>Enable verbose logging</description>
        </parameter>
      </parameters>
    </goal>
  </goals>
</plugin>
```

2. Place the file in the classpath at `plugin-parameters/{artifactId}.xml`

3. The extension will automatically load and validate against this definition

#### Version-Specific Parameter Definitions

The parameter validation system supports version-specific definitions to handle plugins that change parameters across versions. This allows accurate validation even when plugin APIs evolve.

**How Version Matching Works:**

- Definitions include a `minVersion` element specifying the minimum plugin version they apply to
- At runtime, the extension selects the definition with the highest `minVersion` that is ≤ the actual plugin version
- Multiple version-specific definitions can exist in a single file

**Example with version-specific parameters:**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<plugins>
  <!-- Parameters for versions 1.0.0 through 2.x -->
  <plugin xmlns="http://maven.apache.org/PLUGIN-PARAMETERS/1.0.0">
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-example-plugin</artifactId>
    <minVersion>1.0.0</minVersion>
    <goals>
      <goal>
        <name>process</name>
        <parameters>
          <parameter>
            <name>legacyParameter</name>
            <type>functional</type>
            <description>Deprecated in 3.0.0</description>
          </parameter>
        </parameters>
      </goal>
    </goals>
  </plugin>

  <!-- Parameters for versions 3.0.0+ (breaking change) -->
  <plugin xmlns="http://maven.apache.org/PLUGIN-PARAMETERS/1.0.0">
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-example-plugin</artifactId>
    <minVersion>3.0.0</minVersion>
    <goals>
      <goal>
        <name>process</name>
        <parameters>
          <parameter>
            <name>newParameter</name>
            <type>functional</type>
            <description>Added in 3.0.0</description>
          </parameter>
        </parameters>
      </goal>
    </goals>
  </plugin>
</plugins>
```

**Version Selection Examples:**

- Plugin version `1.5.0` → Uses definition with `minVersion=1.0.0`
- Plugin version `3.0.0` → Uses definition with `minVersion=3.0.0`
- Plugin version `4.0.0` → Uses definition with `minVersion=3.0.0` (highest available)
- SNAPSHOT versions are handled correctly (e.g., `3.0.0-SNAPSHOT` matches `minVersion=3.0.0`)

**Current Coverage**: Parameter definitions are included for `maven-compiler-plugin` and `maven-install-plugin`.

### I occasionally cached build with `-DskipTests=true`, and tests do not run now

If you add command line flags to your build, they do not participate in effective pom - Maven defers the final value
resolution to plugin runtime. To invalidate the build if the filed value is different in runtime, add a reconciliation section
to `executionControl`:

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<cache xmlns="http://maven.apache.org/BUILD-CACHE-CONFIG/1.2.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://maven.apache.org/BUILD-CACHE-CONFIG/1.2.0 https://maven.apache.org/xsd/build-cache-config-1.2.0.xsd">
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

### I want to disable caching of compile-only builds

By default, the cache extension saves build outputs when running compile-only phases (like `mvn compile` or `mvn test-compile`).
This allows subsequent builds to restore compiled classes without recompilation. To disable this behavior and only cache
builds that reach the package phase or later:

```shell
mvn compile -Dmaven.build.cache.cacheCompile=false
```

This is useful when:
* You want to ensure cache entries always contain packaged artifacts (JARs, WARs, etc.)
* Your workflow relies on artifacts being available in the local repository
* You prefer the traditional behavior where only complete builds are cached
