# Common Misconceptions
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

Maven 2 plugins kept working with Maven 3 although Maven 3 resolution differs from Maven 2 resolution. Resolution is now much more precise. Some old behaviors from Maven 2 no longer exist. This page lists some of the most common misconceptions.

## Misconception Number 1: How Resolver Works

(Simplified)

The most common use of Resolver is to resolve dependencies transitively. Resolver performs three steps: "collect", "transform", and "resolve". Resolver also exposes these steps as separate API calls.

The "collect" step is first. During this step, Resolver builds the "dirty tree" (or dirty graph) of artifacts. While it builds the graph, Maven only uses POMs. If an artifact is not in your local repository, Maven downloads the POM only. With the POM, Resolver builds the current node of the graph and finds its outgoing vertices and adjacent nodes. The configured criteria decide which dependency continues from the current node POM.

The "transform" step transforms the "dirty graph". This is where conflict resolution happens. Resolver applies rules to resolve conflicting versions and conflicting scopes. If you ask for the "verbose tree", conflict resolution does not remove graph nodes. It only marks the conflicts and the conflict "winner". Therefore, the "verbose tree" cannot be resolved.

Finally, in the "resolve" step, Resolver resolves the artifacts of the transformed graph nodes. It ensures that the corresponding files (for example, JAR files) are present in the local repository. It downloads them if needed.

During the "collect" step, various criteria select the nodes. The configured scope filters are among these criteria. This leads to the notion of the "runtime graph" and the "test graph".

The "scope filter" selects what to omit. This use of the filter is not intuitive. Maven Core uses the filter this way. Maven Core does not have to use it this way. The default session filter in Maven is set up as follows:

```
  new ScopeDependencySelector("test", "provided")
```

This filter omits the dependencies of the "current dependency node" that are in the "test" and "provided" scope. In other words, this filter builds the "downstream runtime classpath" of the supplied artifact. It shows what the artifact needs at runtime when you depend on it.

Note: These notions do not relate to Maven yet. Maven does not appear in this example. This is not the classpath that the Compiler or Surefire plugins use. It is only a showcase of how Resolver works.

## Misconception Number 2: "Test graph" is a Superset of the "Runtime graph"

**Wrong**. For the runtime graph, Resolver omits the "test" scoped dependencies. In Maven 2, the test graph was really a superset of the runtime graph. This is no longer true in Maven3. This has interesting consequences. The example below shows this.

The example below uses Guice and Guava.
Your project uses Google Guice. You have declared Guice as a dependency:

```
      <dependency>
        <groupId>com.google.inject</groupId>
        <artifactId>guice</artifactId>
        <version>${guiceVersion}</version>
      </dependency>
```

Now you want to avoid any use of Guava. Guava is a direct dependency of Guice. The best practice is to declare all dependencies that your code compiles against. If you do not declare Guava, the analysis tools report it as an "undeclared dependency."

Your unit tests need Guava. You add Guava as a test dependency. Your POM then looks like this:

```
      <dependency>
        <groupId>com.google.inject</groupId>
        <artifactId>guice</artifactId>
        <version>${guiceVersion}</version>
      </dependency>
      <dependency>
        <groupId>com.google.guava</groupId>
        <artifactId>guava</artifactId>
        <version>${guavaVersion}</version>
        <scope>test</scope>
      </dependency>
```

The `dependency:tree` plugin for this project outputs this verbose tree:

```
[INFO] --- dependency:3.6.1:tree (default-cli) @ DEMO-PROJECT ---
[INFO] DEMO-PROJECT
[INFO] +- com.google.inject:guice:jar:6.0.0:compile
[INFO] |  +- javax.inject:javax.inject:jar:1:compile
[INFO] |  +- jakarta.inject:jakarta.inject-api:jar:2.0.1:compile
[INFO] |  +- aopalliance:aopalliance:jar:1.0:compile
[INFO] |  \- (com.google.guava:guava:jar:31.0.1-jre:compile - omitted for duplicate)
[INFO] \- com.google.guava:guava:jar:31.0.1-jre:test (scope not updated to compile)
[INFO]    +- com.google.guava:failureaccess:jar:1.0.1:test
[INFO]    +- com.google.guava:listenablefuture:jar:9999.0-empty-to-avoid-conflict-with-guava:test
[INFO]    +- com.google.code.findbugs:jsr305:jar:3.0.2:test
[INFO]    +- org.checkerframework:checker-qual:jar:3.12.0:test
[INFO]    +- com.google.errorprone:error_prone_annotations:jar:2.7.1:test
[INFO]    \- com.google.j2objc:j2objc-annotations:jar:1.3:test
```

