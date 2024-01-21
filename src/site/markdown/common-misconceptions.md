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

Due to smooth transitions from Maven2 into Maven3 (and soon
Maven4), and the fact that Maven2 plugins kept working with Maven3, maybe
even without change, there were some misconceptions crept in 
as well. Despite the marvel of "compatibility", Maven3 resolution
differs quite much from Maven2, and the sole reason is actual improvement
in area of resolution, it became much more precise (and, due
that lost some "bad" habits present in Maven2). Here, we will try to
enumerate some of the most common misconceptions.

## Misconception No1: How Resolver Works

(Simplified)

The most typical use case for Resolver is to "resolve transitively" 
dependencies. Resolver, to achieve this, internally (but these are
exposed via API as distinguished API calls as well) performs 3 steps:
"collect", "transform" and "resolve".

The "collect" step is first, where it builds the "dirty tree" (dirty graph)
of artifacts. It is important to remark, that in "collect" step, while 
the graph is being built, Maven uses only POMs. Hence, if collecting an 
Artifact that was never downloaded to your local repository, it will 
download **the POMs only**. Using POMs resolver is able to build current 
"node" of graph, but also figure outgoing vertices and adjacent nodes of 
current node and so on. Which dependency is chosen to continue with from
the current node POM is decided by various criteria (configured).

The "transform" step transforms the "dirty graph": this is where conflict resolution
happens. It is here when resolver applies various rules to resolve conflicting 
versions, conflicting scopes, and so on. Here, if "verbose tree" is asked for,
conflict resolution does not remove graph nodes, merely marks the conflicts
and the conflict "winner". Thus, "verbose tree" cannot be resolved.

Finally, the "resolve" step runs, when the (transformed) graph node artifacts
are being resolved, basically ensuring (and downloading if needed) their 
correspondent files (i.e. JAR files) are present in local repository.

It is important to state, that in "collect" step happens the selection of nodes
by various criteria, among other by the configured scope filters. And here we
come to the notion of "runtime classpath" vs "test classpath". 

In resolver, maybe un-intuitively, the "scope filter" is usually used (but does 
not have to, this is just how it IS used in Maven Core, probably for historical
reasons) as "what should be omitted". The default session filter in Maven 
is set up as this:

```
  new ScopeDependencySelector("test", "provided")
```

This means, that "current dependency node" dependencies in "test" and "provided" scope
will be simply omitted from the graph. In other words, this filter builds
the "downstream runtime classpath" of supplied artifact (i.e. "what is needed by the 
artifact at runtime when I depend on it").

With selector like this:

```
  new ScopeDependencySelector("provided")
```

the "downstream dependency test classpath" would be built. Aside of giving example,
this selector is actually never used, as "test classpath" makes sense only in the
scope of "current project", but not for "downstream dependant projects".

Note: these are NOT "Maven related" notions yet, there is nowhere Maven in picture here,
and these are not the classpath used by Compiler or Surefire plugins, merely just
a showcase how Resolver works.


## Misconception No2: "Test classpath" Is Superset of "Runtime classpath"

**Wrong**. As can be seen from above, for runtime classpath we leave out "test" scoped
dependencies. It was true in Maven2, where test classpath really was superset of runtime, 
this does not stand anymore in Maven3. And this have interesting consequences. Let me show an example:

(Note: very same scenario, as explained below for Guice+Guava would work for Jackson Databind+Core, etc.)

Assume your project is using Google Guice, so you have declared it as a dependency:

```
      <dependency>
        <groupId>com.google.inject</groupId>
        <artifactId>guice</artifactId>
        <version>${guiceVersion}</version>
      </dependency>
```

All fine and dandy. At the same time, you want to avoid any use of Guava. We all know Guava is a direct dependency 
of Guice. This is fine, since as we know, the best practice is to declare all dependencies your code compiles 
against. By not having Guava here, analyse tools will report if code touches Guava as "undeclared dependency".

But let's go one step further: turns out, to set up your unit tests, you **do need** Guava. So what now? Nothing, just 
add it as a test dependency, so your POM looks like this:

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

And is right, this IS the "test classpath" **of the project** and contains a conflict as noted by "omitted for duplicate"
and "scope not updated to compile" remarks next to Guava nodes.

So this setup results that:
* when you compile, it ensures Guava is NOT on compile classpath, so you cannot even touch it (by mistake)
* when test-compile and test-execute runs, Guava will be present on classpath, as expected

So far good, but what happens when this library is consumed downstream by someone? When it becomes used as a library?
Nothing, all works as expected!

When a downstream dependency declares dependency on this project, the downstream project will get this graph (from
the node that is your library):

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

So what happens here? First, revisit "How Resolver Works", there you will see that for "runtime classpath" of the
dependency the "test" and "provided" scopes of the dependency artifact **are not even considered**. They are simply
omitted. Not skipped, but completely omitted, like they do not even exists. Hence, in the graph there is 
**no conflict happening** (as "test" Guava is completely omitted during "collect" step). Hence, everything 
goes as expected.

### Important Consequences

One, maybe not so obvious consequence can be explained with use of `maven-assembly-plugin`. Let assume you want to
assemble your module "runtime" dependencies.

If you do it from "within" of the project, for example in package phase, your packaging will be incomplete: 
Guava will be missing! But if you do it from "outside" of the project (i.e. subsequent module of the build, or 
downstream dependency), the assembly will contain Guava as well.

This is a [Maven Assembly plugin bug](https://issues.apache.org/jira/browse/MASSEMBLY-1008), somewhat explained 
in [MRESOLVER-391](https://issues.apache.org/jira/browse/MRESOLVER-391). In short, Maven Assembly plugin considers 
"project test classpath", and then "cherry-picks runtime scoped nodes" from it, which, as we can see in this case, 
is wrong. You need to build different graphs for "runtime" and "test" classpath, unlike as it was true in Maven2.
For Assembly plugin, the problem is that as Mojo, it requests "test classpath", then it reads configuration
(assembly descriptor, and this is the point where it learns about required scopes), and then it "filters"
the resolved "test classpath" by runtime scopes. And it is wrong, as Guava is in test scope. Instead, the plugin
should read the configuration first, and ask Resolver for "runtime classpath" and filter that. In turn, this problem
does not stand with `maven-war-plugin`, as the "war" Mojo asks for "compile+runtime" scope. Of course, WAR use case
is much simpler than Assembly use case is, as former always packages same scope, while Assembly receives a complex 
configuration and exposes much more complex "modus operandi".
