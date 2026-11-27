# Deprecated module
<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

`maven-resolver-supplier-mvn4` is deprecated.

This module bootstraps Resolver using Maven 4 the "Maven 3 way": it wires the
Resolver components itself and depends on the Maven 4 `maven-resolver-provider`
and `maven-model-builder` artifacts. New applications should use the Maven 4
standalone API instead. It lives in the
[`org.apache.maven.impl.standalone` package](https://maven.apache.org/ref/4-LATEST/impl/maven-impl/apidocs/org/apache/maven/impl/standalone/package-summary.html)
of the [`org.apache.maven:maven-impl` module](https://github.com/apache/maven/tree/master/impl/maven-impl),
which provides ready-made session and repository-system bootstrapping for use
outside a Maven build. The general Maven 4 API is documented in the
[`org.apache.maven.impl` javadoc](https://maven.apache.org/ref/4-LATEST/impl/maven-impl/apidocs/index.html).

The module is still built and released so existing consumers keep working; no
replacement is available inside this repository. Migrate to the standalone API
to receive further improvements.
