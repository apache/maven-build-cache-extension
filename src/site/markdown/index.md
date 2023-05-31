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

Build cache is an extension targeted to simplify and make more efficient work with large builds in Maven.

A combination of features achieves that:

* Incremental builds works on the modified part of the project graph part only
* Subtree support for multimodule projects to work on the part of the codebase in isolation
* Version normalization to support project version agnostic caches
* Project state restoration (partial) to avoid expensive tasks (code generation and similar)

Large projects usually pose scalability challenges, and working with such projects requires a build tool that scales.
The cache
extension addresses that with incremental build execution and the ability to efficiently work on sub-parts of a larger
project without building and installing dependencies from the larger project. Though, features implemented in Maven
should also give noticeable benefits in medium and small-sized projects.

### Cache concepts

The idea of the build cache is to calculate a key from module inputs, store outputs in the cache, and restore them later
transparently to the standard Maven core. The cache deterministically associates each project state with a unique key
and restores it in subsequent builds. It analyzes source code, project model,
plugins, and their parameters. Projects with the same key are up-to-date (not changed) and could be safely restored from
the cache. Projects producing different keys are out-of-date (changed), and the cache fully rebuilds them. In the latter
case, the cache does not make any
interventions to the build execution logic and delegates build work to the standard maven Maven core. This approach
ensures that
artifacts produced in the presence of a cache are equivalent to the result produced by a standard Maven build.   
To achieve an accurate key calculation, the build-cache extension combines automatic introspection
of [project object model](https://maven.apache.org/pom.html#What_is_the_POM) and fine-grained tuning using
a configuration file. Source code content fingerprinting is digests based, which is more reliable over
widely used file timestamps in tools like Make or Apache Ant. Cache outputs could be shared using a remote cache.
Deterministic inputs calculation allows distributed and parallel builds running in heterogeneous environments (like a
cloud of build agents) efficiently reuse cached build artifacts as soon as they are published. Therefore, incremental
Maven is particularly well-suited for large Maven
projects that have a significant number of small modules. Remote cache, combined with relocatable inputs
identification, effectively enables the "change once - build once" approach across all environments.

### Maven insights

Maven is a proven tool with a long history and core design established many years ago. Historically, Maven's core was
designed with generic stable interfaces that don't have a concept of inputs and outputs. It just runs as configured, but
the core does not control the inputs and effects of the run. Most commonly, artifacts produced in the same build
environment from the same source code will be considered equivalent. But even two identically looking builds from the
same source code could have two different results. The question here is tolerance level - can you accept particular
discrepancies? Though technical differences between artifacts like timestamps in manifests are largely ignored, when
compilers used are of different levels, it is likely a critical difference. Should the produced artifacts be considered
equivalents? Yes and No answers are possible and could be desirable in different scenarios. When productivity
and performance are the primary concerns, it could be beneficial to tolerate insignificant discrepancies and maximize
the reuse. As long as correctness is in focus, there could be a demand to comply with the exact release requirements. In
the same way as Maven, the cache correctness is ensured by proper build configuration and control over the build
environment. As Maven itself, the cached result is just an approximation of another build with some tolerance level
(implementation, configuration, and environment driven) that must be configured to meet your needs.

### Implementation insights

Simply put, the build cache is a hash function that takes a Maven project and produces a unique key. Then the key is
used to store and restore build results. Because of different factors, there could be
collisions and instabilities in the produced key. A collision happens when the semantically different builds have the
same key and will result in unintended reuse. Instability means that the same input yields different keys resulting in
cache misses. The ultimate target is to find a tradeoff between correctness and performance by configuring cache
processing rules in an xml file.

To maximize correctness:

* Select every relevant file as input to the engine
* Add all the functional plugin parameters to the reconciliation

To maximize reuse you need to:

* Filter out non-essential files (documentation, IDE configs, and similar)
* Minimize the overall number of controlled plugin parameters and exclude behavioral plugin parameters (like the number
  of threads or log level)
* Make source code relocatable (environment agnostic)

Effectively, cache setup involves inspecting the build, taking these decisions, and reflecting them in the cache
configuration.

Though strict, comprehensive cache rules aiming for 100% coverage of all parameters and files might be tempting, it is
rarely the optimal decision. When applied to real projects, perfect correctness could lead to prevailing hit misses and
render caching useless. Configuring sufficient (good enough) correctness might yield the best outcomes. Incremental
Maven
provides flexible and transparent control over caching policy and allows achieving desired results - maximizes usability
or maximize equivalency between pre-cached candidates and requested builds.

## Usage

Cache extension is an opt-in feature. It is delivered as is, and though the tool went through careful verification, it's
still the build owner's responsibility to verify build outcomes.

### Recommended Scenarios

Given all the information above, the build-cache extension is recommended to use in scenarios when productivity and
performance are in priority. Typical cases are:

* Continuous integration. In conjunction with the remote cache, the extension could drastically reduce build times,
  validate pull requests faster and reduce the load on CI nodes
* Speedup developer builds. By reusing cached builds, developers could verify changes much faster and be more
  productive.
  No more `-DskipTests` and similar.
* Assemble artifacts faster. In some development models, it might be critical to have a build/deploy turnaround as fast
  as
  possible. Caching helps to cut down time drastically in such scenarios because it doesn't require building cached
  dependencies.

For cases when users must ensure the correctness (e.g. prod builds), it is recommended to disable the cache and do clean
builds instead.
Such a scheme allows cache correctness validation by reconciling the outcomes of cached builds against the reference
builds.

## See also

* [Getting started](getting-started.html) - getting starting with cache and usage manual
* [Usage](usage.html) - shared cache setup procedure
* [Remote cache setup](remote-cache.html) - shared cache setup procedure
* [How-To](how-to.html) - cookbook for typical scenarios
* [Performance](performance.html) - performance tuning
* [Cache Parameters](parameters.html) - description of supported parameters
* [Sample config file](maven-build-cache-config.xml)
