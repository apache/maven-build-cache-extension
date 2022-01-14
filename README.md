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
Apache Maven Build Cache Extension
==================================

[![ASF Jira](https://img.shields.io/endpoint?url=https%3A%2F%2Fmaven.apache.org%2Fbadges%2Fasf_jira-MBUILDCACHE.json)][jira]
[![Apache License, Version 2.0, January 2004](https://img.shields.io/github/license/apache/maven.svg?label=License)][license]
[![Maven Central](https://img.shields.io/maven-central/v/org.apache.maven.extensions/maven-build-cache-extension.svg?label=Maven%20Central)](https://search.maven.org/artifact/org.apache.maven.extensions/maven-build-cache-extension)
[![Jenkins Status](https://img.shields.io/jenkins/s/https/ci-maven.apache.org/job/Maven/job/maven-box/job/maven-build-cache-extension/job/master.svg?)][build]
[![Jenkins tests](https://img.shields.io/jenkins/t/https/ci-maven.apache.org/job/Maven/job/maven-box/job/maven-build-cache-extension/job/master.svg?)][test-results]

This project provides a Build Cache Extension feature which calculates out-of-date modules in the build dependencies graph and improves build times by avoiding re-building unnecessary modules.
Read [cache guide](src/site/markdown/cache.md) for more details.

Building
--------
The code currently relies on un-released modifications in the core Maven project.  Two [gitmodules](https://git-scm.com/book/en/v2/Git-Tools-Submodules) are included in this git repository to allow building the needed Maven distributions and perform integration tests using those: run `git submodule init && git submodule update` to prepare content.

In order to build those Maven distributions, you first need to launch once the `build-maven.sh` script or the following command in the `maven/maven3` and `maven/maven4` directories:
```
mvn install -DskipTests -P versionlessMavenDist
```
This will build the custom distributions of Maven.

Then you can build the extension like a normal Maven project.

License
-------
This code is under the [Apache License, Version 2.0, January 2004][license].

See the [`NOTICE`](./NOTICE) file for required notices and attributions.

[home]: https://maven.apache.org/extensions/maven-build-cache-extension/
[jira]: https://issues.apache.org/jira/projects/MBUILDCACHE/
[license]: https://www.apache.org/licenses/LICENSE-2.0
[build]: https://ci-maven.apache.org/job/Maven/job/maven-box/job/maven-build-cache-extension/job/master/
[test-results]: https://ci-maven.apache.org/job/Maven/job/maven-box/job/maven/job/maven-build-cache-extension/lastCompletedBuild/testReport/
[build-status]: https://img.shields.io/jenkins/s/https/ci-maven.apache.org/job/Maven/job/maven-box/job/maven-build-cache-extension/job/master.svg
[build-tests]: https://img.shields.io/jenkins/t/https/ci-maven.apache.org/job/Maven/job/maven-box/job/maven-build-cache-extension/job/master.svg
