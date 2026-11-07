# Upgrading Resolver (major versions)
<!---
 Licensed to the Apache Software Foundation (ASF) under one or more
 contributor license agreements.  See the NOTICE file distributed with
 this work for additional information regarding copyright ownership.
 The ASF licenses this file to You under the Apache License, Version 2.0
 (the "License"); you may not use this file except in compliance with
 the License.  You may obtain a copy of the License at

      https://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->

This page collects quick guides for developers upgrading from one major
version of Resolver to another.

# Upgrading from 1.x to 2.x

Resolver 2.x is the line that Maven 3.10 and Maven 4 ship. Maven 3.9.x stays on Resolver 1.9.x.
Both lines are maintained, so pick the line that matches the Maven version your
code runs in. A Maven plugin that must run in Maven 3.9 as well as in Maven 3.10 or 4 has its
own guide: [Using Resolver in Maven Plugins](using-resolver-in-maven-plugins.html).

| Your code runs in | Compile against | Notes |
|---|---|---|
| Maven 3.9.x | Resolver 1.9.x | Maven 3.9.12 bundles 1.9.25. |
| Maven 3.10.x | Resolver 2.0.x | Maven 3.10.0 bundles 2.0.24. |
| Maven 4.0.x | Resolver 2.0.x | Maven 4.0.0-rc-7 bundles 2.0.23. |
| Your own application | Resolver 2.0.x | Use a supplier module to bootstrap, see below. |

The API, SPI and Util modules of 2.x stay binary compatible with 1.9.x for code
that does not use anything deprecated in 1.9.x. Everything that 1.9.x marks
`@Deprecated` is gone in 2.x. The rest of this page walks through the changes in
the order you will meet them.

## Step 1: Clean up on the latest 1.9.x first

Build against the latest 1.9.x release with `-Xlint:deprecation` enabled and
replace every deprecated usage. Each item below has a replacement that already
exists on 1.9.x, so this step does not require 2.x.

| Deprecated in 1.9.x, removed in 2.x | Replacement |
|---|---|
| `org.eclipse.aether.spi.log.Logger`, `LoggerFactory`, `NullLoggerFactory` | SLF4J. Resolver 2.x logs through SLF4J only. |
| `org.eclipse.aether.spi.locator.ServiceLocator`, `Service`, `org.eclipse.aether.impl.DefaultServiceLocator`, `org.eclipse.aether.impl.guice.AetherModule` | Dependency injection or a supplier module, see step 2. |
| No-argument constructors of components, such as `new DefaultRepositorySystem()`, `new BasicRepositoryConnectorFactory()`, `new HttpTransporterFactory()`, `new WagonTransporterFactory()` | Components use constructor injection only. Obtain them from the injector or from `RepositorySystemSupplier`. |
| `org.eclipse.aether.transform.FileTransformer`, `FileTransformerManager`, `TransformException`, `RepositorySystemSession#getFileTransformerManager()` | Removed without a direct replacement. Maven 4 builds its consumer POM itself. For install and deploy time changes implement the `org.eclipse.aether.spi.artifact.transformer.ArtifactTransformer` SPI (since 2.0.5). To add generated artifacts such as signatures implement `org.eclipse.aether.spi.artifact.generator.ArtifactGeneratorFactory`. |
| `org.eclipse.aether.spi.connector.checksum.ProvidedChecksumsSource` | `org.eclipse.aether.spi.checksums.ProvidedChecksumsSource` |
| `org.eclipse.aether.impl.SyncContextFactory` | `org.eclipse.aether.spi.synccontext.SyncContextFactory` |
| `org.eclipse.aether.util.StringUtils` | Removed. Use your own helper or the JDK. |
| `TransferResource(String repositoryUrl, String resourceName, File file, RequestTrace trace)` | The constructor that also takes the repository id. In 2.x the file argument is a `Path`. |
| `ChecksumFailureException(String expected, String actual)` | `ChecksumFailureException(String expected, String expectedKind, String actual)` on 1.9.x. On 2.x prefer the factory methods `mismatch`, `noneAvailable` and `processingFailure`. |
| `DefaultMirrorSelector#add` with six arguments | The overload that also takes the `blocked` flag. |
| `ChecksumUtils#read(File)`, `ChecksumUtils#calc(...)` | `ChecksumProcessor` and `ChecksumAlgorithmFactorySelector` from the SPI module. On 2.x the whole `ChecksumUtils` class is deprecated; `StringDigestUtil` covers the string digests. |

