# Configuration Options
<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

Option | Type | Description | Default Value | Supports Repo ID Suffix
--- | --- | --- | --- | --- | --- | ---
`aether.artifactResolver.snapshotNormalization` | boolean | It replaces the timestamped snapshot file name with a filename containing the `SNAPSHOT` qualifier only. This only affects resolving/retrieving artifacts but not uploading those. | `true` | no
`aether.checksums.forSignature` | boolean | Flag indicating if signature artifacts (`.asc`) should have checksums. | `false` | no
`aether.checksums.algorithms` | String | List of [algorithms](https://docs.oracle.com/javase/7/docs/technotes/guides/security/StandardNames.html#MessageDigest) passed to [`MessageDigest`](https://docs.oracle.com/javase/7/docs/api/java/security/MessageDigest.html) with which checksums are validated (downloaded) and generated (uploaded). | `"SHA-512,SHA-256,SHA-1,MD5"` | no
`aether.conflictResolver.verbose` | boolean | Flag controlling the conflict resolver's verbose mode. | `false` | no
`aether.connector.basic.threads` or `maven.artifact.threads` | int | Number of threads to use for uploading/downloading. | `5` | no
`aether.connector.classpath.loader` | ClassLoader | `ClassLoader` from which resources should be retrieved which start with the `classpath:` protocol. | `Thread.currentThread().getContextClassLoader()` | no
`aether.connector.connectTimeout` | long | Connect timeout in milliseconds. | `10000` | yes
`aether.connector.http.cacheState` | boolean | Flag indicating whether a memory-based cache is used for user tokens, connection managers, expect continue requests and authentication schemes. | `true` | no
`aether.connector.http.credentialEncoding` | String | The encoding/charset to use when exchanging credentials with HTTP servers. | `"ISO-8859-1"` | yes
`aether.connector.http.headers` | `Map<String, String>` | The request headers to use for HTTP-based repository connectors. The headers are specified using a map of strings mapping a header name to its value. The repository-specific headers map is supposed to be complete, i.e. is not merged with the general headers map. | - | yes
`aether.connector.https.cipherSuites` | String | Comma-separated list of [Cipher Suites](https://docs.oracle.com/javase/7/docs/technotes/guides/security/StandardNames.html#ciphersuites) which are enabled for HTTPS connections. | - (no restriction) | no
`aether.connector.https.protocols` | String | Comma-separated list of [Protocols](https://docs.oracle.com/javase/7/docs/technotes/guides/security/StandardNames.html#jssenames) which are enabled for HTTPS connections. | - (no restriction) | no
`aether.connector.perms.fileMode` | String | [Octal numerical notation of permissions](https://en.wikipedia.org/wiki/File_system_permissions#Numeric_notation) to set for newly created files. Only considered by certain Wagon providers. | - | no
`aether.connector.perms.dirMode` | String | [Octal numerical notation of permissions](https://en.wikipedia.org/wiki/File_system_permissions#Numeric_notation) to set for newly created directories. Only considered by certain Wagon providers. | - | no
`aether.connector.perms.group` | String | Group which should own newly created directories/files. Only considered by certain Wagon providers. | - | no
`aether.connector.persistedChecksums` | boolean | Flag indicating whether checksums which are retrieved during checksum validation should be persisted in the local filesystem next to the file they provide the checksum for. | `true` | no
`aether.connector.resumeDownloads` | boolean | Whether to resume partially downloaded files if the download has been interrupted. | `true` | yes
`aether.connector.resumeThreshold` | long | The size in bytes which a partial download needs to have at least to be resumed. Requires `aether.connector.resumeDownloads` to be `true` to be effective. | `64 * 1024` | yes
`aether.connector.requestTimeout` | long | Request timeout in milliseconds. | `1800000` | yes
`aether.connector.smartChecksums` | boolean | Flag indicating that instead of comparing the explicit checksum from the remote repo with the calculated one it will try to extract the reference checksum from the actual artifact requests's response header named `ETag` in format `{SHA1{<checksum>}}`. This only works for HTTP(S) requests and certain transport extensions. In addition it only supports SHA-1. | `true` | no
`aether.connector.userAgent` | String | The user agent that repository connectors should report to servers. |  `"Aether"` | no
`aether.connector.wagon.config` | Object | The configuration to use for the Wagon provider. | - | yes (must be used)
`aether.dependencyCollector.maxCycles` | int | Only up to the given amount cyclic dependencies are emitted. | `10` | no
`aether.dependencyCollector.maxExceptions` | int | Only exceptions up to the number given in this configuration property are emitted. Exceptions which exceed that number are swallowed. | `50` | no
`aether.dependencyManager.verbose` | boolean | Flag controlling the verbose mode for dependency management. If enabled, the original attributes of a dependency before its update due to dependency managemnent will be recorded in the node's `DependencyNode#getData()` when building a dependency graph. | `false` | no
`aether.enhancedLocalRepository.trackingFilename` | String | Filename of the file in which to track the remote repositories. | `"_remote.repositories"` | no
`aether.interactive` | boolean | A flag indicating whether interaction with the user is allowed. | `false` | no
`aether.metadataResolver.threads` | int | Number of threads to use in parallel for resolving metadata. | `4` | no
`aether.offline.protocols` | String | Comma-separated list of protocols which are supposed to be resolved offline. | - | no
`aether.offline.hosts` | String | Comma-separated list of hosts which are supposed to be resolved offline. | - | no
`aether.priority.<class>` | float | The priority to use for a certain extension class. `class` can either be the fully qualified name or the simple name stands for fully qualified class name. If the class name ends with `Factory` that suffix could optionally be left out. | - |  no
`aether.priority.implicit` | boolean | Flag indicating whether the priorities of pluggable extensions are implicitly given by their iteration order such that the first extension has the highest priority. If set, an extension's built-in priority as well as any corresponding `aether.priority.<class>` configuration properties are ignored when searching for a suitable implementation among the available extensions. This priority mode is meant for cases where the application will present/inject extensions in the desired search order. | `false` | no
`aether.snapshotFilter` | boolean | Flag whether the `ContextualSnapshotVersionFilter` should be forced to ban snapshots. By default, snapshots are only filtered if the root artifact is not a snapshot. | `false` | no
`aether.updateCheckManager.sessionState` | String | Manages the session state, i.e. influences if the same download requests to artifacts/metadata will happen multiple times within the same RepositorySystemSession. If `"enabled"` will enable the session state. If `"bypass"` will enable bypassing (i.e. store all artifact ids/metadata ids which have been updates but not evaluating those). All other values lead to disabling the session state completely. | `"enabled"` | no

All properties which have `yes` in the column `Supports Repo ID Suffix` can be optionally configured specifically for a repository id. In that case the configuration property needs to be suffixed with a period followed by the repository id of the repository to configure, e.g. `aether.connector.http.headers.central` for repository with id `central`.

## Property Type Conversion

If the value is not given in the target type the following conversions are applied.

From | To | With
--- | --- | ---
`String` | `boolean` | [`Boolean.parseBoolean(...)`](https://docs.oracle.com/javase/7/docs/api/java/lang/Boolean.html#parseBoolean(java.lang.String))
`String` | `int` | [`Integer.parseInt(...)`](https://docs.oracle.com/javase/7/docs/api/java/lang/Integer.html#parseInt(java.lang.String))
`String` | `long` | [`Long.parseLong(...)`](https://docs.oracle.com/javase/7/docs/api/java/lang/Long.html#parseLong(java.lang.String))
`String` | `float` | [`Float.parseFloat(...)`](https://docs.oracle.com/javase/7/docs/api/java/lang/Float.html#parseFloat(java.lang.String))

## Set Configuration from Apache Maven

To set one of the configuration options from above just use system variables. As system variables only support String values the type conversion mentioned above needs to be leveraged.
Sometimes Maven uses different default values than the Maven Resolver itself or tries to extract certain values from the `server.xml`. For details refer to <https://github.com/apache/maven/blob/master/maven-core/src/main/java/org/apache/maven/internal/aether/DefaultRepositorySystemSessionFactory.java>.
