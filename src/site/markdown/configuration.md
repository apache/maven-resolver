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
--- | --- | --- | --- | ---
`aether.artifactResolver.snapshotNormalization` | boolean | It replaces the timestamped snapshot file name with a filename containing the `SNAPSHOT` qualifier only. This only affects resolving/retrieving artifacts but not uploading those. | `true` | no
`aether.artifactResolver.simpleLrmInterop` | boolean | Enable interop with Simple LRM. Ignored when RRF used. | `false` | no
`aether.artifactResolver.postProcessor.trustedChecksums` | boolean | Enable `trustedChecksums` resolver post processor. | `false` | no 
`aether.artifactResolver.postProcessor.trustedChecksums.checksumAlgorithms` | String | Comma-separated list of checksum algorithms with which `trustedChecksums` should operate (validate or record). | `"SHA-1"` | no 
`aether.artifactResolver.postProcessor.trustedChecksums.failIfMissing` | boolean | Makes `trustedChecksums` fail validation if a trusted checksum for an artifact is missing. | `false` | no 
`aether.artifactResolver.postProcessor.trustedChecksums.record` | boolean | Makes `trustedChecksums` calculate and record checksums. | `false` | no 
`aether.artifactResolver.postProcessor.trustedChecksums.snapshots` | boolean | Enables or disables snapshot processing in `trustedChecksums` post processor. | `false` | no 
`aether.checksums.omitChecksumsForExtensions` | String | Comma-separated list of extensions with leading dot (example `.asc`) that should have checksums omitted. These are applied to sub-artifacts only. Note: to achieve 1.7.x `aether.checksums.forSignature=true` behaviour, pass empty string as value for this property. | `.asc` | no
`aether.checksums.algorithms` | String | Comma-separated list of checksum algorithms with which checksums are validated (downloaded) and generated (uploaded). Resolver by default supports following algorithms: `MD5`, `SHA-1`, `SHA-256` and `SHA-512`. New algorithms can be added by implementing `ChecksumAlgorithmFactory` component. | `"SHA-1,MD5"` | no
`aether.conflictResolver.verbose` | boolean | Flag controlling the conflict resolver's verbose mode. | `false` | no
`aether.connector.basic.threads` or `maven.artifact.threads` | int | Number of threads to use for uploading/downloading. | `5` | no
`aether.connector.basic.parallelPut` | boolean | Enables or disables parallel PUT processing (parallel deploys) on basic connector globally or per remote repository. When disabled, connector behaves exactly as in Maven 3.8.x did: GETs are parallel while PUTs are sequential. | `true` | yes
`aether.connector.classpath.loader` | ClassLoader | `ClassLoader` from which resources should be retrieved which start with the `classpath:` protocol. | `Thread.currentThread().getContextClassLoader()` | no
`aether.connector.connectTimeout` | long | Connect timeout in milliseconds. | `10000` | yes
`aether.connector.http.cacheState` | boolean | Flag indicating whether a memory-based cache is used for user tokens, connection managers, expect continue requests and authentication schemes. | `true` | no
`aether.connector.http.credentialEncoding` | String | The encoding/charset to use when exchanging credentials with HTTP servers. | `"ISO-8859-1"` | yes
`aether.connector.http.headers` | `Map<String, String>` | The request headers to use for HTTP-based repository connectors. The headers are specified using a map of strings mapping a header name to its value. The repository-specific headers map is supposed to be complete, i.e. is not merged with the general headers map. | - | yes
`aether.connector.http.preemptiveAuth` | boolean | Should HTTP client use preemptive-authentication for all HTTP verbs (works only w/ BASIC). By default is disabled, as it is considered less secure. | `false` | yes
`aether.connector.http.preemptivePutAuth` | boolean | Should HTTP client use preemptive-authentication for HTTP PUTs only (works only w/ BASIC). By default is enabled (same as Wagon). | `true` | yes
`aether.connector.http.retryHandler.count` | int | The maximum number of times a request to a remote HTTP server should be retried in case of an error. | `3` | yes
`aether.connector.http.supportWebDav` | boolean | If enabled, transport makes best effort to deploy to WebDAV server. This mode is not recommended, better use real Maven Repository Manager instead. | `false` | yes
`aether.connector.http.useSystemProperties` | boolean | If enabled, underlying Apache HttpClient will use system properties as well to configure itself (typically used to set up HTTP Proxy via Java system properties). See <a href="https://hc.apache.org/httpcomponents-client-4.5.x/current/httpclient/apidocs/org/apache/http/impl/client/HttpClientBuilder.html">HttpClientBuilder</a> for used properties. This mode is **not recommended**, better use documented ways of configuration instead. | `false` | yes
`aether.connector.https.cipherSuites` | String | Comma-separated list of [Cipher Suites](https://docs.oracle.com/javase/7/docs/technotes/guides/security/StandardNames.html#ciphersuites) which are enabled for HTTPS connections. | - (no restriction) | no
`aether.connector.https.securityMode` | String | Using this flag resolver may set the "security mode" of HTTPS connector. Any other mode than 'default' is NOT MEANT for production, as it is inherently not secure. Accepted values: "default", "insecure" (ignore any kind of certificate validation errors and hostname validation checks). | `"default"` | yes
`aether.connector.https.protocols` | String | Comma-separated list of [Protocols](https://docs.oracle.com/javase/7/docs/technotes/guides/security/StandardNames.html#jssenames) which are enabled for HTTPS connections. | - (no restriction) | no
`aether.connector.perms.fileMode` | String | [Octal numerical notation of permissions](https://en.wikipedia.org/wiki/File_system_permissions#Numeric_notation) to set for newly created files. Only considered by certain Wagon providers. | - | no
`aether.connector.perms.dirMode` | String | [Octal numerical notation of permissions](https://en.wikipedia.org/wiki/File_system_permissions#Numeric_notation) to set for newly created directories. Only considered by certain Wagon providers. | - | no
`aether.connector.perms.group` | String | Group which should own newly created directories/files. Only considered by certain Wagon providers. | - | no
`aether.connector.persistedChecksums` | boolean | Flag indicating whether checksums which are retrieved during checksum validation should be persisted in the local filesystem next to the file they provide the checksum for. | `true` | no
`aether.connector.requestTimeout` | long | Request timeout in milliseconds. | `1800000` | yes
`aether.connector.smartChecksums` | boolean | Flag indicating that instead of comparing the external checksum fetched from the remote repo with the calculated one, it should try to extract the reference checksum from the actual artifact requests's response headers (several (strategies supported)[included-checksum-strategies.html]). This only works for transport-http transport. | `true` | no
`aether.connector.userAgent` | String | The user agent that repository connectors should report to servers. |  `"Aether"` | no
`aether.connector.wagon.config` | Object | The configuration to use for the Wagon provider. | - | yes (must be used)
`aether.dependencyCollector.maxCycles` | int | Only up to the given amount cyclic dependencies are emitted. | `10` | no
`aether.dependencyCollector.maxExceptions` | int | Only exceptions up to the number given in this configuration property are emitted. Exceptions which exceed that number are swallowed. | `50` | no
`aether.dependencyCollector.impl` | String | The name of the dependency collector implementation to use: depth-first (original) named `df`, and breadth-first (new in 1.8.0) named `bf`. Both collectors produce equivalent results, but they may differ performance wise, depending on project being applied to. Our experience shows that existing `df` is well suited for smaller to medium size projects, while `bf` may perform better on huge projects with many dependencies. Experiment (and come back to us!) to figure out which one suits you the better. | `"df"` | no
`aether.dependencyCollector.bf.skipper` | boolean | Flag controlling whether to skip resolving duplicate/conflicting nodes during the breadth-first (`bf`) dependency collection process. | `true` | no
`aether.dependencyCollector.bf.threads` or `maven.artifact.threads` | int | Number of threads to use for collecting POMs and version ranges in BF collector. | `5` | no
`aether.dependencyCollector.pool.artifact` | String | Flag controlling interning data pool type used by dependency collector for Artifact instances, matters for heap consumption. By default uses "weak" references (consume less heap). Using "hard" will make it much more memory aggressive and possibly faster (system and Java dependent). Supported values: `"hard"`, `"weak"`. | `"weak"` | no
`aether.dependencyCollector.pool.dependency` | String | Flag controlling interning data pool type used by dependency collector for Dependency instances, matters for heap consumption. By default uses "weak" references (consume less heap). Using "hard" will make it much more memory aggressive and possibly faster (system and Java dependent). Supported values: `"hard"`, `"weak"`. | `"weak"` | no
`aether.dependencyCollector.pool.descriptor` | String | Flag controlling interning data pool type used by dependency collector for Artifact Descriptor (POM) instances, matters for heap consumption. By default uses "hard" references (consume more heap, but is faster). Using "weak" will make resolver much more memory conservative, at the cost of up to 10% slower collecting dependency speed (system and Java dependent). Supported values: `"hard"`, `"weak"`. | `"hard"` | no
`aether.dependencyManager.verbose` | boolean | Flag controlling the verbose mode for dependency management. If enabled, the original attributes of a dependency before its update due to dependency managemnent will be recorded in the node's `DependencyNode#getData()` when building a dependency graph. | `false` | no
`aether.enhancedLocalRepository.localPrefix` | String | The prefix to use for locally installed artifacts. | `"installed"` | no
`aether.enhancedLocalRepository.snapshotsPrefix` | String | The prefix to use for snapshot artifacts. | `"snapshots"` | no
`aether.enhancedLocalRepository.split` | boolean | Whether LRM should split local and remote artifacts. | `false` | no
`aether.enhancedLocalRepository.splitLocal` | boolean | Whether locally installed artifacts should be split by version (release/snapshot). | `false` | no
`aether.enhancedLocalRepository.splitRemote` | boolean | Whether cached artifacts should be split by version (release/snapshot). | `false` | no
`aether.enhancedLocalRepository.splitRemoteRepository` | boolean | Whether cached artifacts should be split by origin repository (repository ID). | `false` | no
`aether.enhancedLocalRepository.splitRemoteRepositoryLast` | boolean | For cached artifacts, if both `splitRemote` and `splitRemoteRepository` are set to `true` sets the splitting order: by default it is repositoryId/version (false) or version/repositoryId (true) | `false` | no
`aether.enhancedLocalRepository.remotePrefix` | String | The prefix to use for downloaded and cached artifacts. | `"cached"` | no
`aether.enhancedLocalRepository.releasesPrefix` | String | The prefix to use for release artifacts. | `"releases"` | no
`aether.enhancedLocalRepository.trackingFilename` | String | Filename of the file in which to track the remote repositories. | `"_remote.repositories"` | no
`aether.interactive` | boolean | A flag indicating whether interaction with the user is allowed. | `false` | no
`aether.metadataResolver.threads` | int | Number of threads to use in parallel for resolving metadata. | `4` | no
`aether.offline.protocols` | String | Comma-separated list of protocols which are supposed to be resolved offline. | - | no
`aether.offline.hosts` | String | Comma-separated list of hosts which are supposed to be resolved offline. | - | no
`aether.priority.<class>` | float | The priority to use for a certain extension class. `class` can either be the fully qualified name or the simple name stands for fully qualified class name. If the class name ends with `Factory` that suffix could optionally be left out. | - |  no
`aether.priority.implicit` | boolean | Flag indicating whether the priorities of pluggable extensions are implicitly given by their iteration order such that the first extension has the highest priority. If set, an extension's built-in priority as well as any corresponding `aether.priority.<class>` configuration properties are ignored when searching for a suitable implementation among the available extensions. This priority mode is meant for cases where the application will present/inject extensions in the desired search order. | `false` | no
`aether.remoteRepositoryFilter.groupId` | boolean | Enable `groupId` remote repository filter. | `false` | no
`aether.remoteRepositoryFilter.groupId.basedir` | String | The basedir path for `groupId` filter. If relative, resolved against local repository root, if absolute, used as is. | `".remoteRepositoryFilters"` | no 
`aether.remoteRepositoryFilter.groupId.record` | boolean | Enable recording of `groupId` filter. | `false` | no
`aether.remoteRepositoryFilter.groupId.truncateOnSave` | boolean | When recoding session done, should `groupId` filter truncate (overwrite) the output file with newly recorded data, or, if file exists, load-merge-save it? | `false` | no
`aether.remoteRepositoryFilter.prefixes` | boolean | Enable `prefixes` remote repository filter. | `false` | no
`aether.remoteRepositoryFilter.prefixes.basedir` | String | The basedir path for `prefixes` filter. If relative, resolved against local repository root, if absolute, used as is. | `".remoteRepositoryFilters"` | no 
`aether.snapshotFilter` | boolean | Flag whether the `ContextualSnapshotVersionFilter` should be forced to ban snapshots. By default, snapshots are only filtered if the root artifact is not a snapshot. | `false` | no
`aether.syncContext.named.basedir.locksDir` | String | The basedir path for file named locks. If relative, resolved against local repository root, if absolute, used as is. | `".locks"` | no
`aether.syncContext.named.factory` | String | Name of the named lock factory implementing the `org.eclipse.aether.named.NamedLockFactory` interface. | `"rwlock-local"` | no
`aether.syncContext.named.hashing.depth` | int | The directory depth to "spread" hashes in git-like fashion, integer between 0 and 4 (inclusive). | 2 | no
`aether.syncContext.named.nameMapper` | String | Name of name mapper implementing the `org.eclipse.aether.internal.impl.synccontext.named.NameMapper` interface. | `"gav"` | no
`aether.syncContext.named.time` | long | Amount of time a synchronization context shall wait to obtain a lock. | 30 | no
`aether.syncContext.named.time.unit` | long | Unit of the lock wait time. | `"SECONDS"` | no
`aether.syncContext.named.discriminating.discriminator` | String | A discriminator name prefix identifying a Resolver instance. | `"sha1('${hostname:-localhost}:${maven.repo.local}')"` or `"sha1('')"` if generation fails | no
`aether.syncContext.named.discriminating.hostname` | String | The hostname to be used with discriminating mapper. | Detected with `InetAddress.getLocalHost().getHostName()` | no
`aether.syncContext.named.redisson.configFile` | String | Path to a Redisson configuration file in YAML format. Read [official documentation](https://github.com/redisson/redisson/wiki/2.-Configuration) for details. | none or `"${maven.conf}/maven-resolver-redisson.yaml"` if present | no
`aether.trustedChecksumsSource.sparseDirectory` | boolean | Enable `sparseDirectory` trusted checksum source. | `false` | no
`aether.trustedChecksumsSource.sparseDirectory.basedir` | String | The basedir path for `sparseDirectory` trusted checksum source. If relative, resolved against local repository root, if absolute, used as is. | `".checksums"` | no
`aether.trustedChecksumsSource.sparseDirectory.originAware` | boolean | Is trusted checksum source origin aware (factors in Repository ID into path) or not. | `true` | no
`aether.trustedChecksumsSource.summaryFile` | boolean | Enable `summaryFile` trusted checksum source. | `false` | no
`aether.trustedChecksumsSource.summaryFile.basedir` | String | The basedir path for `summaryFile` trusted checksum source. If relative, resolved against local repository root, if absolute, used as is. | `".checksums"` | no
`aether.trustedChecksumsSource.summaryFile.originAware` | boolean | Is trusted checksum source origin aware (factors in Repository ID into path) or not. | `true` | no
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
Sometimes Maven uses different default values than the Maven Resolver itself or tries to extract certain values from the `settings.xml`. For details refer to <https://github.com/apache/maven/blob/master/maven-core/src/main/java/org/apache/maven/internal/aether/DefaultRepositorySystemSessionFactory.java>.
