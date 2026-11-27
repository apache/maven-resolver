# Dependency Management
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

Dependency management is a set of rules that changes the attributes of dependencies *while the graph is being
collected*. It is easy to confuse with the two operations that surround it, so start with the distinction:

| Phase | What it does | Where management fits |
| --- | --- | --- |
| Collection | Reads artifact descriptors and builds the *dirty* [dependency graph](dependency-graph.html). | `DependencyManager` applies the rules as nodes enter the graph. |
| Transformation | Detects conflicts and prunes the dirty graph into a resolved graph. | Works with the attributes management already set. |
| Resolution | Downloads the artifact files for the nodes of the resolved graph. | Plays no part in management. |

Management never adds a dependency that no descriptor declares, and it never fetches a file. A rule for an artifact
that is not otherwise present in the graph produces no node. See
[Transitive Dependency Resolution](transitive-dependency-resolution.html) for the full collection and resolution flow.

## What a rule can change

[`DependencyManager`](apidocs/org/eclipse/aether/collection/DependencyManager.html) receives a `Dependency` and returns
a `DependencyManagement` update. The update can override the version, scope, optionality, exclusions and system path.
For Maven repositories, most version and scope rules for a POM's *own* dependencies are already applied by the model
builder when it creates the effective model. The resolver-side manager carries those rules into the rest of the graph:

* It derives a child manager for each node with `deriveChildManager()`.
* It records where each rule was found and applies rules only to nodes at the required depths.
* It marks each changed node with the corresponding managed bit, so later graph transformers do not overwrite the
  decision.

Rules are keyed by group id, artifact id, classifier and extension. When several rules match, the one nearest the root
wins, and a rule declared at the root overrides all others.

## Effect on transitive dependencies

The depth at which a rule is collected and the depth at which it is applied determine whether it reaches transitives.
The implementations in `org.eclipse.aether.util.graph.manager` differ in exactly this respect. Depths are counted
from the manager's factory: 0 is the factory, 1 is the root and 2 and above are its descendants. "Collects rules
before" is exclusive, so a rule is collected from a node only if the node's depth is below that number:

| Implementation | Collects rules before depth | Applies rules from | Intended use |
| --- | --- | --- | --- |
| [`ClassicDependencyManager`](apidocs/org/eclipse/aether/util/graph/manager/ClassicDependencyManager.html) | root only (depth 1 is skipped for Maven 2.x compatibility) | depth 2 | Maven 2.x compatible behaviour. Ignores exclusions introduced by direct dependencies. |
| [`DefaultDependencyManager`](apidocs/org/eclipse/aether/util/graph/manager/DefaultDependencyManager.html) | any depth | depth 0 | Aggressive; can override the model builder. Not recommended for Maven. |
| [`TransitiveDependencyManager`](apidocs/org/eclipse/aether/util/graph/manager/TransitiveDependencyManager.html) | any depth | depth 2 | Recommended for modern Maven: full transitive management without fighting the model builder. |

With `TransitiveDependencyManager`, version and exclusion rules are collected at any depth, but scope and optional
rules only from the root, to avoid interfering with inheritance.

A rule found at depth 1 is therefore normally applied to dependencies at depth 2 and below, not to its own node.
Root rules are the exception: they are also applied to the root's direct dependencies. This is why a version declared
in the root's dependency management can change a version that a transitive POM declares for its own child.

## Management and mediation are different

Management supplies the *inputs*; mediation chooses the *winner*. After collection, several nodes with the same group
id, artifact id, classifier and extension may exist. The conflict resolver groups them under one conflict id and the
version selector picks one version — by default the nearest one, with alternatives such as highest available. Changing
a managed version changes which version participates in mediation; it does not bypass mediation. Conversely, a
selection made by the conflict resolver does not rewrite the managed attributes of the losing nodes.

The conflict resolver is configurable; see the
[`ConflictResolver` configuration constants](apidocs/org/eclipse/aether/util/graph/transformer/ConflictResolver.html)
(`CONFIG_PROP_VERBOSE` and `CONFIG_PROP_CONFLICT_RESOLVER_IMPL` in particular), and the
[dependency graph troubleshooting guide](dependency-graph.html#troubleshooting-a-dependency-graph) for how to keep
losing nodes visible in the returned graph.

## Worked example

The root declares `com.acme:lib:2.0` in its dependency management. Neither `a` nor `b` declares that artifact
directly, but both pull it in transitively:

```
root
├── com.acme:a:1.0
│   └── com.acme:lib:1.0
└── com.acme:b:1.0
    └── com.acme:lib:1.2
```

With `TransitiveDependencyManager`, collection applies the root rule to both transitive `lib` nodes. The dirty graph
now contains two nodes for the same conflict id, both carrying version `2.0`:

```
root
├── com.acme:a:1.0
│   └── com.acme:lib:2.0    (managed from 1.0)
└── com.acme:b:1.0
    └── com.acme:lib:2.0    (managed from 1.2)
```

The `(managed from ...)` annotations appear only when `DependencyManagerUtils.CONFIG_PROP_VERBOSE` is enabled; see the
[dependency graph troubleshooting guide](dependency-graph.html#troubleshooting-a-dependency-graph).

Mediation then sees one version for `com.acme:lib` and keeps a single node, the first one encountered at equal
depth, so the resolved graph is a tree:

```
root
├── com.acme:a:1.0
│   └── com.acme:lib:2.0
└── com.acme:b:1.0
```

If the two managed versions had differed, mediation would still have to select one; with verbose conflict resolution
enabled, the losing node remains in the graph marked as a conflict loser instead of being pruned. Resolving artifacts
then downloads `a:1.0`, `b:1.0` and `lib:2.0` — never the unmanaged `1.0` or `1.2` files.
