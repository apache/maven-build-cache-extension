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

## Performance Tuning

### General notes

Cache tuning could significantly reduce resource consumption and build execution time, but that is not guaranteed.
Performance-tuning hints might not considerably affect the build time depending on a usage pattern. As usual with
performance tuning, measure results in relevant scenarios to validate results and weigh the pros and cons.

### Hash algorithm selection

By default, the cache uses the [XX](https://cyan4973.github.io/xxHash/) algorithm, which is a very fast
non-cryptographic hash algorithm and is sufficient for most use cases. All supported values for `<hashAlgorithm>` are:

| Identifier | Description                                                                                      |
|------------|--------------------------------------------------------------------------------------------------|
| `XX`       | **Default.** xxHash, non-cryptographic, fastest.                                                 |
| `XXMM`     | xxHash with memory-mapped file I/O. Faster for large codebases; requires a JVM flag on JDK ≥ 17. |
| `METRO`    | Metro hash, standard I/O.                                                                        |
| `METRO+MM` | Metro hash with memory-mapped file I/O. Requires a JVM flag on JDK ≥ 17.                         |
| `SHA-1`    | Cryptographic. Not recommended unless integrity guarantees are required.                         |
| `SHA-256`  | Cryptographic.                                                                                   |
| `SHA-384`  | Cryptographic.                                                                                   |
| `SHA-512`  | Cryptographic, slowest.                                                                          |

```xml
<hashAlgorithm>XX</hashAlgorithm>
```

The memory-mapped algorithms (`XXMM` and `METRO+MM`) require adding the following line to `.mvn/jvm.config` in the
project root to run correctly on JDK ≥ 17:

```
--add-opens java.base/sun.nio.ch=ALL-UNNAMED
```

### Filter out unnecessary artifacts

The price of uploading and downloading huge artifacts could be significant. In many scenarios assembling WAR,
EAR or ZIP archive locally is more efficient than storing them in cache bundles. To filter out artifacts, add the
configuration section:

```xml
<cache>
    <output>
        <exclude>
            <patterns>
              <pattern>.*\.zip</pattern>
            </patterns>
        </exclude>
    </output>
</cache>
```

### Use a lazy restore

By default, the cache tries to restore all artifacts for a project preemptively. Lazy restore could give significant
time by avoiding requesting and downloading unnecessary artifacts from the cache.
It is beneficial when small changes are a dominating build pattern. Use command line flag:

```
-Dmaven.build.cache.lazyRestore=true
```

In cache corruption situations, the lazy cache cannot support fallback to normal execution. It will fail instead. To
heal the corrupted cache, manually remove corrupted cache entries or force cache rewrite.

### Disable project files restoration

By default, cache supports the partial restoration of source code state from cached generated sources (and potentially
more,
depending on configuration). It is helpful in a local environment but likely unnecessary and adds overhead in continuous
integration. To disable, add a command line flag.

```
-Dmaven.build.cache.restoreGeneratedSources=false
```

### Disable post-processing of archives(JARs, WARs, etc) META-INF

The cache could be configured to auto-correct metadata (most notably [MANIFEST.MF
`Implementation-Version`](https://docs.oracle.com/javase/8/docs/technotes/guides/jar/jar.html#Main_Attributes)). The
correction requires copying and repacking archive entries and adds overhead. If the metadata state is not relevant for
the build, consider disabling it (off by default):

```xml
<cache>
    <configuration>
        ...
        <projectVersioning adjustMetaInf="false"/>
        ...
    </configuration>
    ...
</cache>
```
