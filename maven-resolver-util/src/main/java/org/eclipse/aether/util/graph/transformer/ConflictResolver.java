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

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.DependencyGraphTransformationContext;
import org.eclipse.aether.collection.DependencyGraphTransformer;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.util.artifact.ArtifactIdUtils;

import static java.util.Objects.requireNonNull;

/**
 * A dependency graph transformer that resolves version and scope conflicts among dependencies. For a given set of
 * conflicting nodes, one node will be chosen as the winner and the other nodes are removed from the dependency graph.
 * The exact rules by which a winning node and its effective scope are determined are controlled by user-supplied
 * implementations of {@link VersionSelector}, {@link ScopeSelector}, {@link OptionalitySelector} and
 * {@link ScopeDeriver}.
 * <p>
 * By default, this graph transformer will turn the dependency graph into a tree without duplicate artifacts. Using the
 * configuration property {@link #CONFIG_PROP_VERBOSE}, a verbose mode can be enabled where the graph is still turned
 * into a tree but all nodes participating in a conflict are retained. The nodes that were rejected during conflict
 * resolution have no children and link back to the winner node via the {@link #NODE_DATA_WINNER} key in their custom
 * data. Additionally, the keys {@link #NODE_DATA_ORIGINAL_SCOPE} and {@link #NODE_DATA_ORIGINAL_OPTIONALITY} are used
 * to store the original scope and optionality of each node. Obviously, the resulting dependency tree is not suitable
 * for artifact resolution unless a filter is employed to exclude the duplicate dependencies.
 * <p>
 * This transformer will query the keys {@link TransformationContextKeys#CONFLICT_IDS},
 * {@link TransformationContextKeys#SORTED_CONFLICT_IDS}, {@link TransformationContextKeys#CYCLIC_CONFLICT_IDS} for
 * existing information about conflict ids. In absence of this information, it will automatically invoke the
 * {@link ConflictIdSorter} to calculate it.
 */
public final class ConflictResolver implements DependencyGraphTransformer {

    /**
     * The key in the repository session's {@link org.eclipse.aether.RepositorySystemSession#getConfigProperties()
     * configuration properties} used to store a {@link Boolean} flag controlling the transformer's verbose mode.
     * Accepted values are Boolean types, String type (where "true" would be interpreted as {@code true})
     * or Verbosity enum instances.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Object}
     * @configurationDefaultValue "NONE"
     */
    public static final String CONFIG_PROP_VERBOSE = ConfigurationProperties.PREFIX_AETHER + "conflictResolver.verbose";

    /**
     * The enum representing verbosity levels of conflict resolver.
     *
     * @since 1.9.8
     */
    public enum Verbosity {
        /**
         * Verbosity level to be used in all "common" resolving use cases (ie. dependencies to build class path). The
         * {@link ConflictResolver} in this mode will trim down the graph to the barest minimum: will not leave
         * any conflicting node in place, hence no conflicts will be present in transformed graph either.
         */
        NONE,

        /**
         * Verbosity level to be used in "analyze" resolving use cases (ie. dependency convergence calculations). The
         * {@link ConflictResolver} in this mode will remove any redundant collected nodes, in turn it will leave one
         * with recorded conflicting information. This mode corresponds to "classic verbose" mode when
         * {@link #CONFIG_PROP_VERBOSE} was set to {@code true}. Obviously, the resulting dependency tree is not
         * suitable for artifact resolution unless a filter is employed to exclude the duplicate dependencies.
         */
        STANDARD,

        /**
         * Verbosity level to be used in "analyze" resolving use cases (ie. dependency convergence calculations). The
         * {@link ConflictResolver} in this mode will not remove any collected node, in turn it will record on all
         * eliminated nodes the conflicting information. Obviously, the resulting dependency tree is not suitable
         * for artifact resolution unless a filter is employed to exclude the duplicate dependencies.
         */
        FULL
    }