## Step 2: Bootstrap without the service locator

Inside Maven nothing changes: inject `RepositorySystem` and take the session from
the `repositorySystemSession` parameter, as the [plugin guide](using-resolver-in-maven-plugins.html) shows.

Outside Maven the service locator is gone. The 1.9.x `maven-resolver-supplier`
module became two modules, both exposing `org.eclipse.aether.supplier.RepositorySystemSupplier`
and `org.eclipse.aether.supplier.SessionBuilderSupplier`:

| Module | Scope semantics | Java |
|---|---|---|
| `maven-resolver-supplier-mvn3` | Maven 3: `compile`, `provided`, `runtime`, `test`, `system`, with `test` and `provided` dropped transitively | 8 |
| `maven-resolver-supplier-mvn4` | Maven 4: the Maven 4 build scopes and resolution scopes | 17 |

Choose `mvn3` when the application must resolve exactly like Maven 3 did, for
example a tool that compares its result with `dependency:tree`. Choose `mvn4`
when it should behave like Maven 4. The `mvn3` module also carries the deprecated
`org.eclipse.aether.spi.locator` interfaces as linkage shims, so Maven 3 code that
references them still loads; Resolver never calls them.

```java
RepositorySystem system = new RepositorySystemSupplier().get();
RepositorySystemSession.SessionBuilder builder = new SessionBuilderSupplier(system)
        .get()
        .withLocalRepositoryBaseDirectories(Paths.get("target/local-repo"));
try (RepositorySystemSession.CloseableSession session = builder.build()) {
    // use system and session
}
system.shutdown();
```

Both suppliers are plain classes you can subclass to swap a component, which is
what `DefaultServiceLocator#setServices` used to do. Applications that already
use Eclipse Sisu or Guice keep injecting `RepositorySystem`: the components carry
`javax.inject` annotations and a Sisu index, and `AetherModule` is not needed.

## Session handling changes

Maven Resolver 2.x introduced "onSessionEnd" hooks, that became required for
some of the new features (like HTTP/2 transports are). While the existing "Resolver 1.x"
way of handling sessions still works, it may produce resource leaks.
Client code **managing Resolver** (like Maven) is strongly advised to upgrade
session handling. Client code **using Resolver** (like Maven Mojos)
does not have to change anything, it continues to function in the very same way
as before (as with Resolver 1.x).

What changed on surface:
* introduction of `RepositorySystemSession` nested interfaces `CloseableSession` and `SessionBuilder`.
* introduction of `RepositorySystem` new method `createSessionBuilder` that creates `SessionBuilder` instances.
* deprecation of `DefaultRepositorySystemSession` default constructor, this constructor is actually the "Resolver 1.x way" of using sessions.

Required changes in **consumer project code managing Resolver 2.x**:
* do not use `DefaultRepositorySystemSession` default constructor anymore.
* instead, use `RepositorySystem#createSessionBuilder` to create `SessionBuilder` and out of it `CloseableSession` instances.
* handle sessions as resources: each created instance should be closed once finished their use.
* session instances created by given `RepositorySystem` should be used only with that same instance.
* to shallow-copy session instances (for alteration purposes) using existing `DefaultRepositorySystemSession` copy constructor is acceptable (this is what Mojos do).
* to shallow-copy session instances but have new lifecycle as well, use `SessionBuilder#withRepositorySystemSession` on newly created builder instances.

## Step 3: Move from File to Path

