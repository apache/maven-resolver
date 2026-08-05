# Dependency Graph
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

When resolving transitive dependencies, Resolver (formerly Aether) constructs a *dependency graph*.
The graph contains `DependencyNode` instances.
Each node represents one dependency.
The direct dependencies of a node are its child nodes.
During the early stages of resolution, the graph usually contains duplicate dependencies and sometimes cycles.
The example below shows this.

```
   root
   / \
  /   \
a:1   b:1  <--+
  \   / \     |
   \ /   \    |
   c:1   a:2  |
    |         |
    +---------+
```

After conflict resolution, the graph becomes a *dependency tree*.
Conflict resolution removes duplicate dependencies.
The tree for the previous example looks like this:

```
   root
   / \
  /   \
a:1   b:1
 |
 |
c:1
```

The dependency tree is a useful data structure.
It provides the complete set of artifacts that are necessary to form a classpath.
A simple recursive traversal gathers the relevant dependencies.

## Troubleshooting a Dependency Graph

The dependency tree provides a basic way for end users to understand why and how a given artifact became a dependency.
But the dependency tree misses some information that the dependency graph has.
The examples above illustrate this.
For example, the tree does not show that `b:1` also depends on `c:1`.
To troubleshoot complex dependency graphs, some configuration properties keep useful data in the dependency graph returned by `RepositorySystem.collectDependencies()`.

For example, enable the configuration property `ConflictResolver.CONFIG_PROP_VERBOSE` to produce a graph similar to the dependency hierarchy view in m2e.
The graph keeps the nodes that conflict.
This helps end users understand all the paths that pull in a given dependency.

The configuration property `DependencyManagerUtils.CONFIG_PROP_VERBOSE` can record the attributes of a dependency.
It records the attributes before dependency management updates them.
This helps end users understand why the graph contains one version of a dependency instead of another.
It also helps them understand why a dependency is in a given scope.

The API documentation for these configuration properties describes their effects.
It also describes how to access the additional data.
