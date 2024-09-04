# Using Resolver in Maven Plugins
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

[Apache Maven](http://maven.apache.org/) 3.x uses Resolver (former Aether) for repository
tasks and Maven plugins that target Maven 3.x can do so as well. To
start, you likely want to add the following dependencies to your plugin
POM:

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
      <version>1.9.20</version>
      <scope>provided</scope>
    </dependency>
    <dependency>
      <!-- optional helpers, might be superfluous depending on your use case -->
      <groupId>org.apache.maven.resolver</groupId>
      <artifactId>maven-resolver-util</artifactId>
      <version>1.9.20</version>
      <!-- Scope: use compile to make plugin work in Maven 3.8 and earlier -->
      <scope>compile</scope>
    </dependency>
    ...
  </dependencies>
  ...
</project>
```

*Note:* At runtime, the actual version of `maven-resolver-api` being used is
enforced by the Maven core, just like other Maven APIs. So be sure to
compile/test your plugin against the version of `maven-resolver-api` that is
used by the minimum version of Maven that your plugin wants to support.

Next, in your mojo source, you would need to grab the repository related
components and parameters:

```java
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
...

public class MyMojo extends AbstractMojo
{

    /**
     * The entry point to resolver (fka. Aether), i.e. the component doing all the work.
     */
    @Component
    private RepositorySystem repoSystem;

    /**
     * The current repository/network configuration of Maven.
     */
    @Parameter(defaultValue="${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repoSession;

    /**
     * The project's remote repositories to use for the resolution of project dependencies.
     */
    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true)
    private List<RemoteRepository> projectRepos;

    /**
     * The project's remote repositories to use for the resolution of plugins and their dependencies.
     */
    @Parameter(defaultValue = "${project.remotePluginRepositories}", readonly = true)
    private List<RemoteRepository> pluginRepos;

    // Your other mojo parameters and code here
    ...
}
```

Usually, you need only `projectRepos` or `pluginRepos` depending on the
nature of artifacts your plugin is dealing with, so the other plugin
parameter would be superfluous in that case. But in general, the bits
shown above should give you all handles that you need to work with
Aether from within a Maven plugin.
