/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.eclipse.aether.util.graph.transformer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.DependencyGraphTransformationContext;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.util.artifact.ArtifactIdUtils;

import static java.util.Objects.requireNonNull;

/**
 * A high-performance dependency graph transformer that resolves version and scope conflicts among dependencies.
 * This is the recommended conflict resolver implementation that provides O(N) performance characteristics,
 * significantly improving upon the O(N²) worst-case performance of {@link ClassicConflictResolver}.
 * <p>
 * For a given set of conflicting nodes, one node will be chosen as the winner. How losing nodes are handled
 * depends on the configured verbosity level: they may be removed entirely, have their children removed, or
 * be left in place with conflict information. The exact rules by which a winning node and its effective scope
 * are determined are controlled by user-supplied implementations of {@link ConflictResolver.VersionSelector}, {@link ConflictResolver.ScopeSelector},
 * {@link ConflictResolver.OptionalitySelector} and {@link ConflictResolver.ScopeDeriver}.
 * <p>
 * <strong>Performance Characteristics:</strong>
 * <ul>
 * <li><strong>Time Complexity:</strong> O(N) where N is the number of dependency nodes</li>
 * <li><strong>Memory Usage:</strong> Creates a parallel tree structure for conflict-free processing</li>
 * <li><strong>Scalability:</strong> Excellent performance on large multi-module projects</li>
 * </ul>
 * <p>
 * <strong>Algorithm Overview:</strong>
 * <ol>
 * <li><strong>Path Tree Construction:</strong> Builds a cycle-free parallel tree structure from the input
 *     dependency graph, where each {@code Path} represents a unique route to a dependency node</li>
 * <li><strong>Conflict Partitioning:</strong> Groups paths by conflict ID (based on groupId:artifactId:classifier:extension coordinates)</li>
 * <li><strong>Topological Processing:</strong> Processes conflict groups in topologically sorted order</li>
 * <li><strong>Winner Selection:</strong> Uses provided selectors to choose winners within each conflict group</li>
 * <li><strong>Graph Transformation:</strong> Applies changes back to the original dependency graph</li>
 * </ol>
 * <p>
 * <strong>Key Differences from {@link ClassicConflictResolver}:</strong>
 * <ul>
 * <li><strong>Performance:</strong> O(N) vs O(N²) time complexity</li>
 * <li><strong>Memory Strategy:</strong> Uses parallel tree structure vs in-place graph modification</li>
 * <li><strong>Cycle Handling:</strong> Explicitly breaks cycles during tree construction</li>
 * <li><strong>Processing Order:</strong> Level-by-level from root vs depth-first traversal</li>
 * </ul>
 * <p>
 * <strong>When to Use:</strong>
 * <ul>
 * <li>Default choice for all new projects and Maven 4+ installations</li>
 * <li>Large multi-module projects with many dependencies</li>
 * <li>Performance-critical build environments</li>
 * <li>Any scenario where {@link ClassicConflictResolver} shows performance bottlenecks</li>
 * </ul>
 * <p>
 * <strong>Implementation Note:</strong> This conflict resolver builds a cycle-free "parallel" structure based on the
 * passed-in dependency graph, and applies operations level by level starting from the root. The parallel {@code Path}
 * tree ensures that cycles in the original graph don't affect the conflict resolution algorithm's performance.
 *
 * @see ClassicConflictResolver
 * @since 2.0.11
 */
public final class PathConflictResolver extends ConflictResolver {
    private final ConflictResolver.VersionSelector versionSelector;
    private final ConflictResolver.ScopeSelector scopeSelector;
    private final ConflictResolver.ScopeDeriver scopeDeriver;
    private final ConflictResolver.OptionalitySelector optionalitySelector;