Resolver 2.0.0 switched its API, SPI and Util modules from `java.io.File` to
`java.nio.file.Path`. Every `File` method is still there and deprecated, delegating
to the `Path` one, so existing binaries keep working. Replace them when you move
to 2.x; the table lists the renames.

| Module | Deprecated | Replacement |
|---|---|---|
| API | `Artifact#getFile()`, `Artifact#setFile(File)` | `getPath()`, `setPath(Path)` |
| API | `Metadata#getFile()`, `Metadata#setFile(File)`, `MergeableMetadata#merge(File, File)` | `getPath()`, `setPath(Path)`, `merge(Path, Path)` |
| API | `DefaultMetadata` constructors taking `File` | The same constructors taking `Path` |
| API | `LocalRepository(String)`, `LocalRepository(File)`, `LocalRepository(File, String)`, `LocalRepository#getBasedir()` | `LocalRepository(Path)`, `LocalRepository(Path, String)`, `getBasePath()` |
| API | `LocalArtifactResult` and `LocalMetadataResult` `getFile()`, `setFile(File)` | `getPath()`, `setPath(Path)` |
| API | `LocalRepositoryManager#getPathForLocalArtifact` and the three sibling `getPathFor*` methods returning `String` | `getAbsolutePathForLocalArtifact` and siblings returning `Path` |
| API | `RepositoryEvent#getFile()`, `RepositoryEvent.Builder#setFile(File)` | `getPath()`, `setPath(Path)` |
| API | `TransferResource#getFile()`, `TransferResource#getTransferStartTime()`, the constructor taking `File` | `getPath()`, `getStartTime()`, the constructor taking `Path` |
| SPI | `ArtifactDownload`, `ArtifactUpload`, `MetadataDownload`, `MetadataUpload` constructors taking `File`, and `setFile(File)` on them and on `ArtifactTransfer`, `MetadataTransfer` | The `Path` constructors, `setPath(Path)`, `getPath()` |
| SPI | `GetTask#getDataFile()`, `GetTask#setDataFile(...)`, `PutTask#getDataFile()`, `PutTask#setDataFile(File)` | `getDataPath()`, `setDataPath(...)` |
| SPI | `FileProcessor` | `PathProcessor` for file operations, `ChecksumProcessor` for checksum files |
| Util | `NodeListGenerator#getFiles()` | `getPaths()` |

A plugin that must still run on Maven 3.9 cannot call `getPath()`, because Maven
3.9 ships Resolver 1.9.x without it. Keep `getFile()` there and suppress the
warning; the plugin guide explains the options.

## Step 4: Replace the deprecated Util helpers

| Deprecated in 2.x | Replacement |
|---|---|
| `PreorderNodeListGenerator`, `PostorderNodeListGenerator` | `PreorderDependencyNodeConsumerVisitor` and `PostorderDependencyNodeConsumerVisitor` feeding a `NodeListGenerator`; `LevelOrderDependencyNodeConsumerVisitor` matches the order Resolver 2.x uses for class paths. |
| `NearestVersionSelector` | `ConfigurableVersionSelector`, which also offers the "highest" strategy. |
| `PredicateVersionFilter` | `ArtifactPredicateVersionFilter` |
| `ScopeDependencySelector`, `OptionalDependencySelector` in `org.eclipse.aether.util.graph.selector` | The selectors a supplier session already carries, or `ScopeManager#getDependencySelector(session, resolutionScope)`. Scope semantics belong to the consumer project in 2.x. |
| `ContextualSnapshotVersionFilter#CONFIG_PROP_ENABLE` (`aether.snapshotFilter`) | Add a `SnapshotVersionFilter` to the session to ban snapshots. |
| `ChecksumUtils`, `FileUtils`, `ExecutorUtils` | `StringDigestUtil`, the `org.eclipse.aether.spi.io` processors, and the JDK. Resolver no longer uses these classes. |
| `RepositoryPolicy#getUpdatePolicy()` | `getArtifactUpdatePolicy()` and `getMetadataUpdatePolicy()`. Resolver 2.x keeps the two policies apart; the old method returns the artifact one. |
| `ArtifactResult#addException(Exception)` | `addException(ArtifactRepository, Exception)`, so the failure is attributed to a repository. |
| `UnsolvableVersionConflictException(Collection)` | `UnsolvableVersionConflictException(String, Collection)` with a message. |
| `BasicRepositoryConnectorConfigurationKeys#CONFIG_PROP_THREADS` (`aether.connector.basic.threads`) | `CONFIG_PROP_UPSTREAM_THREADS` and `CONFIG_PROP_DOWNSTREAM_THREADS` |

