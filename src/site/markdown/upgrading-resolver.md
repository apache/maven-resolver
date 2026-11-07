# Upgrading to Resolver 2.x
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

Resolver 1.9.x is the line that Maven 3.9.x ships. Maven 3.10 and Maven 4 ship Resolver 2.x.
Both lines are maintained, so code that runs inside Maven keeps the line of the
Maven version it runs in.

| Your code runs in | Compile against | Notes |
|---|---|---|
| Maven 3.9.x | Resolver 1.9.x | Maven 3.9.16 bundles 1.9.27. |
| Maven 3.10.x | Resolver 2.0.x | Maven 3.10.0 bundles 2.0.24. |
| Maven 4.0.x | Resolver 2.0.x | Maven 4.0.0-rc-7 bundles 2.0.23. |
| Your own application | Resolver 2.0.x | Resolver 2.x has no service locator; its supplier modules bootstrap it. |

This page lists what code built on 1.9.x should change before it moves to 2.x.
Every replacement below already exists on 1.9.x, so the work can be done and
released on this line first. The 2.x side of the move, bootstrapping without
`ServiceLocator`, the `java.io.File` to `java.nio.file.Path` renames, the new
module coordinates and the renamed configuration keys, is covered by the
[Upgrading Resolver](https://maven.apache.org/resolver/upgrading-resolver.html)
page of the 2.x site.

## Remove the deprecated usages first

Everything that 1.9.x marks `@Deprecated` is removed in 2.x. Build with
`-Xlint:deprecation` enabled and replace each usage:

| Deprecated in 1.9.x, removed in 2.x | Replacement |
|---|---|
| `org.eclipse.aether.spi.log.Logger`, `LoggerFactory`, `NullLoggerFactory` | SLF4J. Resolver 2.x logs through SLF4J only. |
| `org.eclipse.aether.spi.locator.ServiceLocator`, `Service`, `org.eclipse.aether.impl.DefaultServiceLocator`, `org.eclipse.aether.impl.guice.AetherModule` | Dependency injection (Eclipse Sisu, Guice), or the `maven-resolver-supplier` module, which 2.x continues as `maven-resolver-supplier-mvn3` and `maven-resolver-supplier-mvn4`. |
| No-argument constructors of components, such as `new DefaultRepositorySystem()`, `new BasicRepositoryConnectorFactory()`, `new HttpTransporterFactory()`, `new WagonTransporterFactory()` | Constructor injection. Obtain components from the injector or from `RepositorySystemSupplier`. |
| `org.eclipse.aether.transform.FileTransformer`, `FileTransformerManager`, `TransformException`, `RepositorySystemSession#getFileTransformerManager()` | Removed in 2.x without a direct replacement. Install and deploy time changes go through the 2.x `ArtifactTransformer` and `ArtifactGeneratorFactory` SPIs. |
| `org.eclipse.aether.spi.connector.checksum.ProvidedChecksumsSource` | `org.eclipse.aether.spi.checksums.ProvidedChecksumsSource` |
| `org.eclipse.aether.impl.SyncContextFactory` | `org.eclipse.aether.spi.synccontext.SyncContextFactory` |
| `org.eclipse.aether.util.StringUtils` | Removed. Use your own helper or the JDK. |
| `TransferResource(String repositoryUrl, String resourceName, File file, RequestTrace trace)` | The constructor that also takes the repository id. |
| `ChecksumFailureException(String expected, String actual)` | `ChecksumFailureException(String expected, String expectedKind, String actual)` |
| `DefaultMirrorSelector#add` with six arguments | The overload that also takes the `blocked` flag. |
| `ChecksumUtils#read(File)`, `ChecksumUtils#calc(...)` | `FileProcessor` for checksum files and `ChecksumAlgorithmFactorySelector` for digests, both in the SPI module. |

## Maven plugins that must run in Maven 3.9 and later

Maven supplies the Resolver API at runtime. A plugin built against 2.0.x that
calls a method 1.9.x does not have, such as `Artifact#getPath()`, fails with
`NoSuchMethodError` inside Maven 3.9. For a plugin that runs in Maven 3.9 as well as
in Maven 3.10 or 4:

* Compile against 1.9.x and keep calling `getFile()`, `setFile(File)` and
  `new LocalRepository(File)`. Resolver 2.x deprecates them but keeps them, so
  they work unchanged in Maven 3.10 and 4.
* Keep `maven-resolver-api` in `provided` scope and do not shade or bundle any
  Resolver module; the runtime version is always Maven's.
* Convert at the boundary: call `artifact.getFile().toPath()` where your own
  code wants a `Path`.
* When the plugin drops Maven 3.9 support, raise the dependency to 2.0.x and
  replace the `File` methods; the `-Xlint:deprecation` warnings point at each
  call site.

A plugin that compiles against 1.9.x without deprecation warnings also loads in
Maven 3.10 and 4.
