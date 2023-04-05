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

Cache tuning could significantly reduce resource consumption and build execution time, but that is not guaranteed. Performance-tuning hints might not considerably affect the build time depending on a usage pattern. As usual with performance tuning, measure results in relevant scenarios to validate results and weigh the pros and cons.

### Hash algorithm selection

By default, the cache uses the [XX](https://cyan4973.github.io/xxHash/) algorithm, which is a very fast hash algorithm and should be enough for most use cases. 
In projects with a large codebase, the performance of hash algorithms becomes more critical, and other algorithms like
XXMM (XX with memory-mapped files) could provide better performance, depending on the environment.

```xml
<hashAlgorithm>XX</hashAlgorithm>
```

### Filter out unnecessary artifacts

The price of uploading and downloading huge artifacts could be significant. In many scenarios assembling WAR,
EAR or ZIP archive locally is more efficient than writing to soring in cache bundles. To filter out artifacts, add the configuration section:

```xml
<cache>
    <output>
        <exclude>
            <pattern>.*\.zip</pattern>
        </exclude>
    </output>
</cache>
```

### Use a lazy restore

By default, the cache tries to restore all artifacts for a project preemptively. Lazy restore could give significant time by avoiding requesting and downloading unnecessary artifacts from the cache.
It is beneficial when small changes are a dominating build pattern. Use command line flag:

```
-Dmaven.build.cache.lazyRestore=true";
```

In cache corruption situations, the lazy cache cannot support fallback to normal execution. It will fail instead. To heal the corrupted cache, manually remove corrupted cache entries or force cache rewrite.

### Disable project files restoration

By default, cache supports the partial restoration of source code state from cached generated sources (and potentially more,
depending on configuration). It is helpful in a local environment but likely unnecessary and adds overhead in continuous integration. To disable, add a command line flag.

```
-Dmaven.build.cache.restoreGeneratedSources=false";
```

### Disable post-processing of archives(JARs, WARs, etc) META-INF

The cache could be configured to auto-correct metadata (most notably [MANIFEST.MF `Implementation-Version`](https://docs.oracle.com/javase/8/docs/technotes/guides/jar/jar.html#Main_Attributes)). The correction requires copying and repacking archive entries and adds overhead. If the metadata state is not relevant for the build, consider disabling it (off by default):

```xml
<cache>
    <configuration>
        ...
        <projectVersioning adadjustMetaInf="false"/>
        ...
    </configuration>
    ...
</cache>
```