    /**
     * Helper method that uses {@link RepositorySystemSession} and {@link #CONFIG_PROP_VERBOSE} key to figure out
     * current {@link Verbosity}: if {@link Boolean} or {@code String} found, returns {@link Verbosity#STANDARD}
     * or {@link Verbosity#NONE}, depending on value (string is parsed with {@link Boolean#parseBoolean(String)}
     * for {@code true} or {@code false} correspondingly. This is to retain "existing" behavior, where the config
     * key accepted only these values.
     * Since 1.9.8 release, this key may contain {@link Verbosity} enum instance as well, in which case that instance
     * is returned.
     * This method never returns {@code null}.
     */
    private static Verbosity getVerbosity(RepositorySystemSession session) {
        final Object verbosityValue = session.getConfigProperties().get(CONFIG_PROP_VERBOSE);
        if (verbosityValue instanceof Boolean) {
            return (Boolean) verbosityValue ? Verbosity.STANDARD : Verbosity.NONE;
        } else if (verbosityValue instanceof String) {
            return Boolean.parseBoolean(verbosityValue.toString()) ? Verbosity.STANDARD : Verbosity.NONE;
        } else if (verbosityValue instanceof Verbosity) {
            return (Verbosity) verbosityValue;
        } else if (verbosityValue != null) {
            throw new IllegalArgumentException("Unsupported Verbosity configuration: " + verbosityValue);
        }
        return Verbosity.NONE;
    }

    /**
     * The key in the dependency node's {@link DependencyNode#getData() custom data} under which a reference to the
     * {@link DependencyNode} which has won the conflict is stored.
     */
    public static final String NODE_DATA_WINNER = "conflict.winner";

    /**
     * The key in the dependency node's {@link DependencyNode#getData() custom data} under which the scope of the
     * dependency before scope derivation and conflict resolution is stored.
     */
    public static final String NODE_DATA_ORIGINAL_SCOPE = "conflict.originalScope";

    /**
     * The key in the dependency node's {@link DependencyNode#getData() custom data} under which the optional flag of
     * the dependency before derivation and conflict resolution is stored.
     */
    public static final String NODE_DATA_ORIGINAL_OPTIONALITY = "conflict.originalOptionality";

    private final VersionSelector versionSelector;

    private final ScopeSelector scopeSelector;

    private final ScopeDeriver scopeDeriver;

    private final OptionalitySelector optionalitySelector;

    private static class CRState {
        /**
         * Flag whether we should keep losers in the graph to enable visualization/troubleshooting of conflicts.
         */
        private final Verbosity verbosity;

        private final VersionSelector versionSelector;
        private final ScopeSelector scopeSelector;
        private final ScopeDeriver scopeDeriver;
        private final OptionalitySelector optionalitySelector;

        private final List<String> sortedConflictIds;
        /**
         * The output from the conflict marker
         */
        private final Map<DependencyNode, String> conflictIds;

        /**
         * The map of conflict ids which could apply to ancestors of nodes with the key conflict id, used to avoid
         * recursion early on. This is basically a superset of the key set of resolvedIds, the additional ids account
         * for cyclic dependencies.
         */
        private final Map<String, Collection<String>> cyclicPredecessors;

        private final Map<String, List<CRNode>> partitions;

        /**
         * A mapping from conflict id to winner node, helps to recognize nodes that have their effective
         * scope&optionality set or are leftovers from previous removals.
         */
        private final Map<String, CRNode> resolvedIds;

        @SuppressWarnings("checkstyle:ParameterNumber")
        private CRState(
                Verbosity verbosity,
                VersionSelector versionSelector,
                ScopeSelector scopeSelector,
                ScopeDeriver scopeDeriver,
                OptionalitySelector optionalitySelector,
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
    }

    private static class CRNode {
        private final CRState state;
        private DependencyNode dn;
        private final String conflictId;
        private final CRNode parent;
        private final int depth;
        private final List<CRNode> children;
        private String scope;
        private boolean optional;

        private CRNode(CRState state, DependencyNode dn, CRNode parent) {
            this.state = state;
            this.dn = dn;
            this.conflictId = state.conflictIds.get(dn);
            this.parent = parent;
            this.depth = parent != null ? parent.depth + 1 : 0;
            this.children = new ArrayList<>();
            pull(0);

            this.state
                    .partitions
                    .computeIfAbsent(this.conflictId, k -> new ArrayList<>())
                    .add(this);
        }

