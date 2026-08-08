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
an artifact from its coordinates and adding it to the Maven build.
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
Artifacts that are not in repositories also be resolved through extension points such as `WorkspaceReader`.
Maven does this when it exposes reactor projects, for example.
Normally you don't need to think about this.
</aside>

### Dependency Graph Collection

Collection is the first step.
Resolver adds a root artifact to the list.
TODO: breadth first or depth first????
Then it looks at the dependencies of that root artifact and adds them to the list.
Then it looks at the dependencies of each of those dependencies and adds each of those 
it has not already seen to the list.  
The output of the collection step is a **dependency graph** known as the *dirty graph*.
It can contain conflicts and duplicates.

Since Resolver 1.9.x, two collector implementations exist.
The legacy collector uses depth-first (DF) traversal.
The new collector uses breadth-first (BF) traversal.
The BF collector is the default now.
It offers better performance.

WTF???? This is a huge incompatible change that will break projects. 

During collection, only certain parts of the effective model are used, not the whole POM.

* `project/dependencies` defines the direct dependencies on a given node.
* `project/dependencyManagement/dependencies` defines the dependency management
  for subsequent nodes.
* `project/repositories` defines the repositories to be used on subsequent
  nodes.


TODO: what is an artifact descriptor?

TODO: likely don't need this
Read the API documentation for
`org.eclipse.aether.resolution.ArtifactDescriptorResult`.
This class is the peephole for Resolver to see the effective model.

TODO: what is transitive dependency management and do we want to talk about it here?
Resolver 1.x ignored transitive dependency management by default.
Resolver 2.x changed this.
Transitive dependency management is enabled by default in Resolver 2.x.

During the collection step, Resolver downloads pom.xml file from remote repositories.
It does not yet download binary JAR files or other artifacts.

See also [common misconceptions](common-misconceptions.html).

### Conflict Resolution

Conflict resolution removes conflicts, duplicates, and cycles from the dependency graph.
The result is the **dependency tree**.

Resolver 2.x has two conflict resolution implementations.
The legacy implementation does multiple graph passes.
The faster path-based implementation does a single graph pass.
TODO: do they give the same result?
The winner selection strategy is pluggable since Resolver 2.x.

The nearest and highest strategies are available out of the box.
The version convergence and major version convergence strategies are also
available.
They are experimental and not enabled by default.

This step operates entirely in memory.
The resolver does not download anything.

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
in the local repository. If it isn't, then Resolver downloads the file
from a remote repository and caches it in the local repository.

----

In general, Resolver performs dependency graph collection and conflict
resolution together.
The name for this operation is dependency collection.
Resolver also usually performs flattening and artifact resolution together.
The name for this pair is artifact resolution.
When all the steps are performed together, the process is called dependency
resolution.
The Resolver API reflects this terminology and offers methods for collection,
resolution, or both.

* `CollectResult collectDependencies(RepositorySystemSession session, CollectRequest request)` performs only the collection step. Only the collection and conflict resolution steps are performed.
* `List<ArtifactResult> resolveArtifacts(RepositorySystemSession session, Collection<? extends ArtifactRequest> requests)` performs only the artifact resolution step.
* `DependencyResult resolveDependencies(RepositorySystemSession session, DependencyRequest request)` performs both the collection and resolution steps.

Each subsequent step depends on the previous one.
For example, a dirty graph cannot be flattened because it can contain cycles.
What you want depends on your use case.
To investigate the dependency graph, you can collect the dirty graph.
You can skip conflict resolution.
