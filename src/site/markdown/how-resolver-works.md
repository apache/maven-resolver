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

Maven Artifact Resolver (former Aether) is central piece of Maven, and thus,
is many times target of questions and curiosity how it actually works under the hood.
This document tries to shed some light on this topic, and explain the main concepts
and building blocks of Resolver.

## Core Concepts

At the core of Resolver lies the concept of **artifacts** and **repositories**. An artifact is basically a 
"symbolic coordinate" of some content. Usually it is a JAR, but it can be really anything, as long as it is
"addressable" using Maven coordinates: `<groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>` (default value of
`extension` is `jar`, and default value for `classifier` is `""`, empty string). Repositories
are places where artifacts are stored and from where they can be retrieved. Resolver by default operates
with one local repository (usually a directory on local filesystem) and zero or more remote repositories.

The term "resolving" is a bit overloaded, but in general it involves following steps:
* **dependency graph collection** builds the "dependency graph"
* **conflict resolution** makes the graph free of cycles, conflicts and duplicates, resulting in "dependency tree"
* **flattening** transforms the tree into a flat list of artifacts, which also represents classpath ordering
* **artifact resolving** is the process of resolving (downloading and caching, if needed) the actual artifact payload from local repository

### Dependency Graph Collection

Collecting is the first step, user input is usually root artifact along with set of remote repositories to use.
Output of collection is **dependency graph** (aka "dirty graph"), a dependency graph that may contain cycles, conflicts, 
duplicates and the like.

Since Resolver 1.9.x there are two collector implementations: legacy DepthFirst (DF) and new BreadthFirst (BF) collector.
The BF collector is now the default one, as it offers better performance.

One very important thing, that is constantly misunderstood, is what information is used during graph collection. Many
assume whole POM, but that is untrue: it is effective model, but only certain parts of it (!) are used.
If am allowed to overly simplify, only following aspects of effective model are used during graph collection:
* `project/dependencies` as direct dependencies on given node
* `project/dependencyManagement/dependencies` for **subsequent** dependency management on given node
* `project/repositories` for **subsequent** repositories to be used on given node

Many wrong assumptions exist on Internet like POMs `project/packaging` is being used, that are clearly wrong. For more, 
check out `org.eclipse.aether.resolution.ArtifactDescriptorResult` class, as that is the "peephole" for Resolver
to see effective model.

Another important detail is that Resolver 1.x by default ignored transitive dependency management, and this changed in
Resolver 2.x where transitive dependency management is enabled by default.

This steps operates only on models, only POMs are being resolved and their effective models are being built during 
graph collection.

See also [common misconceptions](common-misconceptions.html).

### Conflict Resolution

Conflict resolution is the process of removing conflicts, duplicates and cycles from dependency graph, resulting 
in **dependency tree** (as cycles are removed).

Since Resolver 2.x there are two conflict resolver implementations: "legacy" (doing multiple graph passes) and new, 
more performant "path based" (doing single graph pass) conflict resolver. Winner selection strategy is also pluggable since 
Resolver 2.x, out of the box "nearest" and "highest" strategies are available (extras as "version convergence" and
"major version convergence" are available as well, but not available by default; experimental).

This step operates only on graph stored in memory, there is no resolution of any kind happening.

### Flattening

Flattening is the process of transforming the tree into a flat list of artifacts. This list ordering also represents
classpath ordering. This is where filtering is applied as well.

Resolver historically used "pre-order" flattening of tree into list, but Resolver 2 offers three strategies: "pre-order", 
"post-order" and "level order" (the default). These strategies are currently fixed, but in future we may offer more 
flexible API for custom flattening strategies.

This step operates only on tree stored in memory, there is no resolution of any kind happening.

### Artifact Resolving

Artifact resolving is the process of resolving (downloading and caching, if needed) the actual artifact payload from 
local repository.

Important detail is that this step is implicitly being used by Dependency Graph Resolution step as well, as collector
will ask for artifact descriptors (effective model) of each artifact during graph collection, and artifact descriptor calls
into model builder, that in turn, during building, will ask for POM resolutions as needed (parent POMs, import POMs, mixins, etc).

----

In general, steps "collection" and "conflict resolution" are done together, and lingo we use for those is "dependency collection".
The "flattening" and "artifact resolving" are also usually done together, and we use for those the term "artifact resolving".
To have the story more confusing, all the steps together are also called "dependency resolution".
Resolver API reflects this terminology and offers methods doing collection, resolution or both.

Method `CollectResult collectDependencies(RepositorySystemSession session, CollectRequest request)` performs only the collection step,
as name suggests. Hence, only steps "collection" and "conflict resolution" are performed.

Method `List<ArtifactResult> resolveArtifacts(RepositorySystemSession session, Collection<? extends ArtifactRequest> requests)`
performs only the artifact resolving step.

Method `DependencyResult resolveDependencies(RepositorySystemSession session, DependencyRequest request)` performs both 
collection and resolution steps.

Also, each subsequent step depends on the previous one, for example a "dirty graph" cannot be flattened (due possible cycles).