## Consumer Project Changes (if more needed)

Resolver 2.x delegates the Maven specific parts of dependency handling to the
consumer project. Resolver itself treats dependency scopes as labels; the meaning
of `compile`, `test`, or any other scope comes from the `ScopeManager` the
session carries, and the two supplier modules configure it for Maven 3 and Maven 4
semantics. The same applies to the `system` scope, which has its own
`SystemDependencyScope` handler on the session.

The Maven 3 era helpers stay available and are not deprecated: `JavaScopes`, the
`ArtifactProperties` constants, `DefaultArtifactType`, and `DependencyFilterUtils`
with its class path filters. They remain the right choice for code that must
behave like Maven 3, and the `mvn3` supplier builds on them. Code that targets
Maven 4 should take scopes, types and class path logic from Maven's own
`maven-resolver-provider` and API instead, so that it agrees with the build it runs in.

## Step 5: Update module coordinates

| Resolver 1.9.x | Resolver 2.x |
|---|---|
| `maven-resolver-transport-http` (Apache HttpClient 4, package `org.eclipse.aether.transport.http`) | `maven-resolver-transport-apache` (Apache HttpClient 4, package `org.eclipse.aether.transport.apache`, factory `ApacheTransporterFactory`). The connector configuration keys moved, see step 6. |
| `maven-resolver-supplier` | `maven-resolver-supplier-mvn3` or `maven-resolver-supplier-mvn4`, see step 2. |
| `maven-resolver-transport-wagon`, `maven-resolver-transport-file`, `maven-resolver-transport-classpath` | Unchanged coordinates; the configuration keys moved to the `aether.transport.*` namespace. |
| `maven-resolver-named-locks-hazelcast` | Deprecated, support will be dropped. Use the default file locks, `maven-resolver-named-locks-ipc` (Java 17) for a lock daemon shared between JVMs, or `maven-resolver-named-locks-redisson` (Java 11). |

New in 2.x, all optional:

* `maven-resolver-transport-jdk`: HTTP/1.1 and HTTP/2 through `java.net.http`, a multi-release jar that is defunct on Java 8 and active on Java 11 and later. HTTP/3 since 2.0.21 on JDKs that support it.
* `maven-resolver-transport-jetty`: HTTP/2 and HTTP/3 through Jetty, Java 17.
* `maven-resolver-transport-url`: a small `java.net.URL` transport, Java 8.
* `maven-resolver-transport-minio`: native S3 transport.
* `maven-resolver-generator-gnupg` and `maven-resolver-generator-sigstore`: sign artifacts at deploy time, Java 17.

When more than one HTTP transport is on the class path the highest priority wins:
Jetty (15), then JDK (10), then Apache (5). The Wagon and URL transports have a
negative priority and only serve when nothing else can. Set the `aether.priority.*`
properties to change the order.

## Step 6: Rename configuration keys

The 2.x keys live under `aether.transport.*` for transports and `aether.lrm.*`,
`aether.named.*`, `aether.system.*` for the rest. The full, generated list with
defaults and the version each key appeared in is on the
[Configuration Options](configuration.html) page. The renames below are the ones a
1.9.x `settings.xml`, `.mvn/maven.config` or session setup is likely to contain.

Resolver 2.x still reads the old key for the rows marked "yes" in the last column.
Every other old key is ignored silently, so grep your configuration for
`aether.connector.` and `aether.enhancedLocalRepository.` before upgrading.