This is the "test graph" of the project. It contains a conflict. The tree shows the remarks "omitted for duplicate" and "scope not updated to compile" next to the Guava nodes.

This setup has these results:

* When you compile, Guava is not on the compile classpath. You cannot touch it by mistake.
* When test-compile and test-execute run, Guava is present on the classpath, as expected.

What happens when someone consumes this library downstream? Nothing. Everything works as expected.

When a downstream project declares a dependency on your project, it gets this graph from the node of your library:

```
[INFO] --- dependency:3.6.1:tree (default-cli) @ DOWNSTREAM-PROJECT ---
[INFO] DOWNSTREAM-PROJECT
[INFO] \- DEMO-PROJECT:compile
[INFO]    \- com.google.inject:guice:jar:6.0.0:compile
[INFO]       +- javax.inject:javax.inject:jar:1:compile
[INFO]       +- jakarta.inject:jakarta.inject-api:jar:2.0.1:compile
[INFO]       +- aopalliance:aopalliance:jar:1.0:compile
[INFO]       \- com.google.guava:guava:jar:31.0.1-jre:compile
[INFO]          +- com.google.guava:failureaccess:jar:1.0.1:compile
[INFO]          +- com.google.guava:listenablefuture:jar:9999.0-empty-to-avoid-conflict-with-guava:compile
[INFO]          +- com.google.code.findbugs:jsr305:jar:3.0.2:compile
[INFO]          +- org.checkerframework:checker-qual:jar:3.12.0:compile
[INFO]          +- com.google.errorprone:error_prone_annotations:jar:2.7.1:compile
[INFO]          \- com.google.j2objc:j2objc-annotations:jar:1.3:compile
```

What happens here? The section "How Resolver Works" above explains this. For the "runtime graph" of the dependency, Resolver does not consider the "test" and "provided" scopes of the dependency artifact. Resolver omits them completely. It does not skip them. They do not exist in the graph.

Therefore, no conflict happens. Resolver omits the "test" Guava during the "collect" step. Everything goes as expected.

### Important Consequences

One consequence is not so obvious. It involves `maven-assembly-plugin`. You want to assemble the "runtime" dependencies of your module.

If you assemble from within the project, for example in the package phase, the packaging will be incomplete. Guava will be missing. If you assemble from outside the project, the assembly will contain Guava. This includes assembly from a subsequent module of the build or from a downstream dependency.

This is a [Maven Assembly plugin bug](https://issues.apache.org/jira/browse/MASSEMBLY-1008). [MRESOLVER-391](https://issues.apache.org/jira/browse/MRESOLVER-391) explains it in part. The Maven Assembly plugin considers the "test graph" of the project. Then it "cherry-picks" the runtime scoped nodes from the graph. This is wrong in this case.

You must build different graphs for the "runtime" and "test" classpath. The Assembly plugin is a Mojo. It requests the "test graph". Then it reads the configuration (the assembly descriptor). It learns the required scopes at this point. Then it "filters" the resolved "test graph" for the runtime scopes.

This is wrong, because Guava is in the test scope. The plugin must read the configuration first. Then it must ask Resolver for the "runtime graph". Then it must filter the graph. This problem does not exist with `maven-war-plugin`. The "war" Mojo asks for the "compile+runtime" scope.

The WAR use case is much simpler than the Assembly use case. The WAR plugin always packages the same scope. The Assembly plugin receives a complex configuration. The way the Assembly plugin works is also much more complex.
