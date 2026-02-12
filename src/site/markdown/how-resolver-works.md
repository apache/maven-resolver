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

Maven Artifact Resolver (former Aether) is a central piece of Maven.
This document tries to  explain how resolver works under the hood, and explain the main concepts
and building blocks of Resolver.

Resolver alone is "incomplete". Integrating application, like Maven is
the one that provides the "glue" (models) and logic how to resolve versions, ranges, build effective models.
By itself, Resolver is unusable. One needs to complement it with models and implementations of missing 
components. Historically, the Maven module completing Resolver is `org.apache.maven:maven-resolver-provider`.



## Core Concepts

At the core of Resolver are **artifacts** and **repositories**. An artifact is basically a 
"symbolic coordinate" of some content. Usually it is a JAR, but it can be really anything, as long as it is
"addressable" using Maven coordinates: `<groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>` (default value of
`extension` is `jar`, and default value for `classifier` is `""`, empty string). Repositories
are places where artifacts are stored and from where they can be retrieved. Resolver, by default operates
with one local repository (usually a directory on local filesystem) and zero or more remote repositories.

The term "resolving" is a bit overloaded, but in general it involves following steps:
* **dependency graph collection** builds the "dependency graph"
* **conflict resolution** makes the graph free of cycles, conflicts and duplicates, resulting in "dependency tree"
* **flattening** transforms the tree into a flat list of artifacts, which also represents classpath ordering
* **artifact resolving** is the process of resolving (downloading and caching, if needed) the actual artifact payload

We call an artifact "resolvable" if it can be resolved from any available (local or remote) repository. To make artifact
"resolvable" from the local repository, one needs to "install" it. To make an artifact "resolvable" from a remote repository, 
one needs to "deploy" it (this is over-simplification; publishing is new term, but it also involves deploy step).
Furthermore, there are extension points like `WorkspaceReader` that can make artifacts resolvable 
without installing or deploying them, but that is an integration detail (like Maven does by exposing reactor projects).

### Dependency Graph Collection

Collecting is the first step. Caller usually provides the root artifact along with the set of remote repositories to use.
Output of collection is **dependency graph**; a.k.a. "dirty graph" that may contain cycles, conflicts, 
duplicates and the like.

Since Resolver 1.9.x there are two collector implementations: the legacy DepthFirst (DF) and the new BreadthFirst (BF) collector.
The BF collector is now the default, as it offers better performance.

One very important thing, that is constantly misunderstood, is what information is used during graph collection. 
Only certain parts of the effective model are used, not the whole POM.
If am allowed to overly simplify, only the following aspects of the effective model are used during graph collection:
* `project/dependencies` as direct dependencies on given node
* `project/dependencyManagement/dependencies` for **subsequent** dependency management on given node
* `project/repositories` for **subsequent** repositories to be used on given node

For more, check out `org.eclipse.aether.resolution.ArtifactDescriptorResult` class, as that is the "peephole" for Resolver
to see effective model.

Another important detail is that Resolver 1.x by default ignored transitive dependency management. This changed in
Resolver 2.x where transitive dependency management is enabled by default.

These steps operate only on models. Only POMs are resolved, and their effective models are built during
graph collection.

See also [common misconceptions](common-misconceptions.html).

### Conflict Resolution

Conflict resolution is the process of removing conflicts, duplicates and cycles from the dependency graph, resulting 
in the **dependency tree** (as cycles are removed).

Resolver 2.x has two conflict resolver implementations: "legacy" (doing multiple graph passes) and new, 
faster "path based" (doing single graph pass) conflict resolver. Winner selection strategy is also pluggable since 
Resolver 2.x. Out of the box "nearest" and "highest" strategies are available (extras as "version convergence" and
"major version convergence" are available as well, but not available by default; experimental).

This step operates only on the graph stored in memory. There is no resolution of any kind.

### Flattening

Flattening is the process of transforming the tree into a flat list of artifacts. This list order becomes the classpath order.
This is where filtering is applied as well.

Resolver historically used "pre-order" to flatten the tree into a list, but Resolver 2 offers three strategies: "pre-order", 
"post-order", and "level order" (the default). These strategies are currently fixed, but in the future we may offer a more 
flexible API for custom flattening strategies.

This step operates only on the tree stored in memory. There is no resolution of any kind.

### Artifact Resolution

Artifact resolution is the process of resolving (downloading and caching, if needed) the actual artifact payload from 
local repository.

This step is implicitly used by the Dependency Graph Resolution step as well, as collector
will ask for artifact descriptors (effective model) of each artifact during graph collection, and artifact descriptor calls
into model builder, that in turn, during building, resolve POMs as needed (parent POMs, import POMs, mixins, etc).

----

In general, the steps "dependency graph collection" and "conflict resolution" are performed together. The name we give that operation is "dependency collection".
The "flattening" and "artifact resolving" are also usually done together, and we use for those the term "artifact resolution".
To make the story more confusing, when all the steps are performed together is also called "dependency resolution".
The Resolver API reflects this terminology and offers methods doing collection, resolution or both.
* 
* The method `CollectResult collectDependencies(RepositorySystemSession session, CollectRequest request)` performs only the collection step,
as its name suggests. Hence, only the steps "collection" and "conflict resolution" are performed.
* Method `List<ArtifactResult> resolveArtifacts(RepositorySystemSession session, Collection<? extends ArtifactRequest> requests)`
performs only the artifact resolving step.
* Method `DependencyResult resolveDependencies(RepositorySystemSession session, DependencyRequest request)` performs both 
collection and resolution steps.

Also, each subsequent step depends on the previous one. For example a "dirty graph" cannot be flattened (due possible cycles).
Many times, what you want depends on your use case. For example, to investigate dependency graph, one may 
want to collect the dirty graph, but not conflict resolve it. Moreover, dirty graphs are usually not even resolvable
due cycles.