| Resolver 1.9.x key | Resolver 2.x key | Old key still read |
|---|---|---|
| `aether.connector.connectTimeout` | `aether.transport.http.connectTimeout` | no |
| `aether.connector.requestTimeout` | `aether.transport.http.requestTimeout` | no |
| `aether.connector.userAgent` | `aether.transport.http.userAgent` | yes |
| `aether.connector.http.headers` (and `.headers.<repoId>`) | `aether.transport.http.headers` | no |
| `aether.connector.http.preemptiveAuth`, `.preemptivePutAuth` | `aether.transport.http.preemptiveAuth`, `.preemptivePutAuth` | no |
| `aether.connector.http.reuseConnections`, `.connectionMaxTtl`, `.maxConnectionsPerRoute` | `aether.transport.http.reuseConnections`, `.connectionMaxTtl`, `.maxConnectionsPerRoute` | no |
| `aether.connector.http.expectContinue`, `.credentialEncoding`, `.supportWebDav` | `aether.transport.http.expectContinue`, `.credentialEncoding`, `.supportWebDav` | no |
| `aether.connector.http.retryHandler.count`, `.interval`, `.intervalMax`, `.serviceUnavailable` | `aether.transport.http.retryHandler.count`, `.interval`, `.intervalMax`, `.serviceUnavailable` | no |
| `aether.connector.http.retryHandler.name`, `.requestSentEnabled` | `aether.transport.apache.retryHandler.name`, `.requestSentEnabled` | no |
| `aether.connector.http.followRedirects`, `.maxRedirects`, `.useSystemProperties` | `aether.transport.apache.followRedirects`, `.maxRedirects`, `.useSystemProperties` | no |
| `aether.connector.bind.address` | `aether.transport.http.localAddress` | no |
| `aether.connector.http.cacheState` | Removed. | no |
| `aether.connector.https.securityMode` | `aether.transport.https.securityMode` | no |
| `aether.connector.https.cipherSuites`, `.protocols` | `aether.transport.apache.https.cipherSuites`, `.protocols` | no |
| `aether.connector.wagon.config` | `aether.transport.wagon.config` | no |
| `aether.connector.perms.fileMode`, `.dirMode`, `.group` | `aether.transport.wagon.perms.fileMode`, `.dirMode`, `.group` | no |
| `aether.connector.classpath.loader` | `aether.transport.classpath.loader` | no |
| `aether.connector.persistedChecksums` | `aether.connector.basic.persistedChecksums` | no |
| `aether.connector.smartChecksums` | `aether.connector.basic.includedChecksums` | yes |
| `aether.connector.basic.threads` | `aether.connector.basic.upstreamThreads` and `.downstreamThreads` | yes, deprecated |
| `aether.checksums.algorithms` | `aether.checksums.checksumAlgorithms` | yes |
| `aether.enhancedLocalRepository.*` (`split`, `splitLocal`, `splitRemote`, `localPrefix`, `remotePrefix`, `releasesPrefix`, `snapshotsPrefix`, `splitRemoteRepository`, `splitRemoteRepositoryLast`, `trackingFilename`) | `aether.lrm.enhanced.*` with the same suffixes | yes |
| `aether.syncContext.named.time`, `.time.unit` | `aether.system.named.time`, `.timeUnit` | yes, deprecated |
| `aether.remoteRepositoryFilter.groupId.truncateOnSave` | Removed. | no |

## Behaviour changes to expect

These defaults changed between 1.9.x and 2.0.x. None of them needs a code change,
but each can change what a build resolves or how fast it runs. The
[Resolver 2.0.x Notable Features](release-highlights.html) page has the full list.

* The breadth-first collector is the default; `aether.dependencyCollector.impl=df` restores the old one.
* Named locks default to file locks with the GAECV name mapper.
* Remote repository filtering is on by default.
* Artifact and metadata update policies are separate.
* `WarnChecksumPolicy` proceeds when a repository serves no checksums at all.
* Class path order follows the level-order graph visitor.