        /**
         * Pulls (possibly updated) scope and optional values from associated dependency node.
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
                for (CRNode child : this.children) {
                    child.pull(newLevels);
                }
            }
        }

        /**
         * Derives values (scope and optionality) in the tree recursively.
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
                for (CRNode child : children) {
                    child.derive(newLevels, false);
                }
            }
        }

        /**
         * Pushes (applies) the scope and optional and more to associated dependency node.
         */
        private void push(int levels) {
            if (this.parent != null) {
                CRNode winner = this.state.resolvedIds.get(this.conflictId);
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
                                NODE_DATA_ORIGINAL_SCOPE,
                                this.dn.getDependency().getScope());
                        this.dn.setData(
                                NODE_DATA_ORIGINAL_OPTIONALITY,
                                this.dn.getDependency().getOptional());
                        this.dn.setScope(this.scope);
                        this.dn.setOptional(this.optional);
                    }
                } else {
                    boolean markLoser = false;
                    switch (state.verbosity) {
                        case NONE:
                            // remove this dn; discard all children from winner consideration as well
                            this.parent.children.remove(this);
                            this.parent.dn.setChildren(new ArrayList<>(this.parent.dn.getChildren()));
                            this.parent.dn.getChildren().remove(this.dn);
                            this.children.clear();
                            break;
                        case STANDARD:
                            // if same ArtifactId, just record the facts, otherwise remove this dn children as well
                            String winnerArtifactId = ArtifactIdUtils.toId(winner.dn.getArtifact());
                            if (!winnerArtifactId.equals(ArtifactIdUtils.toId(this.dn.getArtifact()))) {
                                this.children.clear();
                                this.dn.setChildren(Collections.emptyList());
                            }
                            markLoser = true;
                            break;
                        case FULL:
                            // record the facts
                            markLoser = true;
                            break;
                        default:
                            throw new IllegalArgumentException("Unknown " + state.verbosity);
                    }
                    if (markLoser) {
                        // copy dn
                        DependencyNode dnCopy = new DefaultDependencyNode(this.dn);
                        dnCopy.setData(
                                NODE_DATA_ORIGINAL_SCOPE,
                                this.dn.getDependency().getScope());
                        dnCopy.setData(
                                NODE_DATA_ORIGINAL_OPTIONALITY,
                                this.dn.getDependency().getOptional());
                        dnCopy.setScope(this.scope);
                        dnCopy.setOptional(this.optional);
                        if (Verbosity.FULL != state.verbosity) {
                            dnCopy.getChildren().clear();
                        }

                        // swap it out in DN graph
                        this.parent.dn.getChildren().remove(this.dn);
                        this.parent.dn.getChildren().add(dnCopy);
                        this.dn = dnCopy;

                        this.dn.setData(NODE_DATA_WINNER, winner.dn);
                    }
                }
            }
            if (!this.children.isEmpty()) {
                int newLevels = levels - 1;
                if (newLevels >= 0) {
                    // child may remove itself from iterated list
                    for (CRNode child : new ArrayList<>(children)) {
                        child.push(newLevels);
                    }
                }
            } else if (!this.dn.getChildren().isEmpty()) {
                this.dn.setChildren(Collections.emptyList());
            }
        }

        /**
         * Adds node children: this method should be "batch" used, as all (potential) children should be added at once.
         * Method will return really added ones, as this class avoids cycles, is always a tree.
         */
        private List<CRNode> addChildren(List<DependencyNode> children) throws RepositoryException {
            Collection<String> thisCyclicPredecessors =
                    this.state.cyclicPredecessors.getOrDefault(this.conflictId, Collections.emptySet());

            ArrayList<CRNode> added = new ArrayList<>(children.size());
            for (DependencyNode child : children) {
                String childConflictId = this.state.conflictIds.get(child);
                if (!this.state.partitions.containsKey(childConflictId)
                        || !thisCyclicPredecessors.contains(childConflictId)) {
                    CRNode c = new CRNode(this.state, child, this);
                    this.children.add(c);
                    c.derive(0, false);
                    added.add(c);
                }
            }
            return added;
        }

        /**
         * Dumps.
         */
        private void dump(String padding) {
            System.out.println(padding + this.dn + ": " + this.scope + "/" + this.optional);
            for (CRNode child : children) {
                child.dump(padding + "  ");
            }
        }

        @Override
        public String toString() {
            return this.dn.toString();
        }
    }

    /**
     * Creates a new conflict resolver instance with the specified hooks.
     *
     * @param versionSelector The version selector to use, must not be {@code null}.
     * @param scopeSelector The scope selector to use, must not be {@code null}.
     * @param optionalitySelector The optionality selector ot use, must not be {@code null}.
     * @param scopeDeriver The scope deriver to use, must not be {@code null}.
     */
    public ConflictResolver(
            VersionSelector versionSelector,
            ScopeSelector scopeSelector,
            OptionalitySelector optionalitySelector,
            ScopeDeriver scopeDeriver) {
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

        CRState crState = new CRState(
                getVerbosity(context.getSession()),
                versionSelector.getInstance(node, context),
                scopeSelector.getInstance(node, context),
                scopeDeriver.getInstance(node, context),
                optionalitySelector.getInstance(node, context),
                sortedConflictIds,
                conflictIds,
                cyclicPredecessors);

        CRNode root = new CRNode(crState, node, null);
        gatherCRNodes(root);
        // System.out.println("LOAD:");
        // root.dump("");

        for (String conflictId : crState.sortedConflictIds) {
            List<CRNode> crNodes = crState.partitions.get(conflictId).stream()
                    .filter(n -> n.parent == null || n.parent.children.contains(n))
                    .collect(Collectors.toList());

            if (crNodes.isEmpty()) {
                continue;
            }

            ConflictContext ctx = new ConflictContext(
                    node,
                    crState.conflictIds,
                    crNodes.stream().map(ConflictItem::new).collect(Collectors.toList()),
                    conflictId);
            crState.versionSelector.selectVersion(ctx);
            if (ctx.winner == null) {
                throw new RepositoryException("conflict resolver did not select winner among " + ctx.items);
            }
            crState.scopeSelector.selectScope(ctx);
            crState.optionalitySelector.selectOptionality(ctx);

            ConflictItem winnerItem = ctx.winner;
            DependencyNode winnerNode = winnerItem.node;
            CRNode winnerCrNode = winnerItem.crNode;

            if (crState.resolvedIds.containsKey(conflictId)) {
                throw new RepositoryException("conflict resolver already have winner for conflictId=" + conflictId
                        + ": " + crState.resolvedIds);
            }
            crState.resolvedIds.put(conflictId, winnerCrNode);

            for (CRNode crNode : crNodes) {
                DependencyNode dependencyNode = crNode.dn;
                boolean winner = winnerNode == dependencyNode;

                crNode.scope = ctx.scope;
                crNode.optional = ctx.optional;

                // reset children
                crNode.children.forEach(c -> c.pull(0));
                // derive with new values from this to children
                crNode.derive(1, winner);
                // push this node changes
                crNode.push(0);
            }
        }

        // System.out.println("FINISH:");
        // root.dump("");

        if (stats != null) {
            long time2 = System.nanoTime();
            stats.put("ConflictResolver.totalTime", time2 - time1);
            stats.put(
                    "ConflictResolver.conflictItemCount",
                    crState.partitions.values().stream().map(List::size).reduce(0, Integer::sum));
        }

        return node;
    }

    private void gatherCRNodes(CRNode node) throws RepositoryException {
        List<DependencyNode> children = node.dn.getChildren();
        if (!children.isEmpty()) {
            // add children; we will get back those really added (not causing cycles)
            List<CRNode> added = node.addChildren(children);
            for (CRNode child : added) {
                gatherCRNodes(child);
            }
        }
    }

    /**
     * A context used to hold information that is relevant for deriving the scope of a child dependency.
     *
     * @see ScopeDeriver
     * @noinstantiate This class is not intended to be instantiated by clients in production code, the constructor may
     *                change without notice and only exists to enable unit testing.
     */
    public static final class ScopeContext {

        String parentScope;

        String childScope;

        String derivedScope;

        /**
         * Creates a new scope context with the specified properties.
         *
         * @param parentScope The scope of the parent dependency, may be {@code null}.
         * @param childScope The scope of the child dependency, may be {@code null}.
         * @noreference This class is not intended to be instantiated by clients in production code, the constructor may
         *              change without notice and only exists to enable unit testing.
         */
        public ScopeContext(String parentScope, String childScope) {
            this.parentScope = (parentScope != null) ? parentScope : "";
            derivedScope = (childScope != null) ? childScope : "";
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
    public static final class ConflictItem {
        final CRNode crNode;

        // nodes can share child lists, we care about the unique owner of a child node which is the child list
        final List<DependencyNode> parent;

        // only for debugging/toString() to help identify the parent node(s)
        final Artifact artifact;

        // is mutable as removeLosers will mutate it (if Verbosity==STANDARD)
        final DependencyNode node;

        final int depth;

        // we start with String and update to Set<String> if needed
        final String scope;

        // bit field of OPTIONAL_FALSE and OPTIONAL_TRUE
        final int optionalities;

        /**
         * Bit flag indicating whether one or more paths consider the dependency non-optional.
         */
        public static final int OPTIONAL_FALSE = 0x01;

        /**
         * Bit flag indicating whether one or more paths consider the dependency optional.
         */
        public static final int OPTIONAL_TRUE = 0x02;

        ConflictItem(CRNode crNode) {
            this.crNode = crNode;
            if (crNode.parent != null) {
                DependencyNode parent = crNode.parent.dn;
                this.parent = parent.getChildren();
                this.artifact = parent.getArtifact();
            } else {
                this.parent = null;
                this.artifact = null;
            }
            this.node = crNode.dn;
            this.depth = crNode.depth;
            this.scope = crNode.scope;
            this.optionalities = crNode.optional ? OPTIONAL_TRUE : OPTIONAL_FALSE;
        }

        /**
         * Determines whether the specified conflict item is a sibling of this item.
         *
         * @param item The other conflict item, must not be {@code null}.
         * @return {@code true} if the given item has the same parent as this item, {@code false} otherwise.
         */
        public boolean isSibling(ConflictItem item) {
            return parent == item.parent;
        }

        /**
         * Gets the dependency node involved in the conflict.
         *
         * @return The involved dependency node, never {@code null}.
         */
        public DependencyNode getNode() {
            return node;
        }

        /**
         * Gets the dependency involved in the conflict, short for {@code getNode.getDependency()}.
         *
         * @return The involved dependency, never {@code null}.
         */
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
        public int getDepth() {
            return depth;
        }

        /**
         * Gets the derived scopes of the dependency. In general, the same dependency node could be reached via
         * different paths and each path might result in a different derived scope.
         *
         * @see ScopeDeriver
         * @return The (read-only) set of derived scopes of the dependency, never {@code null}.
         */
        public Collection<String> getScopes() {
            return Collections.singleton(scope);
        }

        /**
         * Gets the derived optionalities of the dependency. In general, the same dependency node could be reached via
         * different paths and each path might result in a different derived optionality.
         *
         * @return A bit field consisting of {@link ConflictResolver.ConflictItem#OPTIONAL_FALSE} and/or
         *         {@link ConflictResolver.ConflictItem#OPTIONAL_TRUE} indicating the derived optionalities the
         *         dependency was encountered with.
         */
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
     * @see VersionSelector
     * @see ScopeSelector
     * @noinstantiate This class is not intended to be instantiated by clients in production code, the constructor may
     *                change without notice and only exists to enable unit testing.
     */
    public static final class ConflictContext {
        final DependencyNode root;

        final Map<DependencyNode, String> conflictIds;

        final Collection<ConflictItem> items;

        final String conflictId;

        ConflictItem winner;

        String scope;

        Boolean optional;

        ConflictContext(
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
        public DependencyNode getRoot() {
            return root;
        }

        /**
         * Determines whether the specified dependency node belongs to this conflict context.
         *
         * @param node The dependency node to check, must not be {@code null}.
         * @return {@code true} if the given node belongs to this conflict context, {@code false} otherwise.
         */
        public boolean isIncluded(DependencyNode node) {
            return conflictId.equals(conflictIds.get(node));
        }

        /**
         * Gets the collection of conflict items in this context.
         *
         * @return The (read-only) collection of conflict items in this context, never {@code null}.
         */
        public Collection<ConflictItem> getItems() {
            return items;
        }

        /**
         * Gets the conflict item which has been selected as the winner among the conflicting dependencies.
         *
         * @return The winning conflict item or {@code null} if not set yet.
         */
        public ConflictItem getWinner() {
            return winner;
        }

        /**
         * Sets the conflict item which has been selected as the winner among the conflicting dependencies.
         *
         * @param winner The winning conflict item, may be {@code null}.
         */
        public void setWinner(ConflictItem winner) {
            this.winner = winner;
        }

        /**
         * Gets the effective scope of the winning dependency.
         *
         * @return The effective scope of the winning dependency or {@code null} if none.
         */
        public String getScope() {
            return scope;
        }

        /**
         * Sets the effective scope of the winning dependency.
         *
         * @param scope The effective scope, may be {@code null}.
         */
        public void setScope(String scope) {
            this.scope = scope;
        }

        /**
         * Gets the effective optional flag of the winning dependency.
         *
         * @return The effective optional flag or {@code null} if none.
         */
        public Boolean getOptional() {
            return optional;
        }

        /**
         * Sets the effective optional flag of the winning dependency.
         *
         * @param optional The effective optional flag, may be {@code null}.
         */
        public void setOptional(Boolean optional) {
            this.optional = optional;
        }

        @Override
        public String toString() {
            return winner + " @ " + scope + " < " + items;
        }
    }

    /**
     * An extension point of {@link ConflictResolver} that determines the winner among conflicting dependencies. The
     * winning node (and its children) will be retained in the dependency graph, the other nodes will get removed. The
     * version selector does not need to deal with potential scope conflicts, these will be addressed afterwards by the
     * {@link ScopeSelector}.
     * <p>
     * <strong>Note:</strong> Implementations must be stateless.
     */
    public abstract static class VersionSelector {

        /**
         * Retrieves the version selector for use during the specified graph transformation. The conflict resolver calls
         * this method once per
         * {@link ConflictResolver#transformGraph(DependencyNode, DependencyGraphTransformationContext)} invocation to
         * allow implementations to prepare any auxiliary data that is needed for their operation. Given that
         * implementations must be stateless, a new instance needs to be returned to hold such auxiliary data. The
         * default implementation simply returns the current instance which is appropriate for implementations which do
         * not require auxiliary data.
         *
         * @param root The root node of the (possibly cyclic!) graph to transform, must not be {@code null}.
         * @param context The graph transformation context, must not be {@code null}.
         * @return The scope deriver to use for the given graph transformation, never {@code null}.
         * @throws RepositoryException If the instance could not be retrieved.
         */
        public VersionSelector getInstance(DependencyNode root, DependencyGraphTransformationContext context)
                throws RepositoryException {
            return this;
        }

        /**
         * Determines the winning node among conflicting dependencies. Implementations will usually iterate
         * {@link ConflictContext#getItems()}, inspect {@link ConflictItem#getNode()} and eventually call
         * {@link ConflictContext#setWinner(ConflictResolver.ConflictItem)} to deliver the winner. Failure to select a
         * winner will automatically fail the entire conflict resolution.
         *
         * @param context The conflict context, must not be {@code null}.
         * @throws RepositoryException If the version selection failed.
         */
        public abstract void selectVersion(ConflictContext context) throws RepositoryException;
    }

    /**
     * An extension point of {@link ConflictResolver} that determines the effective scope of a dependency from a
     * potentially conflicting set of {@link ScopeDeriver derived scopes}. The scope selector gets invoked after the
     * {@link VersionSelector} has picked the winning node.
     * <p>
     * <strong>Note:</strong> Implementations must be stateless.
     */
    public abstract static class ScopeSelector {

        /**
         * Retrieves the scope selector for use during the specified graph transformation. The conflict resolver calls
         * this method once per
         * {@link ConflictResolver#transformGraph(DependencyNode, DependencyGraphTransformationContext)} invocation to
         * allow implementations to prepare any auxiliary data that is needed for their operation. Given that
         * implementations must be stateless, a new instance needs to be returned to hold such auxiliary data. The
         * default implementation simply returns the current instance which is appropriate for implementations which do
         * not require auxiliary data.
         *
         * @param root The root node of the (possibly cyclic!) graph to transform, must not be {@code null}.
         * @param context The graph transformation context, must not be {@code null}.
         * @return The scope selector to use for the given graph transformation, never {@code null}.
         * @throws RepositoryException If the instance could not be retrieved.
         */
        public ScopeSelector getInstance(DependencyNode root, DependencyGraphTransformationContext context)
                throws RepositoryException {
            return this;
        }

        /**
         * Determines the effective scope of the dependency given by {@link ConflictContext#getWinner()}.
         * Implementations will usually iterate {@link ConflictContext#getItems()}, inspect
         * {@link ConflictItem#getScopes()} and eventually call {@link ConflictContext#setScope(String)} to deliver the
         * effective scope.
         *
         * @param context The conflict context, must not be {@code null}.
         * @throws RepositoryException If the scope selection failed.
         */
        public abstract void selectScope(ConflictContext context) throws RepositoryException;
    }

    /**
     * An extension point of {@link ConflictResolver} that determines the scope of a dependency in relation to the scope
     * of its parent.
     * <p>
     * <strong>Note:</strong> Implementations must be stateless.
     */
    public abstract static class ScopeDeriver {

        /**
         * Retrieves the scope deriver for use during the specified graph transformation. The conflict resolver calls
         * this method once per
         * {@link ConflictResolver#transformGraph(DependencyNode, DependencyGraphTransformationContext)} invocation to
         * allow implementations to prepare any auxiliary data that is needed for their operation. Given that
         * implementations must be stateless, a new instance needs to be returned to hold such auxiliary data. The
         * default implementation simply returns the current instance which is appropriate for implementations which do
         * not require auxiliary data.
         *
         * @param root The root node of the (possibly cyclic!) graph to transform, must not be {@code null}.
         * @param context The graph transformation context, must not be {@code null}.
         * @return The scope deriver to use for the given graph transformation, never {@code null}.
         * @throws RepositoryException If the instance could not be retrieved.
         */
        public ScopeDeriver getInstance(DependencyNode root, DependencyGraphTransformationContext context)
                throws RepositoryException {
            return this;
        }

        /**
         * Determines the scope of a dependency in relation to the scope of its parent. Implementors need to call
         * {@link ScopeContext#setDerivedScope(String)} to deliver the result of their calculation. If said method is
         * not invoked, the conflict resolver will assume the scope of the child dependency remains unchanged.
         *
         * @param context The scope context, must not be {@code null}.
         * @throws RepositoryException If the scope deriviation failed.
         */
        public abstract void deriveScope(ScopeContext context) throws RepositoryException;
    }

    /**
     * An extension point of {@link ConflictResolver} that determines the effective optional flag of a dependency from a
     * potentially conflicting set of derived optionalities. The optionality selector gets invoked after the
     * {@link VersionSelector} has picked the winning node.
     * <p>
     * <strong>Note:</strong> Implementations must be stateless.
     */
    public abstract static class OptionalitySelector {

        /**
         * Retrieves the optionality selector for use during the specified graph transformation. The conflict resolver
         * calls this method once per
         * {@link ConflictResolver#transformGraph(DependencyNode, DependencyGraphTransformationContext)} invocation to
         * allow implementations to prepare any auxiliary data that is needed for their operation. Given that
         * implementations must be stateless, a new instance needs to be returned to hold such auxiliary data. The
         * default implementation simply returns the current instance which is appropriate for implementations which do
         * not require auxiliary data.
         *
         * @param root The root node of the (possibly cyclic!) graph to transform, must not be {@code null}.
         * @param context The graph transformation context, must not be {@code null}.
         * @return The optionality selector to use for the given graph transformation, never {@code null}.
         * @throws RepositoryException If the instance could not be retrieved.
         */
        public OptionalitySelector getInstance(DependencyNode root, DependencyGraphTransformationContext context)
                throws RepositoryException {
            return this;
        }

        /**
         * Determines the effective optional flag of the dependency given by {@link ConflictContext#getWinner()}.
         * Implementations will usually iterate {@link ConflictContext#getItems()}, inspect
         * {@link ConflictItem#getOptionalities()} and eventually call {@link ConflictContext#setOptional(Boolean)} to
         * deliver the effective optional flag.
         *
         * @param context The conflict context, must not be {@code null}.
         * @throws RepositoryException If the optionality selection failed.
         */
        public abstract void selectOptionality(ConflictContext context) throws RepositoryException;
    }
}
