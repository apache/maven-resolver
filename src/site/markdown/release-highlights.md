# Resolver 2.0.x Notable Features
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

This page lists the notable features that the Resolver 2.0.x releases added, up to 2.0.24.
For the full change lists, see the
[GitHub releases](https://github.com/apache/maven-resolver/releases).

## Transports

* New HTTP/2 capable `jdk` (Java HTTP client) and `jetty` transports.
  Both support HTTP/3 (since 2.0.21).
* New S3 transport (MinIO based).
* Bundle transport can read.
* The `jdk` transport has retries, `Retry-After` support, HTTP compression and preemptive authentication.
* Separate upload and download thread counts.
* Connector pipelining.
* RFC 9457 (Problem Details for HTTP APIs) support.
* `AuthenticationBuilder` supports `SSLContext` (mTLS).
* Option to close the connection at the end of a transaction.
* File transporter can use symlinks and hardlinks (experimental).

## Locking

* File-lock is the default named locking.
* New IPC named lock, with a lock daemon that authenticates its clients.
* `NamedLock` can carry more than one name.
* Locking inhibitor SPI.
* New GAECV name mapper.

## Dependency collection and conflict resolution

* BF (breadth-first) collector and a configurable graph visiting strategy.
* `levelOrder` is the default graph visitor.
* Less memory in `PathConflictResolver`.
* Faster `TransitiveDependencyManager`.
* Relocated candidates from version ranges are kept.
* Resolver does not know about scopes. A `ScopeManager` defines them,
  including an experimental one for Maven 3 and resolution scope aliases.
* Artifact and dependency validation SPI.

## Versions

* `GenericVersionScheme` follows the specification more closely.
  It also caches versions and exposes qualifiers.
* Version range processing strategies are exposed.
* Version filter builder.

## Repositories and checksums

* Remote repository filtering (RRF) is on by default.
  It heals itself from broken auto-discovered prefix files.
* Repository Key Function SPI.
* Remote repository intent.
* Metadata update policy.
* Trusted checksums support scopes.
  Included checksums can come from `x-amz-meta-*` headers.
* Better checksum control.
  `WarnChecksumPolicy` proceeds when there are no checksums.

## Extension points

* Artifact generators, for example the Sigstore generator.
* `ArtifactTransformer` SPI.
* `RepositorySystem.flatten`.
* `RepositoryCache.computeIfAbsent`.
* A configurable `DependencyGraphDumper`.
* Configuration property metadata is available, and `docgen` generates the configuration page.

## Platform

* The build needs Java 17 (later 21). The target is Java 8.
* `api`, `spi` and `util` are Java 9 modules.
* More accurate OSGi metadata.
* Lower memory use and many performance fixes.
* Security hardening (2.0.23): credentials are bound to their origin, TLS downgrade redirects are rejected,
  response reads are bounded, remote signals cannot weaken local policy,
  and coordinates and repository keys are validated.