    /**
     * Creates a new conflict resolver instance with the specified hooks.
     *
     * @param versionSelector The version selector to use, must not be {@code null}.
     * @param scopeSelector The scope selector to use, must not be {@code null}.
     * @param optionalitySelector The optionality selector ot use, must not be {@code null}.
     * @param scopeDeriver The scope deriver to use, must not be {@code null}.
     */
    public PathConflictResolver(
            ConflictResolver.VersionSelector versionSelector,
            ConflictResolver.ScopeSelector scopeSelector,
            ConflictResolver.OptionalitySelector optionalitySelector,
            ConflictResolver.ScopeDeriver scopeDeriver) {
        this.versionSelector = requireNonNull(versionSelector, "version selector cannot be null");
        this.scopeSelector = requireNonNull(scopeSelector, "scope selector cannot be null");
        this.optionalitySelector = requireNonNull(optionalitySelector, "optionality selector cannot be null");
        this.scopeDeriver = requireNonNull(scopeDeriver, "scope deriver cannot be null");
    }

    @SuppressWarnings("unchecked")
    @Override
    public DependencyNode transformGraph(DependencyNode node, DependencyGraphTransformationContext context)
            throws RepositoryException {
        requireNonNull(node, "node cannot be null");
        requireNonNull(context, "context cannot be null");
        List<String> sortedConflictIds = (List<String>) context.get(TransformationContextKeys.SORTED_CONFLICT_IDS);
        if (sortedConflictIds == null) {
            ConflictIdSorter sorter = new ConflictIdSorter();
            sorter.transformGraph(node, context);

            sortedConflictIds = (List<String>) context.get(TransformationContextKeys.SORTED_CONFLICT_IDS);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> stats = (Map<String, Object>) context.get(TransformationContextKeys.STATS);
        long time1 = System.nanoTime();

        @SuppressWarnings("unchecked")
        Collection<Collection<String>> conflictIdCycles =
                (Collection<Collection<String>>) context.get(TransformationContextKeys.CYCLIC_CONFLICT_IDS);
        if (conflictIdCycles == null) {
            throw new RepositoryException("conflict id cycles have not been identified");
        }

        Map<DependencyNode, String> conflictIds =
                (Map<DependencyNode, String>) context.get(TransformationContextKeys.CONFLICT_IDS);
        if (conflictIds == null) {
            throw new RepositoryException("conflict groups have not been identified");
        }

        Map<String, Collection<String>> cyclicPredecessors = new HashMap<>();
        for (Collection<String> cycle : conflictIdCycles) {
            for (String conflictId : cycle) {
                Collection<String> predecessors = cyclicPredecessors.computeIfAbsent(conflictId, k -> new HashSet<>());
                predecessors.addAll(cycle);
            }
        }

        State state = new State(
                ConflictResolver.getVerbosity(context.getSession()),
                versionSelector.getInstance(node, context),
                scopeSelector.getInstance(node, context),
                scopeDeriver.getInstance(node, context),
                optionalitySelector.getInstance(node, context),
                sortedConflictIds,
                conflictIds,
                cyclicPredecessors);

        state.build(node);

        // loop over topographically sorted conflictIds
        for (String conflictId : state.sortedConflictIds) {
            // paths in given conflict group to consider
            List<Path> paths = state.partitions.get(conflictId);
            if (paths.isEmpty()) {
                // this means that whole group "fall out of scope" (are all on loser branches); skip
                continue;
            }

            // create conflict context for given conflictId
            ConflictContext ctx = new ConflictContext(
                    node,
                    state.conflictIds,
                    paths.stream().map(ConflictItem::new).collect(Collectors.toList()),
                    conflictId);

            // select winner (is done by VersionSelector)
            state.versionSelector.selectVersion(ctx);
            if (ctx.winner == null) {
                throw new RepositoryException("conflict resolver did not select winner among " + ctx.items);
            }
            // select scope (no side effect between this and above operations)
            state.scopeSelector.selectScope(ctx);
            // select optionality (no side effect between this and above operations)
            state.optionalitySelector.selectOptionality(ctx);

            // we have a winner path
            Path winnerPath = ctx.winner.path;

            // mark conflictId as resolved with winner; sanity check
            if (state.resolvedIds.containsKey(conflictId)) {
                throw new RepositoryException("conflict resolver already have winner for conflictId=" + conflictId
                        + ": " + state.resolvedIds);
            }
            state.resolvedIds.put(conflictId, winnerPath);

            // loop over considered paths and apply selection results; note: node may remove itself from iterated list
            for (Path path : new ArrayList<>(paths)) {
                // apply selected inherited properties scope/optional to all (winner carries version; others are losers)
                path.scope = ctx.scope;
                path.optional = ctx.optional;

                // reset children as inheritance may be affected by this node scope/optionality change
                path.children.forEach(c -> c.pull(0));
                // derive with new values from this to children only; observe winner flag
                path.derive(1, path == winnerPath);
                // push this node full level changes to DN graph
                path.push(0);
            }
        }

        if (stats != null) {
            long time2 = System.nanoTime();
            stats.put("ConflictResolver.totalTime", time2 - time1);
            stats.put(
                    "ConflictResolver.conflictItemCount",
                    state.partitions.values().stream().map(List::size).reduce(0, Integer::sum));
        }

        return node;
    }

    /**
     * State of conflict resolution processing, to make this component (held in session) re-entrant by multiple threads.
     */
    private static class State {
        /**
         * Verbosity to be applied, see {@link ConflictResolver.Verbosity}.
         */
        private final ConflictResolver.Verbosity verbosity;

        /**
         * The {@link ConflictResolver.VersionSelector} to use.
         */
        private final ConflictResolver.VersionSelector versionSelector;

        /**
         * The {@link ConflictResolver.ScopeSelector} to use.
         */
        private final ConflictResolver.ScopeSelector scopeSelector;

        /**
         * The {@link ConflictResolver.ScopeDeriver} to use.
         */
        private final ConflictResolver.ScopeDeriver scopeDeriver;

        /**
         * The {@link ConflictResolver.OptionalitySelector} to use/
         */
        private final ConflictResolver.OptionalitySelector optionalitySelector;

        /**
         * Topologically sorted conflictIds from {@link ConflictIdSorter}.
         */
        private final List<String> sortedConflictIds;

        /**
         * The node to conflictId mapping from {@link ConflictMarker}.
         */
        private final Map<DependencyNode, String> conflictIds;

        /**
         * The map of conflictIds which could apply to ancestors of nodes with the key conflict id, used to avoid
         * recursion early on. This is basically a superset of the key set of resolvedIds, the additional ids account
         * for cyclic dependencies. From {@link ConflictIdSorter}.
         */
        private final Map<String, Collection<String>> cyclicPredecessors;

        /**
         * A mapping from conflictId to paths represented as {@link Path}s that exist for each conflictId. In other
         * words all paths to each {@link DependencyNode} that are member of same conflictId group.
         */
        private final Map<String, List<Path>> partitions;

        /**
         * A mapping from conflictIds to winner {@link Path}, hence {@link DependencyNode}  for given conflictId.
         */
        private final Map<String, Path> resolvedIds;

        @SuppressWarnings("checkstyle:ParameterNumber")
        private State(
                ConflictResolver.Verbosity verbosity,
                ConflictResolver.VersionSelector versionSelector,
                ConflictResolver.ScopeSelector scopeSelector,
                ConflictResolver.ScopeDeriver scopeDeriver,
                ConflictResolver.OptionalitySelector optionalitySelector,
                List<String> sortedConflictIds,
                Map<DependencyNode, String> conflictIds,
                Map<String, Collection<String>> cyclicPredecessors) {
            this.verbosity = verbosity;
            this.versionSelector = versionSelector;
            this.scopeSelector = scopeSelector;
            this.scopeDeriver = scopeDeriver;
            this.optionalitySelector = optionalitySelector;
            this.sortedConflictIds = sortedConflictIds;
            this.conflictIds = conflictIds;
            this.cyclicPredecessors = cyclicPredecessors;
            this.partitions = new HashMap<>();
            this.resolvedIds = new HashMap<>();
        }

        /**
         * Consumes the dirty graph and builds internal structures out of {@link Path} instances that is always a
         * tree.
         */
        private Path build(DependencyNode node) throws RepositoryException {
            Path root = new Path(this, node, null);
            gatherCRNodes(root);
            return root;
        }

        /**
         * Recursively builds {@link Path} graph by observing each node associated {@link DependencyNode}.
         */
        private void gatherCRNodes(Path node) throws RepositoryException {
            List<DependencyNode> children = node.dn.getChildren();
            if (!children.isEmpty()) {
                // add children; we will get back those really added (not causing cycles)
                List<Path> added = node.addChildren(children);
                for (Path child : added) {
                    gatherCRNodes(child);
                }
            }
        }
    }

    /**
     * Represents a unique path within the dependency graph from the root to a specific {@link DependencyNode}.
     * This is the core data structure that enables the O(N) performance of {@link PathConflictResolver}.
     * <p>
     * <strong>Key Concepts:</strong>
     * <ul>
     * <li><strong>Path Uniqueness:</strong> Each {@code Path} instance represents a distinct route through
     *     the dependency graph, even if multiple paths lead to the same {@code DependencyNode}</li>
     * <li><strong>Cycle-Free Structure:</strong> The {@code Path} tree is guaranteed to be acyclic, even
     *     when the original dependency graph contains cycles</li>
     * <li><strong>Parallel Structure:</strong> This creates a "clean" tree alongside the original "dirty"
     *     graph for efficient processing</li>
     * </ul>
     * <p>
     * <strong>Example:</strong> If dependency A appears in the graph via two different routes:
     * <pre>
     * Root → B → A (path 1)
     * Root → C → A (path 2)
     * </pre>
     * Two separate {@code Path} instances will be created, both pointing to the same {@code DependencyNode} A,
     * but representing different paths through the dependency tree.
     * <p>
     * <strong>Memory Optimization:</strong> While this creates additional objects, it enables the algorithm
     * to process conflicts in O(N) time rather than O(N²), making it much more efficient for large graphs.
     * <p>
     * <strong>Conflict Resolution:</strong> Paths are grouped by conflict ID (based on groupId:artifactId:classifier:extension coordinates),
     * and the conflict resolution algorithm can efficiently process each group independently.
     */
    private static class Path {
        private final State state;
        private DependencyNode dn;
        private final String conflictId;
        private final Path parent;
        private final int depth;
        private final List<Path> children;
        private final List<DependencyNode> cycles;
        private String scope;
        private boolean optional;

        private Path(State state, DependencyNode dn, Path parent) {
            this.state = state;
            this.dn = dn;
            this.conflictId = state.conflictIds.get(dn);
            this.parent = parent;
            this.depth = parent != null ? parent.depth + 1 : 0;
            this.children = new ArrayList<>();
            this.cycles = new ArrayList<>();
            pull(0);

            this.state
                    .partitions
                    .computeIfAbsent(this.conflictId, k -> new ArrayList<>())
                    .add(this);
        }

        /**
         * Pulls (possibly updated) scope and optional values from associated {@link DependencyNode} to this instance,
         * going down toward children recursively the required count of levels.
         */
        private void pull(int levels) {
            Dependency d = dn.getDependency();
            if (d != null) {
                this.scope = d.getScope();
                this.optional = d.isOptional();
            } else {
                this.scope = "";
                this.optional = false;
            }
            int newLevels = levels - 1;
            if (newLevels >= 0) {
                for (Path child : this.children) {
                    child.pull(newLevels);
                }
            }
        }

        /**
         * Derives (from this to children direction) values that are "inherited" in tree: scope and optionality in the tree
         * recursively going down required count of "levels".
         */
        private void derive(int levels, boolean winner) throws RepositoryException {
            if (!winner) {
                if (this.parent != null) {
                    if ((dn.getManagedBits() & DependencyNode.MANAGED_SCOPE) == 0) {
                        ScopeContext context = new ScopeContext(this.parent.scope, this.scope);
                        state.scopeDeriver.deriveScope(context);
                        this.scope = context.derivedScope;
                    }
                    if ((dn.getManagedBits() & DependencyNode.MANAGED_OPTIONAL) == 0) {
                        if (!this.optional && this.parent.optional) {
                            this.optional = true;
                        }
                    }
                } else {
                    this.scope = "";
                    this.optional = false;
                }
            }
            int newLevels = levels - 1;
            if (newLevels >= 0) {
                for (Path child : children) {
                    child.derive(newLevels, false);
                }
            }
        }

        /**
         * Pushes (applies) the scope and optional and structural changes to associated {@link DependencyNode} modifying
         * the graph of it. Verbosity is observed, and depending on it the conflicting/loser nodes are removed, or
         * just their children is removed (with special care for version ranges, see {@link #relatedSiblingsCount(Artifact, Path)}
         * or by just doing nothing with them only marking losers in full verbosity mode.
         */
        private void push(int levels) {
            if (this.parent != null) {
                Path winner = this.state.resolvedIds.get(this.conflictId);
                if (winner == null) {
                    throw new IllegalStateException(
                            "Winner selection did not happen for conflictId=" + this.conflictId);
                }
                if (!Objects.equals(winner.conflictId, this.conflictId)) {
                    throw new IllegalStateException(
                            "ConflictId mix-up: this=" + this.conflictId + " winner=" + winner.conflictId);
                }

                if (winner == this) {
                    // copy onto dn; if applicable
                    if (this.dn.getDependency() != null) {
                        this.dn.setData(
                                ConflictResolver.NODE_DATA_ORIGINAL_SCOPE,
                                this.dn.getDependency().getScope());
                        this.dn.setData(
                                ConflictResolver.NODE_DATA_ORIGINAL_OPTIONALITY,
                                this.dn.getDependency().getOptional());
                        this.dn.setScope(this.scope);
                        this.dn.setOptional(this.optional);

                        // unless FULL, kill off cycles
                        if (this.state.verbosity != Verbosity.FULL) {
                            this.dn.getChildren().removeAll(this.cycles);
                        }
                    }
                } else {
                    // loser; move out of scope
                    moveOutOfScope();
                    boolean markLoser = false;
                    switch (state.verbosity) {
                        case NONE:
                            // remove this dn
                            this.parent.children.remove(this);
                            this.parent.dn.setChildren(new ArrayList<>(this.parent.dn.getChildren()));
                            this.parent.dn.getChildren().remove(this.dn);
                            this.children.clear();
                            break;
                        case STANDARD:
                            String artifactId = ArtifactIdUtils.toId(this.dn.getArtifact());
                            String winnerArtifactId = ArtifactIdUtils.toId(winner.dn.getArtifact());
                            if (!Objects.equals(artifactId, winnerArtifactId)
                                    && relatedSiblingsCount(this.dn.getArtifact(), this.parent) > 1) {
                                // is redundant dn (version range); remove it
                                this.parent.children.remove(this);
                                this.parent.dn.setChildren(new ArrayList<>(this.parent.dn.getChildren()));
                                this.parent.dn.getChildren().remove(this.dn);
                                this.children.clear();
                            } else {
                                // leave this dn; remove children
                                this.children.clear();
                                this.dn.setChildren(Collections.emptyList());
                                markLoser = true;
                            }
                            break;
                        case FULL:
                            // leave all in place (even cycles)
                            markLoser = true;
                            break;
                        default:
                            throw new IllegalArgumentException("Unknown " + state.verbosity);
                    }
                    if (markLoser) {
                        // copy dn
                        DependencyNode dnCopy = new DefaultDependencyNode(this.dn);
                        dnCopy.setData(ConflictResolver.NODE_DATA_WINNER, winner.dn);
                        dnCopy.setData(
                                ConflictResolver.NODE_DATA_ORIGINAL_SCOPE,
                                this.dn.getDependency().getScope());
                        dnCopy.setData(
                                ConflictResolver.NODE_DATA_ORIGINAL_OPTIONALITY,
                                this.dn.getDependency().getOptional());
                        dnCopy.setScope(this.scope);
                        dnCopy.setOptional(this.optional);
                        if (ConflictResolver.Verbosity.FULL != state.verbosity) {
                            dnCopy.getChildren().clear();
                        }

                        // swap it out in DN graph
                        this.parent
                                .dn
                                .getChildren()
                                .set(this.parent.dn.getChildren().indexOf(this.dn), dnCopy);
                        this.dn = dnCopy;
                    }
                }
            }
            if (!this.children.isEmpty()) {
                int newLevels = levels - 1;
                if (newLevels >= 0) {
                    // child may remove itself from iterated list
                    for (Path child : new ArrayList<>(children)) {
                        child.push(newLevels);
                    }
                }
            } else if (!this.dn.getChildren().isEmpty()) {
                this.dn.setChildren(Collections.emptyList());
            }
        }

        /**
         * Counts "relatives" (GACE equal) artifacts under same parent; this is for cleaning up redundant nodes in
         * case of version ranges, where same GACE is resolved into multiple GACEV as range is resolved. In {@link ConflictResolver.Verbosity#STANDARD}
         * verbosity mode we remove "redundant" nodes (of a range) leaving only "winner equal" loser, that have same GACEV as winner.
         */
        private int relatedSiblingsCount(Artifact artifact, Path parent) {
            String ga = artifact.getGroupId() + ":" + artifact.getArtifactId();
            return Math.toIntExact(parent.children.stream()
                    .map(n -> n.dn.getArtifact())
                    .filter(a -> ga.equals(a.getGroupId() + ":" + a.getArtifactId()))
                    .count());
        }

        /**
         * Removes this and all child {@link Path} nodes from winner selection scope; essentially marks whole subtree
         * from "this and below" as loser, to not be considered in subsequent winner selections.
         */
        private void moveOutOfScope() {
            this.state.partitions.get(this.conflictId).remove(this);
            for (Path child : this.children) {
                child.moveOutOfScope();
            }
        }

        /**
         * Adds node children: this method should be "batch" used, as all (potential) children should be added at once.
         * Method will return really added ones, as this class avoids cycles.
         */
        private List<Path> addChildren(List<DependencyNode> children) throws RepositoryException {
            Collection<String> thisCyclicPredecessors =
                    this.state.cyclicPredecessors.getOrDefault(this.conflictId, Collections.emptySet());

            ArrayList<Path> added = new ArrayList<>(children.size());
            for (DependencyNode child : children) {
                String childConflictId = this.state.conflictIds.get(child);
                if (!this.state.partitions.containsKey(childConflictId)
                        || !thisCyclicPredecessors.contains(childConflictId)) {
                    Path c = new Path(this.state, child, this);
                    this.children.add(c);
                    c.derive(0, false);
                    added.add(c);
                } else {
                    this.cycles.add(child);
                }
            }
            return added;
        }

        /**
         * Dump for debug.
         */
        private void dump(String padding) {
            System.out.println(padding + this.dn + ": " + this.scope + "/" + this.optional);
            for (Path child : children) {
                child.dump(padding + "  ");
            }
        }

        /**
         * For easier debug.
         */
        @Override
        public String toString() {
            return this.dn.toString();
        }
    }

    /**
     * A context used to hold information that is relevant for deriving the scope of a child dependency.
     *
     * @see ConflictResolver.ScopeDeriver
     * @noinstantiate This class is not intended to be instantiated by clients in production code, the constructor may
     *                change without notice and only exists to enable unit testing.
     */
    private static final class ScopeContext extends ConflictResolver.ScopeContext {
        private final String parentScope;
        private final String childScope;
        private String derivedScope;

        /**
         * Creates a new scope context with the specified properties.
         *
         * @param parentScope The scope of the parent dependency, may be {@code null}.
         * @param childScope The scope of the child dependency, may be {@code null}.
         * @noreference This class is not intended to be instantiated by clients in production code, the constructor may
         *              change without notice and only exists to enable unit testing.
         */
        private ScopeContext(String parentScope, String childScope) {
            this.parentScope = (parentScope != null) ? parentScope : "";
            this.derivedScope = (childScope != null) ? childScope : "";
            this.childScope = (childScope != null) ? childScope : "";
        }

        /**
         * Gets the scope of the parent dependency. This is usually the scope that was derived by earlier invocations of
         * the scope deriver.
         *
         * @return The scope of the parent dependency, never {@code null}.
         */
        public String getParentScope() {
            return parentScope;
        }

        /**
         * Gets the original scope of the child dependency. This is the scope that was declared in the artifact
         * descriptor of the parent dependency.
         *
         * @return The original scope of the child dependency, never {@code null}.
         */
        public String getChildScope() {
            return childScope;
        }

        /**
         * Gets the derived scope of the child dependency. This is initially equal to {@link #getChildScope()} until the
         * scope deriver makes changes.
         *
         * @return The derived scope of the child dependency, never {@code null}.
         */
        public String getDerivedScope() {
            return derivedScope;
        }

        /**
         * Sets the derived scope of the child dependency.
         *
         * @param derivedScope The derived scope of the dependency, may be {@code null}.
         */
        public void setDerivedScope(String derivedScope) {
            this.derivedScope = (derivedScope != null) ? derivedScope : "";
        }
    }

    /**
     * A conflicting dependency.
     *
     * @noinstantiate This class is not intended to be instantiated by clients in production code, the constructor may
     *                change without notice and only exists to enable unit testing.
     */
    private static final class ConflictItem extends ConflictResolver.ConflictItem {
        private final Path path;
        private final List<DependencyNode> parent;
        private final Artifact artifact;
        private final DependencyNode node;
        private final int depth;
        private final String scope;
        private final int optionalities;

        private ConflictItem(Path path) {
            this.path = path;
            if (path.parent != null) {
                DependencyNode parent = path.parent.dn;
                this.parent = parent.getChildren();
                this.artifact = parent.getArtifact();
            } else {
                this.parent = null;
                this.artifact = null;
            }
            this.node = path.dn;
            this.depth = path.depth;
            this.scope = path.scope;
            this.optionalities = path.optional ? OPTIONAL_TRUE : OPTIONAL_FALSE;
        }

        /**
         * Determines whether the specified conflict item is a sibling of this item.
         *
         * @param item The other conflict item, must not be {@code null}.
         * @return {@code true} if the given item has the same parent as this item, {@code false} otherwise.
         */
        @Override
        public boolean isSibling(ConflictResolver.ConflictItem item) {
            return parent == ((ConflictItem) item).parent;
        }

        /**
         * Gets the dependency node involved in the conflict.
         *
         * @return The involved dependency node, never {@code null}.
         */
        @Override
        public DependencyNode getNode() {
            return node;
        }

        /**
         * Gets the dependency involved in the conflict, short for {@code getNode.getDependency()}.
         *
         * @return The involved dependency, never {@code null}.
         */
        @Override
        public Dependency getDependency() {
            return node.getDependency();
        }

        /**
         * Gets the zero-based depth at which the conflicting node occurs in the graph. As such, the depth denotes the
         * number of parent nodes. If actually multiple paths lead to the node, the return value denotes the smallest
         * possible depth.
         *
         * @return The zero-based depth of the node in the graph.
         */
        @Override
        public int getDepth() {
            return depth;
        }

        /**
         * Gets the derived scopes of the dependency. In general, the same dependency node could be reached via
         * different paths and each path might result in a different derived scope.
         *
         * @see ConflictResolver.ScopeDeriver
         * @return The (read-only) set of derived scopes of the dependency, never {@code null}.
         */
        @Override
        public Collection<String> getScopes() {
            return Collections.singleton(scope);
        }

        /**
         * Gets the derived optionalities of the dependency. In general, the same dependency node could be reached via
         * different paths and each path might result in a different derived optionality.
         *
         * @return A bit field consisting of {@link PathConflictResolver.ConflictItem#OPTIONAL_FALSE} and/or
         *         {@link PathConflictResolver.ConflictItem#OPTIONAL_TRUE} indicating the derived optionalities the
         *         dependency was encountered with.
         */
        @Override
        public int getOptionalities() {
            return optionalities;
        }

        @Override
        public String toString() {
            return node + " @ " + depth + " < " + artifact;
        }
    }

    /**
     * A context used to hold information that is relevant for resolving version and scope conflicts.
     *
     * @see ConflictResolver.VersionSelector
     * @see ConflictResolver.ScopeSelector
     * @noinstantiate This class is not intended to be instantiated by clients in production code, the constructor may
     *                change without notice and only exists to enable unit testing.
     */
    private static final class ConflictContext extends ConflictResolver.ConflictContext {
        private final DependencyNode root;
        private final Map<DependencyNode, String> conflictIds;
        private final Collection<ConflictResolver.ConflictItem> items;
        private final String conflictId;

        // elected properties
        private ConflictItem winner;
        private String scope;
        private Boolean optional;

        private ConflictContext(
                DependencyNode root,
                Map<DependencyNode, String> conflictIds,
                Collection<ConflictItem> items,
                String conflictId) {
            this.root = root;
            this.conflictIds = conflictIds;
            this.items = Collections.unmodifiableCollection(items);
            this.conflictId = conflictId;
        }

        /**
         * Gets the root node of the dependency graph being transformed.
         *
         * @return The root node of the dependency graph, never {@code null}.
         */
        @Override
        public DependencyNode getRoot() {
            return root;
        }

        /**
         * Determines whether the specified dependency node belongs to this conflict context.
         *
         * @param node The dependency node to check, must not be {@code null}.
         * @return {@code true} if the given node belongs to this conflict context, {@code false} otherwise.
         */
        @Override
        public boolean isIncluded(DependencyNode node) {
            return conflictId.equals(conflictIds.get(node));
        }

        /**
         * Gets the collection of conflict items in this context.
         *
         * @return The (read-only) collection of conflict items in this context, never {@code null}.
         */
        @Override
        public Collection<ConflictResolver.ConflictItem> getItems() {
            return items;
        }

        /**
         * Gets the conflict item which has been selected as the winner among the conflicting dependencies.
         *
         * @return The winning conflict item or {@code null} if not set yet.
         */
        @Override
        public ConflictResolver.ConflictItem getWinner() {
            return winner;
        }

        /**
         * Sets the conflict item which has been selected as the winner among the conflicting dependencies.
         *
         * @param winner The winning conflict item, may be {@code null}.
         */
        @Override
        public void setWinner(ConflictResolver.ConflictItem winner) {
            this.winner = (ConflictItem) winner;
        }

        /**
         * Gets the effective scope of the winning dependency.
         *
         * @return The effective scope of the winning dependency or {@code null} if none.
         */
        @Override
        public String getScope() {
            return scope;
        }

        /**
         * Sets the effective scope of the winning dependency.
         *
         * @param scope The effective scope, may be {@code null}.
         */
        @Override
        public void setScope(String scope) {
            this.scope = scope;
        }

        /**
         * Gets the effective optional flag of the winning dependency.
         *
         * @return The effective optional flag or {@code null} if none.
         */
        @Override
        public Boolean getOptional() {
            return optional;
        }

        /**
         * Sets the effective optional flag of the winning dependency.
         *
         * @param optional The effective optional flag, may be {@code null}.
         */
        @Override
        public void setOptional(Boolean optional) {
            this.optional = optional;
        }

        @Override
        public String toString() {
            return winner + " @ " + scope + " < " + items;
        }
    }
}
