# Transitive Dependency Resolution
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

An important task of Resolver (former Aether) is to resolve transitive dependencies. This
task can be split into two sub-tasks:

1.  Determine the coordinates of the artifacts that make up the
    transitive dependencies.
2.  Resolve the files for the artifacts that have been identified in
    step 1.

Artifacts and their dependencies among each other form a *[dependency graph](dependency-graph.html)*. So in other
words, step 1 means to calculate this dependency graph and step 2 is a
simple graph traversal that fetches the file for each artifact in the
dependency graph. In Resolver, this dependency graph can be easily
inspected and extension points are provided to allow for more control
over the construction of the dependency graph. To understand those
extension points, we will have a closer look at the way the dependency
graph is constructed.

Starting from a given root dependency like
`org.eclipse.aether:aether-impl:0.9.0`, the repository system first
reads the corresponding *artifact descriptor* (i.e. the POM when dealing
with Maven repositories). The artifact descriptor tells about direct
dependencies, dependency management and additional remote repositories
to consider during the resolution. For each direct dependency, a
*dependency selector* is given a chance to exclude the dependency from
the graph. If the dependency is included, a *dependency manager* applies
dependency management (if any). Next, the declared dependency version is
expanded to a list of matching versions from the repositories. For a
simple version like "1.0", the resulting list contains only that
version. For a version range like "\[1.0,2.0)", the version list
generally contains multiple versions and is subject to filtering by a
*version filter*. For each matching version of the dependency, a child
node is added to the dependency graph. Recursion of the process for each
child dependency is controlled by a *dependency traverser*.

The above process creates a dependency graph that often contains
duplicate or conflicting dependencies or even cycles and as such is
called a *dirty graph*. A chain of *dependency graph transformers* is
then used to trim this graph down and to form a *resolved graph*.

So more technically, the dependency graph that the repository system
returns to its caller is affected by instances of
`org.eclipse.aether.collection.DependencySelector`,
`org.eclipse.aether.collection.DependencyManager`,
`org.eclipse.aether.collection.VersionFilter`,
`org.eclipse.aether.collection.DependencyTraverser` and
`org.eclipse.aether.collection.DependencyGraphTransformer`. Users of the
repository system can directly control those extension points when
creating the repository system session by providing implementations that
fit their needs.

For example, a dependency selector can process exclusions on child
dependencies, exclude optional dependencies or dependencies with certain
unwanted scopes. A dependency traverser can be used to decide whether
the dependencies of a (fat) WAR should be included in the dependency
graph or not. The version filter can exclude specific versions of an
artifact which are unacceptable in the current context, e.g. ban
snapshots. Dependency graph transformers can identify and mark
conflicting nodes in the dirty tree and resolve conflicting versions or
scopes by pruning unwanted parts from the graph.

Several classes from `maven-resolver-provider` helps to construct a
session that mimics the resolution rules used by Maven. In
case you want to customize the graph construction, feel free to have a
look at the source of that class to learn about the implementation
classes being used there to achieve Maven style behavior, you might want
to reuse some of those for your own repository system session as well.
Maven plugins can easily get access to the current repository system
session via the usual parameter injection, see for the actual code bits.
