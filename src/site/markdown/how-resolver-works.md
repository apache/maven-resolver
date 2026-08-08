# How Resolver Works
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

Maven Artifact Resolver (formerly Aether) is a central piece of Maven.
This document explains how Resolver works internally.
It also explains the main concepts and components of Resolver.

Resolver alone is incomplete.
Applications such as Maven provide the glue.
The glue includes the models and the logic to resolve versions and ranges and
to build effective models.
By itself, Resolver is unusable.
You must complement it with models and implementations of missing components.
The Maven module `org.apache.maven:maven-resolver-provider` completes Resolver.

## Core Concepts

**Artifacts** and **repositories** are at the core of Resolver.
An artifact is a symbolic coordinate backed by some content.
Usually it is a JAR, but it can be anything as long as Maven coordinates can
address it.
The Maven coordinates are
`<groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>`.
The default value of `extension` is `jar`.
The default value of `classifier` is an empty string.

Repositories are places where artifacts are stored and from where they can be
retrieved.
By default, Resolver operates with one local repository and zero or more remote
repositories.
The local repository is usually a directory on the local file system.

The term resolving is overloaded.
In general, it involves the following steps:

1. **Dependency graph collection** builds the dependency graph.
2. **Conflict resolution** removes conflicts, duplicates, and cycles from the
   graph. It produces the dependency tree.
3. **Flattening** transforms the tree into a flat list of artifacts. The list
   order represents the classpath order.
4. **Artifact resolution** resolves the actual artifact payload. It downloads
   and caches the payload when needed.

We call an artifact resolvable if it can be resolved from any available
repository.
The repository can be local or remote.
To make an artifact resolvable from the local repository, you must install it.
To make an artifact resolvable from a remote repository, you must deploy it.
This is an over-simplification.
Publishing is a new term, but it also involves the deploy step.

Extension points such as `WorkspaceReader` can make artifacts resolvable when
you do not install or deploy them.
This is an integration detail.
Maven does this when it exposes reactor projects.

### Dependency Graph Collection

Collection is the first step.
The caller usually provides the root artifact and the set of remote
repositories to use.
The output of the collection step is a **dependency graph**.
The graph is also known as the dirty graph.
It can contain cycles, conflicts, and duplicates.

Since Resolver 1.9.x, two collector implementations exist.
The legacy collector uses depth-first (DF) traversal.
The new collector uses breadth-first (BF) traversal.
The BF collector is the default now.
It offers better performance.

People constantly misunderstand the information used during graph collection.
Only certain parts of the effective model are used, not the whole POM.
Only the following aspects of the effective model are used:

* `project/dependencies` defines the direct dependencies on a given node.
* `project/dependencyManagement/dependencies` defines the dependency management
  for subsequent nodes.
* `project/repositories` defines the repositories to be used on subsequent
  nodes.

Read the API documentation for
`org.eclipse.aether.resolution.ArtifactDescriptorResult`.
This class is the peephole for Resolver to see the effective model.

Resolver 1.x ignored transitive dependency management by default.
Resolver 2.x changed this.
Transitive dependency management is enabled by default in Resolver 2.x.

These steps operate only on models.
Only POMs are resolved.
Their effective models are built during graph collection.

See also [common misconceptions](common-misconceptions.html).

### Conflict Resolution

Conflict resolution is the process that removes conflicts, duplicates, and
cycles from the dependency graph.
The result is the **dependency tree**.
Cycles are removed.

Resolver 2.x has two conflict resolver implementations.
The legacy implementation does multiple graph passes.
The path-based implementation does a single graph pass.
It is faster.
The winner selection strategy is pluggable since Resolver 2.x.

The nearest and highest strategies are available out of the box.
The version convergence and major version convergence strategies are also
available.
They are not enabled by default.
They are experimental.

This step operates only on the graph stored in memory.
There is no resolution of any kind.

### Flattening

Flattening is the process that transforms the tree into a flat list of
artifacts.
The list order becomes the classpath order.
Filtering is applied here as well.

Resolver historically used pre-order to flatten the tree into a list.
Resolver 2 offers three strategies.
They are pre-order, post-order, and level order.
Level order is the default.

This step operates only on the tree stored in memory.
There is no resolution of any kind.

### Artifact Resolution

Artifact resolution is the process that resolves the actual artifact content
from the local repository.
When needed, Resolver downloads and caches the content.

Dependency graph collection uses this step implicitly.
The collector asks for the artifact descriptor of each artifact during graph
collection.
The artifact descriptor is the effective model.
The artifact descriptor call enters the model builder.
During the build, the model builder resolves POMs as needed.
Examples are parent POMs, import POMs, and mixins.

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
