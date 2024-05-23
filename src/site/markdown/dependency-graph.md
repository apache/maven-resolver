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

When resolving transitive dependencies, Resolver (former Aether) constructs a *dependency
graph* consisting of `DependencyNode` instances where each node
represents a dependency and its direct dependencies are represented as
child nodes. During early stages of the resolution process, there are
usually duplicate dependencies or even cycles in the graph as sketched
below:

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

Once this dependency graph has undergone conflict resolution, i.e.
duplicate dependencies have been removed, one actually has a *dependency
tree*. Taking the previous example, the tree might look like this:

```
   root
   / \
  /   \
a:1   b:1
 |
 |
c:1
```

The dependency tree is a handy data structure to get the complete set of
artifacts one would need to form a classpath etc. as a simple recursive
traversal is sufficient to gather the relevant dependencies.

## Troubleshooting a Dependency Graph

The dependency tree provides a compact and basic means to end users to
understand why/how a given artifact ended up among the dependencies. But
as the examples above illustrate, the dependency tree misses some
information compared to the dependency graph. For instance, the tree
does not indicate that `b:1` also depends on `c:1`. To help
troubleshooting complex dependency graphs, some configuration properties
exist to keep useful data in the dependency graph returned by
`RepositorySystem.collectDependencies()`.

For instance, the configuration property
`ConflictResolver.CONFIG_PROP_VERBOSE` can be enabled to produce a graph
similar to m2e's dependency hierarchy view where conflicting nodes are
retained. This gives end users a better understanding of all the paths
that pull in a given dependency.

The configuration property `DependencyManagerUtils.CONFIG_PROP_VERBOSE`
can be enabled to record the attributes of a dependency before they were
updated due to dependency management. This helps end users to understand
why one version of a dependency and not the other is found in the graph
or why a dependency ended up in a given scope.

Please see the API docs for said configuration properties for details
regarding their effects and ways to access the additional data.
