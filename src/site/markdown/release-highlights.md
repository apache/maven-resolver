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

# Resolver 2.0.x Notable Features

This page lists the notable features that the Resolver 2.0.x releases added, up to 2.0.24.
For the full change lists, see the
[GitHub releases](https://github.com/apache/maven-resolver/releases).

## Transports

* New HTTP/2 capable `jdk` (Java HTTP client) and `jetty` transports.
  Both support HTTP/3 (since 2.0.21).
* New S3 transport (MinIO Java client based).
* Bundle transport can read ZIP files, "mount" them and use as remote repository.
* Separated upload and download thread counts.
* Connector pipelining.
* RFC 9457 (Problem Details for HTTP APIs) support.
* File transporter can use symlinks and hardlinks (experimental).

## Locking

* File-lock is the default named locking.
* New IPC named lock, with a lock daemon that authenticates its clients.
* `NamedLock` can carry more than one name (multi-resource locking).
* Locking inhibitor SPI for "known to be safe" resources.
* New GAECV name mapper is the default, drastically lowers lock contention.

## Dependency collection and conflict resolution

* Default is new BF (breadth-first) collector.
* Configurable graph visiting strategy; `levelOrder` is the default graph visitor (classpath is affected -- is also level order).
* Less memory in `PathConflictResolver` a new conflict resolver that is O(N).
* Improved and faster `TransitiveDependencyManager`.
* Configurable graph visiting strategy; `levelOrder` is the default graph visitor (classpath ordering follows the same level order).
* Less memory in `PathConflictResolver`, a new conflict resolver that is O(N).
* Artifact and dependency validation SPI.
* Pluggable winner selection, default is "nearest" (Maven classic default), with "highest" available as well.

## Versions

* `GenericVersionScheme` follows the specification more closely.
  It also caches versions and exposes qualifiers.
* Version range processing strategies.
* Version filter builder.

## Repositories and checksums

* Remote repository filtering (RRF) is on by default.
* Repository Key Function SPI, strengthens repository selection and origin tracking.
* Metadata and artifact update policy split.
* Trusted checksums support scopes.
  Included checksums can come from `x-amz-meta-*` headers.
* Better checksum control.
  `WarnChecksumPolicy` proceeds when there are no checksums.

## Extension points

* Artifact generators, for example the Sigstore generator.
* `ArtifactTransformer` SPI.
* `RepositorySystem.flatten` exposed.
* `RepositoryCache.computeIfAbsent` exposed.
* A configurable `DependencyGraphDumper` that is reusable.

## Platform

* The build needs Java 21, the target is still Java 8.
* `api`, `spi` and `util` carries Java 9 module metadata.
* `api`, `spi` and `util` carry Java 9 module metadata.
* Lower memory use and many performance fixes.
* Security hardening (2.0.23): credentials are bound to their origin, TLS downgrade redirects are rejected,
  response reads are bounded, remote signals cannot weaken local policy,
  and coordinates and repository keys are validated.
