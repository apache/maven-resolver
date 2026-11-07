# Using Resolver in Maven Plugins
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

[Apache Maven](https://maven.apache.org/) 3.x uses Resolver (formerly Aether) for repository
tasks, and Maven plugins that target Maven 3.x can do so as well. To
start, add the following dependencies to the plugin POM:

```xml
<project>
  ...
  <prerequisites>
    <!-- Maven 3.1.0 is the earliest version using Eclipse Aether, Maven 3.0.x uses the incompatible predecessor Sonatype Aether -->
    <maven>3.1</maven>
  </prerequisites>

  <dependencies>
    <dependency>
      <!-- required in all cases -->
      <groupId>org.apache.maven.resolver</groupId>
      <artifactId>maven-resolver-api</artifactId>
      <version>1.9.27</version>
      <scope>provided</scope>
    </dependency>
    <dependency>
      <!-- optional helpers, might be superfluous depending on your use case -->
      <groupId>org.apache.maven.resolver</groupId>
      <artifactId>maven-resolver-util</artifactId>
      <version>1.9.27</version>
      <!-- Scope: use compile to make plugin work in Maven 3.8 and earlier -->
      <scope>compile</scope>
    </dependency>
    ...
  </dependencies>
  ...
</project>
```

*Note:* At runtime, the actual version of `maven-resolver-api` added to the classpath is
set by the Maven core so be sure to
compile/test your plugin against the version of `maven-resolver-api` that is
used by the minimum version of Maven that your plugin wants to support.

## Which Resolver version to compile against

Maven supplies the Resolver API at runtime, and the Maven lines ship
different Resolver lines:

| Maven | Resolver | Notes |
|---|---|---|
| 3.9.x | 1.9.x | Maven 3.9.12 bundles 1.9.25. |
| 3.10.x | 2.0.x | Maven 3.10.0 bundles 2.0.24. |
| 4.0.x | 2.0.x | Maven 4.0.0-rc-7 bundles 2.0.23. |

Compile against the Resolver line of the oldest Maven you support. A plugin
built against 2.0.x that calls a method 1.9.x does not have, such as
`Artifact#getPath()`, fails with `NoSuchMethodError` inside Maven 3.9.

### Plugins that support Maven 3.9 and later

Resolver 2.x deprecated every `java.io.File` method of the API in favour of
`java.nio.file.Path`, see [Upgrading Resolver](upgrading-resolver.html). The
`File` methods still exist in 2.x and delegate to the `Path` ones, while the
`Path` methods do not exist in 1.9.x. For a plugin that runs in Maven 3.9 as well as
in Maven 3.10 or 4:

* Compile against 1.9.x and keep calling `getFile()`, `setFile(File)` and
  `new LocalRepository(File)`. They work unchanged in Maven 3.10 and 4.
* Keep `maven-resolver-api` in `provided` scope and do not shade or bundle any
  Resolver module; the runtime version is always Maven's.
* Convert at the boundary: call `artifact.getFile().toPath()` where your own
  code wants a `Path`.
* When the plugin drops Maven 3.9 support, raise the dependency to 2.0.x and
  replace the `File` methods; the `-Xlint:deprecation` warnings point at each
  call site.

Everything deprecated in 1.9.x is removed in 2.x, so a plugin that compiles
against 1.9.x without deprecation warnings also loads in Maven 3.10 and 4.

Next, in your mojo source, you need to inject the repository related
components and parameters:

```java
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
...

public class MyMojo extends AbstractMojo
{

    /**
     * The current repository/network configuration of Maven.
     */
    @Parameter(defaultValue="${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repositorySystemSession;

    /**
     * The project's remote repositories to use for the resolution of project dependencies.
     */
    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true)
    private List<RemoteRepository> remoteProjectRepositories;

    /**
     * The project's remote repositories to use for the resolution of plugins and their dependencies.
     */
    @Parameter(defaultValue = "${project.remotePluginRepositories}", readonly = true)
    private List<RemoteRepository> remotePluginRepositories;


    /**
     * The entry point to the resolver (a.k.a. Aether); that is, the component doing all the work.
     */
    private final RepositorySystem repositorySystem;
    
    @Inject
    public MyMojo(RepositorySystem repositorySystem) {
        this.repositorySystem = repositorySystem;
    }
    
    // Your other mojo parameters and code here
    ...
}
```

Usually you only need one of `remoteProjectRepositories` or `remotePluginRepositories`,
depending on the nature of the artifacts your plugin is dealing with. The other plugin
parameter is superfluous. In general, the code
shown above should give you all the handles that you need to use the resolver
from within a Maven plugin.
