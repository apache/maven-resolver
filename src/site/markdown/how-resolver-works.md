# How Resolver Works
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

Maven Artifact Resolver (formerly Aether) is a central piece of Maven.
This document explains how Resolver works internally.
It also explains the main concepts and components of Resolver.

Resolver alone is incomplete. It needs an application such as 
Maven to resolve versions and build effective models.
The Maven module `org.apache.maven:maven-resolver-provider` complements it with
models and implementations of missing components.


## Core Concepts

**Artifacts** and **repositories** are at the core of Resolver.
An *artifact* is a binary resource with Maven coordinates.
Usually it is a JAR file, but it can be anything as long as Maven coordinates can
address it.
The Maven coordinates are
`<groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>`.
The default value of `extension` is `jar`.
The default value of `classifier` is an empty string.

A *repository* is a place where artifacts are stored and from where they can be
retrieved.
By default, Resolver operates with one local repository and zero or more remote
repositories.
The local repository is usually a directory on the local file system.
Remote repositories are usually HTTP servers.

*Resolving* is the process of finding
*Resolving* is the process of locating an artifact from its coordinates and obtaining its content.
It involves the following steps:

1. **Dependency graph collection** builds the dependency graph.
2. **Conflict resolution** removes conflicts, duplicates, and cycles from the
   graph. It produces the dependency tree.
3. **Flattening** transforms the tree into a list of artifacts. The list
   order represents the classpath order.
4. **Artifact resolution** finds each artifact in the flattened list in one of the available repositories. If necessary, it downloads
   the artifact from a remote repository and adds it to the local repository.

We call an artifact *resolvable* if it can be resolved from any available
repository.
The repository can be local or remote.
To make an artifact resolvable from the local repository, you install it.
To make an artifact resolvable from a remote repository, you deploy it.

<aside>
Artifacts that are not in repositories can also be resolved through extension points such as `WorkspaceReader`.
Maven does this when it exposes reactor projects, for example.
Normally you don't need to think about this.
</aside>

### Dependency Graph Collection

Collection is the first step.
Resolver adds a root artifact to a graph.
Then it adds the dependencies of the root artifact to the graph.
Then it adds the dependencies of the dependencies, and so on.
It stops when there are no more dependencies that haven't been added to the graph. 
The output of the collection step is a *dependency graph* known as the *dirty graph*.
It can contain conflicts, duplicates, and cycles.

The exact procedure for building the dirty graph isn't important as long as 
it ends with the same graph.
Starting in Resolver 1.9.x, there are two collector implementations.
The legacy collector traverses in depth-first order.
The new collector traverses in breadth-first (BF) order.
The BF collector is faster and is now the default.

During the collection step, Resolver downloads pom.xml files from remote repositories.
It does not yet download binary JAR files or other artifacts.

During collection, only certain parts of the effective model are used, not the whole POM.

* `project/dependencies` defines the direct dependencies of a given node.
* `project/dependencyManagement/dependencies` defines the dependency management
  for subsequent nodes.
* `project/repositories` defines the repositories to be used on subsequent
  nodes.

In Resolver 1.x, `project/dependencyManagement` only defines versions of dependencies
for its own pom.xml. It does not affect the versions of the dependencies of the dependencies.
(*transitive dependencies*).

In Resolver 2.x, `project/dependencyManagement` does define versions of dependencies
for the transitive dependencies.

See also [common misconceptions](common-misconceptions.html).

### Conflict Resolution

Conflict resolution removes conflicts, duplicates, and cycles from the dependency graph.
The result is the **dependency tree**. This step operates entirely in memory.
The resolver does not download anything.

Resolver 2.x has two conflict resolution implementations.
The legacy implementation does multiple graph passes.
The faster path-based implementation does a single graph pass.

The strategy for selecting winners is pluggable in Resolver 2.x.
Nearest and highest strategies are available out of the box.
Experimental version convergence and major version convergence strategies are also
available, but these are not enabled by default.

### Flattening

Flattening transforms the tree into a list of artifacts.
The list order becomes the classpath order.
Filtering is applied here as well.

Resolver historically used pre-order to flatten the tree into a list.
Resolver 2 offers three strategies: pre-order, post-order, and level order.
Level order is the default.

This step operates entirely in memory.
The resolver does not download anything.

### Artifact Resolution

Artifact resolution checks to see if the binary artifact resource, most commonly a JAR file, is
in the local repository. If it isn't, Resolver downloads the file
from a remote repository and caches it in the local repository.

----

In general, Resolver builds the dependency graph and resolves conflicts in that graph in the same pass.
The name for this operation is *dependency collection*.

Resolver also usually resolves artifacts as it builds the flattened list.
The name for this combined step is *artifact resolution*.

When all the steps are performed together, the process is called *dependency
resolution*.

Yes, this is an unfortunate overload of terminology. 

The Resolver API reflects this terminology and offers methods for collection,
resolution, or both.

* `CollectResult collectDependencies(RepositorySystemSession session, CollectRequest request)` performs the dependency collection step. It builds the dependency graph and resolves conflicts in that graph before returning.
* `List<ArtifactResult> resolveArtifacts(RepositorySystemSession session, Collection<? extends ArtifactRequest> requests)` performs the artifact resolution step. It builds a flattened list and downloads artifacts before returning.
* `DependencyResult resolveDependencies(RepositorySystemSession session, DependencyRequest request)` performs both the collection and resolution steps.

Each step depends on the previous one.
For example, a dirty graph cannot be flattened because it can contain cycles.

While a standard Maven build will perform all these steps, 
you can use them independently if you're doing something else.
For example, if you're looking for linkage errors, you can collect the dirty graph for inspection
and skip conflict resolution.
