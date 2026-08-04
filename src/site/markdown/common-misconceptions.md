# Common Misconceptions

Maven2 plugins kept working with Maven3, often without change. Some misconceptions appeared because of this. Maven3 resolution differs from Maven2. The reason is improvement in the area of resolution. This document lists the most common misconceptions.

## Misconception 1: How Resolver Works

The most typical use case for Resolver is to resolve dependencies transitively. Resolver performs three steps to achieve this:

1. **Collect** - Resolver builds the dirty graph of artifacts.
2. **Transform** - Resolver resolves conflicts in the dirty graph.
3. **Resolve** - Resolver downloads the files for each graph node.

### The Collect Step

The collect step builds the dirty graph. Resolver uses only POMs during this step. If an artifact was never downloaded to your local repository, Resolver downloads only its POM file. Resolver uses the POM to build the current node of the graph. It also identifies outgoing vertices and adjacent nodes. Various criteria determine which dependency continues from the current node POM.

### The Transform Step

The transform step modifies the dirty graph. Conflict resolution happens during this step. Resolver applies rules to resolve:

- Conflicting versions
- Conflicting scopes

If you request a verbose tree, conflict resolution does not remove nodes. It marks the conflicts and the conflict winner. A verbose tree cannot be resolved.

### The Resolve Step

The resolve step runs after transformation. Resolver ensures that the file for each node artifact exists in the local repository. If a file is missing, Resolver downloads it.

### Scope Filters

The collect step uses scope filters to select nodes. Resolver applies these filters during selection. Maven Core uses scope filters as "what to omit". The default session filter in Maven is:

```
  new ScopeDependencySelector("test", "provided")
```

This filter omits dependencies in "test" and "provided" scope. It builds the downstream runtime classpath of the supplied artifact.

Note: These are not Maven-related notions. Maven is not involved here. These are not the classpaths used by Compiler or Surefire plugins. They show how Resolver works.

## Misconception 2: "Test graph" Is Superset Of "Runtime graph"

**Wrong**. The runtime graph omits test-scoped dependencies. Maven2 had a test graph that was a superset of the runtime graph. This is not true in Maven3. This has consequences.

### Example

Assume your project uses Google Guice. You declare it as a dependency:

```
      <dependency>
        <groupId>com.google.inject</groupId>
        <artifactId>guice</artifactId>
        <version>${guiceVersion}</version>
      </dependency>
```

You want to avoid any use of Guava. Guava is a direct dependency of Guice. Do not declare Guava as a dependency. This ensures that analysis tools report any Guava usage as an undeclared dependency.

Your unit tests need Guava. Add Guava as a test dependency:

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

The `dependency:tree` plugin for this project outputs:

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

This is the test graph of the project. It contains a conflict. The "omitted for duplicate" and "scope not updated to compile" remarks confirm the conflict.

This setup has the following results:

- When you compile, Guava is not on the compile classpath. You cannot touch it by mistake.
- When test-compile and test-execute run, Guava is present on the classpath.

### Downstream Usage

When a downstream project depends on your library, it gets this graph:

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

For the runtime graph of the dependency, the test and provided scopes are not considered. They are omitted during the collect step. No conflict occurs in the graph.

### Important Consequences

You can see one consequence when you use `maven-assembly-plugin`. Assume you want to assemble the runtime dependencies of your module.

If you run the assembly from within the project, for example during the package phase, the packaging is incomplete. Guava will be missing. If you run the assembly from outside the project, the assembly contains Guava.

This is a Maven Assembly plugin bug. The bug is explained in MRESOLVER-391. The Maven Assembly plugin considers the project test graph. It then selects runtime-scoped nodes from this graph. This approach is wrong. You must build different graphs for the runtime and test classpaths.

The plugin requests the test graph. It then reads the assembly descriptor. It learns about the required scopes at this point. It filters the resolved test graph for runtime scopes. This is wrong because Guava has test scope. The plugin must read the configuration first. It must ask Resolver for the runtime graph and filter that.

The `maven-war-plugin` does not have this problem. The war Mojo asks for resolution of the compile+runtime scope. The WAR use case is simpler than the Assembly use case. The WAR Mojo always packages the same scope. The Assembly Mojo receives a complex configuration.
