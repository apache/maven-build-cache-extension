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
[Apache Maven Build Cache Extension](https://maven.apache.org/extensions/maven-build-cache-extension/)
==================================

[![Apache License, Version 2.0, January 2004](https://img.shields.io/github/license/apache/maven.svg?label=License)][license]
[![Maven Central](https://img.shields.io/maven-central/v/org.apache.maven.extensions/maven-build-cache-extension.svg?label=Maven%20Central)](https://search.maven.org/artifact/org.apache.maven.extensions/maven-build-cache-extension)
[![Jenkins Status](https://img.shields.io/jenkins/s/https/ci-maven.apache.org/job/Maven/job/maven-box/job/maven-build-cache-extension/job/master.svg?)][build]
[![Jenkins tests](https://img.shields.io/jenkins/t/https/ci-maven.apache.org/job/Maven/job/maven-box/job/maven-build-cache-extension/job/master.svg?)][test-results]

This project provides a Build Cache Extension feature which calculates out-of-date modules in the build dependencies graph and improves build times by avoiding re-building unnecessary modules.
Read [cache guide](https://maven.apache.org/extensions/maven-build-cache-extension/index.html) for more details.

Requires Maven 3.9+ ([MNG-7391](https://issues.apache.org/jira/browse/MNG-7391)).

License
-------
This code is under the [Apache License, Version 2.0, January 2004][license].

See the [`NOTICE`](./NOTICE) file for required notices and attributions.

[home]: https://maven.apache.org/extensions/maven-build-cache-extension/
[license]: https://www.apache.org/licenses/LICENSE-2.0
[build]: https://ci-maven.apache.org/job/Maven/job/maven-box/job/maven-build-cache-extension/job/master/
[test-results]: https://ci-maven.apache.org/job/Maven/job/maven-box/job/maven/job/maven-build-cache-extension/lastCompletedBuild/testReport/
[build-status]: https://img.shields.io/jenkins/s/https/ci-maven.apache.org/job/Maven/job/maven-box/job/maven-build-cache-extension/job/master.svg
[build-tests]: https://img.shields.io/jenkins/t/https/ci-maven.apache.org/job/Maven/job/maven-box/job/maven-build-cache-extension/job/master.svg

[Apache Maven X Account](https://x.com/ASFMavenProject)
[Apache Maven Bluesky Account](https://bsky.app/profile/maven.apache.org)
[Apache Maven Mastodon Account](https://mastodon.social/deck/@ASFMavenProject@fosstodon.org)
