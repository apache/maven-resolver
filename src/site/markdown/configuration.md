# Configuration Options

Option | Type | Description | Default Value | Supports Repo ID Suffix
--- | --- | --- | --- | --- | --- | ---
`aether.priority.<class>` | Float | The priority to use for a certain extension class. `class` can either be the fully qualified name or the simple name stands for fully qualified class name. If the class name ends with `Factory` that suffix could optionally be left out. | - |  no
`aether.priority.implicit` | Boolean | A flag indicating whether the priorities of pluggable extensions are implicitly given by their iteration order such that the first extension has the highest priority. If set, an extension's built-in priority as well as any corresponding `aether.priority.<class>` configuration properties are ignored when searching for a suitable implementation among the available extensions. This priority mode is meant for cases where the application will present/inject extensions in the desired search order. | `false` | no
`aether.interactive` | Boolean | A flag indicating whether interaction with the user is allowed. | `false` | no
`aether.snapshotFilter` | Boolean | A flag whether the `ContextualSnapshotVersionFilter` should be forced to ban snapshots. By default, snapshots are only filtered if the root artifact is not a snapshot. | `false` | no
`aether.conflictResolver.verbose` | Boolean | A flag controlling the conflict resolver's verbose mode. | `false` | no
`aether.connector.userAgent` | String | The user agent that repository connectors should report to servers. |  `"Aether"` | no
`aether.connector.basic.threads` or `maven.artifact.threads` | Integer | Number of threads to  use for uploading/downloading. | `5` | no
`aether.connector.resumeDownloads` | Boolean | Whether to resume partially downloaded files if the download has been interrupted. | `true` | yes
`aether.connector.resumeThreshold` | Long | The size in bytes which a partial download needs to have at least to be resumed. Requires `aether.connector.resumeDownloads` to be `true` to be effective. | `64 * 1024` | yes
`aether.connector.requestTimeout` | Long | Request timeout in milliseconds. | `1800000` | yes
`aether.connector.connectTimeout` | Long | Connect timeout in milliseconds. | `10000` | yes
`aether.connector.wagon.config` | Object | The configuration to use for the Wagon provider. | - | yes (must be used)
`aether.connector.http.headers` | `Map<String, String>` | The request headers to use for HTTP-based repository connectors. The headers are specified using a map of Strings mapping a header name to its value. The repository-specific headers map is supposed to be complete, i.e. is not merged with the general headers map. | - | yes
`aether.connector.http.cacheState` | Boolean | A flag indicating whether a memory-based cache is used for user tokens, connection managers, expect continue requests and authentication schemes. | `true` | no
`aether.connector.http.credentialEncoding` | String | The encoding/charset to use when exchanging credentials with HTTP servers. | `"ISO-8859-1"` | yes
`aether.connector.https.cipherSuites` | String | Comma-separated list of [Cipher Suites](https://docs.oracle.com/javase/7/docs/technotes/guides/security/StandardNames.html#ciphersuites) which are enabled for HTTPS connections. | - (no restriction) | no
`aether.connector.https.protocols` | String | Comma-separated list of [Protocols](https://docs.oracle.com/javase/7/docs/technotes/guides/security/StandardNames.html#jssenames) which are enabled for HTTPS connections. | - (no restriction) | no
`aether.connector.perms.fileMode` | String | [Octal numerical notation of permissions](https://en.wikipedia.org/wiki/File_system_permissions#Numeric_notation) to set for newly created artifacts. Only considered by certain Wagon providers. | - | no
`aether.connector.perms.dirMode` | String | [Octal numerical notation of permissions](https://en.wikipedia.org/wiki/File_system_permissions#Numeric_notation) to set for newly created directories. Only considered by certain Wagon providers. | - | no
`aether.connector.perms.group` | String | The group which should own newly created directories/artifacts. Only considered by certain wagon providers. | - | no
`aether.connector.persistedChecksums` | Boolean | A flag indicating whether checksums which are retrieved during checksum validation should be persisted in the local filesystem next to the file they provide the checksum for. | `true` | no
`aether.connector.smartChecksums` | Boolean | A flag indicating that instead of comparing the explicit checksum from the remote repo with the calculated one it will try to extract it from the actual artifact requests's response header named `ETag` in format `SHA1{<checksum>}`. This only works for HTTP(S) requests and certain transport extensions. In addition it only supports SHA1. | `true` | no
`aether.connector.classpath.loader` | ClassLoader | `ClassLoader` from which resources should be retrieved which start with the `classpath:` protocol. | `Thread.currentThread().getContextClassLoader()` | no
`aether.checksums.forSignature` | Boolean | Flag indicating if signature artifacts (`.asc`) should have checksums. | `false` | no
`aether.checksums.algorithms` | String | List of [algorithms](https://docs.oracle.com/javase/7/docs/technotes/guides/security/StandardNames.html#MessageDigest) passed to [MessageDigest](hhttps://docs.oracle.com/javase/7/docs/api/java/security/MessageDigest.html) with which checksums are validated (downloaded) and generated (uploaded). | `"SHA-512,SHA-256,SHA-1,MD5"` | no
`aether.dependencyCollector.maxExceptions` | Integer | Only exceptions up to the number given in this configuration property are emitted. Exceptions which exceed that number are swallowed. | `50` | no
`aether.dependencyCollector.maxCycles` | Integer | Only up to the given amount cyclic dependencies are emitted. | `10` | no
`aether.dependencyManager.verbose` | Boolean | A flag controlling the verbose mode for dependency management. If enabled, the original attributes of a dependency before its update due to dependency managemnent will be recorded in the node's `DependencyNode#getData()` when building a dependency graph. | `false` | no
`aether.artifactResolver.snapshotNormalization` | Boolean | It replaces the the timestamped snapshot file name with a filename containing SNAPSHOT only. This only affects resolving/retrieving artifacts but not uploading those. | `true` | no
`aether.metadataResolver.threads` | Integer | Number of threads to use in parallel for resolving metadata. | `4` | no
`aether.enhancedLocalRepository.trackingFilename` | String | Filename of the file in which to track the remote repositories. | `"_remote.repositories"` | no
`aether.offline.protocols` | String | Comma-separated list of protocols which are supposed to be resolved offline. | - | no
`aether.offline.hosts` | String | Comma-separated list of hosts which are supposed to be resolved offline. | - | no
`aether.updateCheckManager.sessionState` | String | Manages the session state, i.e. influences if the same download requests to artifacts/metadata will happen multiple times within the same RepositorySystemSession. If `"true"` will enable the session state. If `"bypass"` will enable bypassing (i.e. store all artifact ids/metadata ids which have been updates but not evaluating those). All other values lead to disabling the session state completely. | `"true"` | no

All properties which have `yes` in the column `Supports Repo ID Suffix` can be optionally configured specifically for a repository id. In that case the configuration property needs to be suffixed with a comma followed by the repository id of the repository to configure, e.g. `aether.connector.http.headers.central` for repository with id `central`.


## Set configuration from Apache Maven

To set one of the configuration options from above just use system variables. As system variables only support String values a conversion to the target type is automatically performed with `Boolean.parseBoolean(...)`, `Integer.valueOf(...)`,`Long.valueOf(...)` or `Float.valueOf(...)`.

