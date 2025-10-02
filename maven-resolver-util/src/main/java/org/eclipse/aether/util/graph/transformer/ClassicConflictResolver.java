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
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;

import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.DependencyGraphTransformationContext;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyManagementSubject;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.util.artifact.ArtifactIdUtils;

import static java.util.Objects.requireNonNull;

/**
 * A legacy dependency graph transformer that resolves version and scope conflicts among dependencies.
 * This implementation maintains backward compatibility with Maven 3.x and Resolver 1.x behavior but has
 * O(N²) worst-case performance characteristics. For new projects, consider using {@link PathConflictResolver}.
 * <p>
 * For a given set of conflicting nodes, one node will be chosen as the winner. How losing nodes are handled
 * depends on the configured verbosity level: they may be removed entirely, have their children removed, or
 * be left in place with conflict information. The exact rules by which a winning node and its effective scope
 * are determined are controlled by user-supplied implementations of {@link ConflictResolver.VersionSelector}, {@link ConflictResolver.ScopeSelector},
 * {@link ConflictResolver.OptionalitySelector} and {@link ConflictResolver.ScopeDeriver}.
 * <p>
 * <strong>Performance Characteristics:</strong>
 * <ul>
 * <li><strong>Time Complexity:</strong> O(N²) worst-case where N is the number of dependency nodes</li>
 * <li><strong>Memory Usage:</strong> Modifies the dependency graph in-place</li>
 * <li><strong>Scalability:</strong> Performance degrades significantly on large multi-module projects</li>
 * </ul>
 * <p>
 * <strong>Algorithm Overview:</strong>
 * <ol>
 * <li><strong>Depth-First Traversal:</strong> Walks the dependency graph depth-first</li>
 * <li><strong>In-Place Modification:</strong> Modifies nodes directly during traversal</li>
 * <li><strong>Conflict Detection:</strong> Identifies conflicts by comparing conflict IDs</li>
 * <li><strong>Winner Selection:</strong> Uses provided selectors to choose winners</li>
 * <li><strong>Loser Removal:</strong> Removes or marks losing nodes during traversal</li>
 * </ol>
 * <p>
 * <strong>When to Use:</strong>
 * <ul>
 * <li>Exact backward compatibility with Maven 3.x/Resolver 1.x is required</li>
 * <li>Debugging performance differences between old and new algorithms</li>
 * <li>Small projects where performance is not a concern</li>
 * <li>Testing and validation scenarios</li>
 * </ul>
 * <p>
 * <strong>Migration Recommendation:</strong> New projects should use {@link PathConflictResolver} for better
 * performance. This implementation is retained primarily for compatibility and testing purposes.
 * <p>
 * <strong>Implementation Note:</strong> This conflict resolver is identical to the one used in Maven 3/Resolver 1.x.
 * The implementation may produce O(N²) worst-case performance on projects with many small conflict groups
 * (typically one member each), which is common in large multi-module projects.
 *
 * @see PathConflictResolver
 * @since 2.0.11
 */
public final class ClassicConflictResolver extends ConflictResolver {
    private final ConflictResolver.VersionSelector versionSelector;
    private final ConflictResolver.ScopeSelector scopeSelector;
    private final ConflictResolver.ScopeDeriver scopeDeriver;
    private final ConflictResolver.OptionalitySelector optionalitySelector;

    /**
     * Creates a new conflict resolver instance with the specified hooks.
     *
     * @param versionSelector the version selector to use, must not be {@code null}
     * @param scopeSelector the scope selector to use, must not be {@code null}
     * @param optionalitySelector the optionality selector ot use, must not be {@code null}
     * @param scopeDeriver the scope deriver to use, must not be {@code null}
     */
    public ClassicConflictResolver(
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

        State state = new State(node, conflictIds, sortedConflictIds.size(), context);
        for (Iterator<String> it = sortedConflictIds.iterator(); it.hasNext(); ) {
            String conflictId = it.next();

            // reset data structures for next graph walk
            state.prepare(conflictId, cyclicPredecessors.get(conflictId));

            // find nodes with the current conflict id and while walking the graph (more deeply), nuke leftover losers
            gatherConflictItems(node, state);

            // now that we know the min depth of the parents, update depth of conflict items
            state.finish();

            // earlier runs might have nuked all parents of the current conflict id, so it might not exist anymore
            if (!state.items.isEmpty()) {
                ConflictContext ctx = state.conflictCtx;
                state.versionSelector.selectVersion(ctx);
                if (ctx.winner == null) {
                    throw new RepositoryException("conflict resolver did not select winner among " + state.items);
                }
                DependencyNode winner = ((ConflictItem) (ctx.winner)).node;

                state.scopeSelector.selectScope(ctx);
                if (ConflictResolver.Verbosity.NONE != state.verbosity) {
                    winner.setData(
                            ConflictResolver.NODE_DATA_ORIGINAL_SCOPE,
                            winner.getDependency().getScope());
                }
                winner.setScope(ctx.scope);

                state.optionalitySelector.selectOptionality(ctx);
                if (ConflictResolver.Verbosity.NONE != state.verbosity) {
                    winner.setData(
                            ConflictResolver.NODE_DATA_ORIGINAL_OPTIONALITY,
                            winner.getDependency().isOptional());
                }
                winner.setOptional(ctx.optional);

                removeLosers(state);
            }

            // record the winner so we can detect leftover losers during future graph walks
            state.winner();

            // in case of cycles, trigger final graph walk to ensure all leftover losers are gone
            if (!it.hasNext() && !conflictIdCycles.isEmpty() && state.conflictCtx.winner != null) {
                DependencyNode winner = ((ConflictItem) (state.conflictCtx).winner).node;
                // Note: using non-existing key here (empty) as that one for sure was not met
                state.prepare("", null);
                gatherConflictItems(winner, state);
            }
        }

        if (stats != null) {
            long time2 = System.nanoTime();
            stats.put("ConflictResolver.totalTime", time2 - time1);
            stats.put("ConflictResolver.conflictItemCount", state.totalConflictItems);
        }

        return node;
    }

    private boolean gatherConflictItems(DependencyNode node, State state) throws RepositoryException {
        String conflictId = state.conflictIds.get(node);
        if (state.currentId.equals(conflictId)) {
            // found it, add conflict item (if not already done earlier by another path)
            state.add(node);
            // we don't recurse here so we might miss losers beneath us, those will be nuked during future walks below
        } else if (state.loser(node, conflictId)) {
            // found a leftover loser (likely in a cycle) of an already processed conflict id, tell caller to nuke it
            return false;
        } else if (state.push(node, conflictId)) {
            // found potential parent, no cycle and not visited before with the same derived scope, so recurse
            for (Iterator<DependencyNode> it = node.getChildren().iterator(); it.hasNext(); ) {
                DependencyNode child = it.next();
                if (!gatherConflictItems(child, state)) {
                    it.remove();
                }
            }
            state.pop();
        }
        return true;
    }

    private static void removeLosers(State state) {
        ConflictItem winner = ((ConflictItem) state.conflictCtx.winner);
        String winnerArtifactId = ArtifactIdUtils.toId(winner.node.getArtifact());
        List<DependencyNode> previousParent = null;
        ListIterator<DependencyNode> childIt = null;
        HashSet<String> toRemoveIds = new HashSet<>();
        for (ConflictItem item : state.items) {
            if (item == winner) {
                continue;
            }
            if (item.parent != previousParent) {
                childIt = item.parent.listIterator();
                previousParent = item.parent;
            }
            while (childIt.hasNext()) {
                DependencyNode child = childIt.next();
                if (child == item.node) {
                    // NONE: just remove it and done
                    if (ConflictResolver.Verbosity.NONE == state.verbosity) {
                        childIt.remove();
                        break;
                    }

                    // STANDARD: doing extra bookkeeping to select "which nodes to remove"
                    if (ConflictResolver.Verbosity.STANDARD == state.verbosity) {
                        String childArtifactId = ArtifactIdUtils.toId(child.getArtifact());
                        // if two IDs are equal, it means "there is nearest", not conflict per se.
                        // In that case we do NOT allow this child to be removed (but remove others)
                        // and this keeps us safe from iteration (and in general, version) ordering
                        // as we explicitly leave out ID that is "nearest found" state.
                        //
                        // This tackles version ranges mostly, where ranges are turned into list of
                        // several nodes in collector (as many were discovered, ie. from metadata), and
                        // old code would just "mark" the first hit as conflict, and remove the rest,
                        // even if rest could contain "more suitable" version, that is not conflicting/diverging.
                        // This resulted in verbose mode transformed tree, that was misrepresenting things
                        // for dependency convergence calculations: it represented state like parent node
                        // depends on "wrong" version (diverge), while "right" version was present (but removed)
                        // as well, as it was contained in parents version range.
                        if (!Objects.equals(winnerArtifactId, childArtifactId)) {
                            toRemoveIds.add(childArtifactId);
                        }
                    }

                    // FULL: just record the facts
                    DependencyNode loser = new DefaultDependencyNode(child);
                    loser.setData(ConflictResolver.NODE_DATA_WINNER, winner.node);
                    loser.setData(
                            ConflictResolver.NODE_DATA_ORIGINAL_SCOPE,
                            loser.getDependency().getScope());
                    loser.setData(
                            ConflictResolver.NODE_DATA_ORIGINAL_OPTIONALITY,
                            loser.getDependency().isOptional());
                    loser.setScope(item.getScopes().iterator().next());
                    loser.setChildren(Collections.emptyList());
                    childIt.set(loser);
                    item.node = loser;
                    break;
                }
            }
        }

        // 2nd pass to apply "standard" verbosity: leaving only 1 loser, but with care
        if (ConflictResolver.Verbosity.STANDARD == state.verbosity && !toRemoveIds.isEmpty()) {
            previousParent = null;
            for (ConflictItem item : state.items) {
                if (item == winner) {
                    continue;
                }
                if (item.parent != previousParent) {
                    childIt = item.parent.listIterator();
                    previousParent = item.parent;
                }
                while (childIt.hasNext()) {
                    DependencyNode child = childIt.next();
                    if (child == item.node) {
                        String childArtifactId = ArtifactIdUtils.toId(child.getArtifact());
                        if (toRemoveIds.contains(childArtifactId)
                                && relatedSiblingsCount(child.getArtifact(), item.parent) > 1) {
                            childIt.remove();
                        }
                        break;
                    }
                }
            }
        }

        // there might still be losers beneath the winner (e.g. in case of cycles)
        // those will be nuked during future graph walks when we include the winner in the recursion
    }

    private static long relatedSiblingsCount(Artifact artifact, List<DependencyNode> parent) {
        String ga = artifact.getGroupId() + ":" + artifact.getArtifactId();
        return parent.stream()
                .map(DependencyNode::getArtifact)
                .filter(a -> ga.equals(a.getGroupId() + ":" + a.getArtifactId()))
                .count();
    }

    static final class NodeInfo {

        /**
         * The smallest depth at which the node was seen, used for "the" depth of its conflict items.
         */
        int minDepth;

        /**
         * The set of derived scopes the node was visited with, used to check whether an already seen node needs to be
         * revisited again in context of another scope. To conserve memory, we start with {@code String} and update to
         * {@code Set<String>} if needed.
         */
        Object derivedScopes;

        /**
         * The set of derived optionalities the node was visited with, used to check whether an already seen node needs
         * to be revisited again in context of another optionality. To conserve memory, encoded as bit field (bit 0 ->
         * optional=false, bit 1 -> optional=true).
         */
        int derivedOptionalities;

        /**
         * The conflict items which are immediate children of the node, used to easily update those conflict items after
         * a new parent scope/optionality was encountered.
         */
        List<ConflictItem> children;

        static final int CHANGE_SCOPE = 0x01;

        static final int CHANGE_OPTIONAL = 0x02;

        private static final int OPT_FALSE = 0x01;

        private static final int OPT_TRUE = 0x02;

        NodeInfo(int depth, String derivedScope, boolean optional) {
            minDepth = depth;
            derivedScopes = derivedScope;
            derivedOptionalities = optional ? OPT_TRUE : OPT_FALSE;
        }

        @SuppressWarnings("unchecked")
        int update(int depth, String derivedScope, boolean optional) {
            if (depth < minDepth) {
                minDepth = depth;
            }
            int changes;
            if (derivedScopes.equals(derivedScope)) {
                changes = 0;
            } else if (derivedScopes instanceof Collection) {
                changes = ((Collection<String>) derivedScopes).add(derivedScope) ? CHANGE_SCOPE : 0;
            } else {
                Collection<String> scopes = new HashSet<>();
                scopes.add((String) derivedScopes);
                scopes.add(derivedScope);
                derivedScopes = scopes;
                changes = CHANGE_SCOPE;
            }
            int bit = optional ? OPT_TRUE : OPT_FALSE;
            if ((derivedOptionalities & bit) == 0) {
                derivedOptionalities |= bit;
                changes |= CHANGE_OPTIONAL;
            }
            return changes;
        }

        void add(ConflictItem item) {
            if (children == null) {
                children = new ArrayList<>(1);
            }
            children.add(item);
        }
    }

    final class State {

        /**
         * The conflict id currently processed.
         */
        String currentId;

        /**
         * Stats counter.
         */
        int totalConflictItems;

        /**
         * Flag whether we should keep losers in the graph to enable visualization/troubleshooting of conflicts.
         */
        final ConflictResolver.Verbosity verbosity;

        /**
         * A mapping from conflict id to winner node, helps to recognize nodes that have their effective
         * scope&optionality set or are leftovers from previous removals.
         */
        final Map<String, DependencyNode> resolvedIds;

        /**
         * The set of conflict ids which could apply to ancestors of nodes with the current conflict id, used to avoid
         * recursion early on. This is basically a superset of the key set of resolvedIds, the additional ids account
         * for cyclic dependencies.
         */
        final Collection<String> potentialAncestorIds;

        /**
         * The output from the conflict marker.
         */
        final Map<DependencyNode, String> conflictIds;

        /**
         * The conflict items we have gathered so far for the current conflict id.
         */
        final List<ConflictItem> items;

        /**
         * The (conceptual) mapping from nodes to extra infos, technically keyed by the node's child list which better
         * captures the identity of a node since we're basically concerned with effects towards children.
         */
        final Map<List<DependencyNode>, NodeInfo> infos;

        /**
         * The set of nodes on the DFS stack to detect cycles, technically keyed by the node's child list to match the
         * dirty graph structure produced by the dependency collector for cycles.
         */
        final Map<List<DependencyNode>, Boolean> stack;

        /**
         * The stack of parent nodes.
         */
        final List<DependencyNode> parentNodes;

        /**
         * The stack of derived scopes for parent nodes.
         */
        final List<String> parentScopes;

        /**
         * The stack of derived optional flags for parent nodes.
         */
        final List<Boolean> parentOptionals;

        /**
         * The stack of node infos for parent nodes, may contain {@code null} which is used to disable creating new
         * conflict items when visiting their parent again (conflict items are meant to be unique by parent-node combo).
         */
        final List<NodeInfo> parentInfos;

        /**
         * The conflict context passed to the version/scope/optionality selectors, updated as we move along rather than
         * recreated to avoid tmp objects.
         */
        final ConflictContext conflictCtx;

        /**
         * The scope context passed to the scope deriver, updated as we move along rather than recreated to avoid tmp
         * objects.
         */
        final ScopeContext scopeCtx;

        /**
         * The effective version selector, i.e. after initialization.
         */
        final ConflictResolver.VersionSelector versionSelector;

        /**
         * The effective scope selector, i.e. after initialization.
         */
        final ConflictResolver.ScopeSelector scopeSelector;

        /**
         * The effective scope deriver, i.e. after initialization.
         */
        final ConflictResolver.ScopeDeriver scopeDeriver;

        /**
         * The effective optionality selector, i.e. after initialization.
         */
        final ConflictResolver.OptionalitySelector optionalitySelector;

        State(
                DependencyNode root,
                Map<DependencyNode, String> conflictIds,
                int conflictIdCount,
                DependencyGraphTransformationContext context)
                throws RepositoryException {
            this.conflictIds = conflictIds;
            this.verbosity = ConflictResolver.getVerbosity(context.getSession());
            potentialAncestorIds = new HashSet<>(conflictIdCount * 2);
            resolvedIds = new HashMap<>(conflictIdCount * 2);
            items = new ArrayList<>(256);
            infos = new IdentityHashMap<>(64);
            stack = new IdentityHashMap<>(64);
            parentNodes = new ArrayList<>(64);
            parentScopes = new ArrayList<>(64);
            parentOptionals = new ArrayList<>(64);
            parentInfos = new ArrayList<>(64);
            conflictCtx = new ConflictContext(root, conflictIds, items);
            scopeCtx = new ScopeContext(null, null);
            versionSelector = ClassicConflictResolver.this.versionSelector.getInstance(root, context);
            scopeSelector = ClassicConflictResolver.this.scopeSelector.getInstance(root, context);
            scopeDeriver = ClassicConflictResolver.this.scopeDeriver.getInstance(root, context);
            optionalitySelector = ClassicConflictResolver.this.optionalitySelector.getInstance(root, context);
        }

        void prepare(String conflictId, Collection<String> cyclicPredecessors) {
            currentId = conflictId;
            conflictCtx.conflictId = conflictId;
            conflictCtx.winner = null;
            conflictCtx.scope = null;
            conflictCtx.optional = null;
            items.clear();
            infos.clear();
            if (cyclicPredecessors != null) {
                potentialAncestorIds.addAll(cyclicPredecessors);
            }
        }

        void finish() {
            List<DependencyNode> previousParent = null;
            int previousDepth = 0;
            totalConflictItems += items.size();
            for (ListIterator<ConflictItem> iterator = items.listIterator(items.size()); iterator.hasPrevious(); ) {
                ConflictItem item = iterator.previous();
                if (item.parent == previousParent) {
                    item.depth = previousDepth;
                } else if (item.parent != null) {
                    previousParent = item.parent;
                    NodeInfo info = infos.get(previousParent);
                    previousDepth = info.minDepth + 1;
                    item.depth = previousDepth;
                }
            }
            potentialAncestorIds.add(currentId);
        }

        void winner() {
            resolvedIds.put(currentId, (conflictCtx.winner != null) ? ((ConflictItem) conflictCtx.winner).node : null);
        }

        boolean loser(DependencyNode node, String conflictId) {
            DependencyNode winner = resolvedIds.get(conflictId);
            return winner != null && winner != node;
        }

        boolean push(DependencyNode node, String conflictId) throws RepositoryException {
            if (conflictId == null) {
                if (node.getDependency() != null) {
                    if (node.getData().get(ConflictResolver.NODE_DATA_WINNER) != null) {
                        return false;
                    }
                    throw new RepositoryException("missing conflict id for node " + node);
                }
            } else if (!potentialAncestorIds.contains(conflictId)) {
                return false;
            }

            List<DependencyNode> graphNode = node.getChildren();
            if (stack.put(graphNode, Boolean.TRUE) != null) {
                return false;
            }

            int depth = depth();
            String scope = deriveScope(node, conflictId);
            boolean optional = deriveOptional(node, conflictId);
            NodeInfo info = infos.get(graphNode);
            if (info == null) {
                info = new NodeInfo(depth, scope, optional);
                infos.put(graphNode, info);
                parentInfos.add(info);
                parentNodes.add(node);
                parentScopes.add(scope);
                parentOptionals.add(optional);
            } else {
                int changes = info.update(depth, scope, optional);
                if (changes == 0) {
                    stack.remove(graphNode);
                    return false;
                }
                parentInfos.add(null); // disable creating new conflict items, we update the existing ones below
                parentNodes.add(node);
                parentScopes.add(scope);
                parentOptionals.add(optional);
                if (info.children != null) {
                    if ((changes & NodeInfo.CHANGE_SCOPE) != 0) {
                        ListIterator<ConflictItem> itemIterator = info.children.listIterator(info.children.size());
                        while (itemIterator.hasPrevious()) {
                            ConflictItem item = itemIterator.previous();
                            String childScope = deriveScope(item.node, null);
                            item.addScope(childScope);
                        }
                    }
                    if ((changes & NodeInfo.CHANGE_OPTIONAL) != 0) {
                        ListIterator<ConflictItem> itemIterator = info.children.listIterator(info.children.size());
                        while (itemIterator.hasPrevious()) {
                            ConflictItem item = itemIterator.previous();
                            boolean childOptional = deriveOptional(item.node, null);
                            item.addOptional(childOptional);
                        }
                    }
                }
            }

            return true;
        }

        void pop() {
            int last = parentInfos.size() - 1;
            parentInfos.remove(last);
            parentScopes.remove(last);
            parentOptionals.remove(last);
            DependencyNode node = parentNodes.remove(last);
            stack.remove(node.getChildren());
        }

        void add(DependencyNode node) throws RepositoryException {
            DependencyNode parent = parent();
            if (parent == null) {
                ConflictItem item = newConflictItem(parent, node);
                items.add(item);
            } else {
                NodeInfo info = parentInfos.get(parentInfos.size() - 1);
                if (info != null) {
                    ConflictItem item = newConflictItem(parent, node);
                    info.add(item);
                    items.add(item);
                }
            }
        }

        private ConflictItem newConflictItem(DependencyNode parent, DependencyNode node) throws RepositoryException {
            return new ConflictItem(parent, node, deriveScope(node, null), deriveOptional(node, null));
        }

        private int depth() {
            return parentNodes.size();
        }

        private DependencyNode parent() {
            int size = parentNodes.size();
            return (size <= 0) ? null : parentNodes.get(size - 1);
        }

        private String deriveScope(DependencyNode node, String conflictId) throws RepositoryException {
            if (node.isManagedSubjectEnforced(DependencyManagementSubject.SCOPE)
                    || (conflictId != null && resolvedIds.containsKey(conflictId))) {
                return scope(node.getDependency());
            }

            int depth = parentNodes.size();
            scopes(depth, node.getDependency());
            if (depth > 0) {
                scopeDeriver.deriveScope(scopeCtx);
            }
            return scopeCtx.derivedScope;
        }

        private void scopes(int parent, Dependency child) {
            scopeCtx.parentScope = (parent > 0) ? parentScopes.get(parent - 1) : null;
            scopeCtx.derivedScope = scope(child);
            scopeCtx.childScope = scope(child);
        }

        private String scope(Dependency dependency) {
            return (dependency != null) ? dependency.getScope() : null;
        }

        private boolean deriveOptional(DependencyNode node, String conflictId) {
            Dependency dep = node.getDependency();
            boolean optional = (dep != null) && dep.isOptional();
            if (optional
                    || node.isManagedSubjectEnforced(DependencyManagementSubject.OPTIONAL)
                    || (conflictId != null && resolvedIds.containsKey(conflictId))) {
                return optional;
            }
            int depth = parentNodes.size();
            return (depth > 0) ? parentOptionals.get(depth - 1) : false;
        }
    }

    /**
     * A context used to hold information that is relevant for deriving the scope of a child dependency.
     *
     * @see ConflictResolver.ScopeDeriver
     * @noinstantiate This class is not intended to be instantiated by clients in production code, the constructor may
     *                change without notice and only exists to enable unit testing
     */
    private static final class ScopeContext extends ConflictResolver.ScopeContext {
        private String parentScope;
        private String childScope;
        private String derivedScope;

        /**
         * Creates a new scope context with the specified properties.
         *
         * @param parentScope the scope of the parent dependency, may be {@code null}
         * @param childScope the scope of the child dependency, may be {@code null}
         * @noreference This class is not intended to be instantiated by clients in production code, the constructor may
         *              change without notice and only exists to enable unit testing
         */
        private ScopeContext(String parentScope, String childScope) {
            this.parentScope = (parentScope != null) ? parentScope : "";
            derivedScope = (childScope != null) ? childScope : "";
            this.childScope = (childScope != null) ? childScope : "";
        }

        /**
         * Gets the scope of the parent dependency. This is usually the scope that was derived by earlier invocations of
         * the scope deriver.
         *
         * @return the scope of the parent dependency, never {@code null}
         */
        @Override
        public String getParentScope() {
            return parentScope;
        }

        /**
         * Gets the original scope of the child dependency. This is the scope that was declared in the artifact
         * descriptor of the parent dependency.
         *
         * @return the original scope of the child dependency, never {@code null}
         */
        @Override
        public String getChildScope() {
            return childScope;
        }

        /**
         * Gets the derived scope of the child dependency. This is initially equal to {@link #getChildScope()} until the
         * scope deriver makes changes.
         *
         * @return the derived scope of the child dependency, never {@code null}
         */
        @Override
        public String getDerivedScope() {
            return derivedScope;
        }

        /**
         * Sets the derived scope of the child dependency.
         *
         * @param derivedScope the derived scope of the dependency, may be {@code null}
         */
        @Override
        public void setDerivedScope(String derivedScope) {
            this.derivedScope = (derivedScope != null) ? derivedScope : "";
        }
    }

    /**
     * A conflicting dependency.
     *
     * @noinstantiate This class is not intended to be instantiated by clients in production code, the constructor may
     *                change without notice and only exists to enable unit testing
     */
    private static final class ConflictItem extends ConflictResolver.ConflictItem {

        // nodes can share child lists, we care about the unique owner of a child node which is the child list
        final List<DependencyNode> parent;

        // only for debugging/toString() to help identify the parent node(s)
        final Artifact artifact;

        // is mutable as removeLosers will mutate it (if Verbosity==STANDARD)
        DependencyNode node;

        int depth;

        // we start with String and update to Set<String> if needed
        Object scopes;

        // bit field of OPTIONAL_FALSE and OPTIONAL_TRUE
        int optionalities;

        private ConflictItem(DependencyNode parent, DependencyNode node, String scope, boolean optional) {
            if (parent != null) {
                this.parent = parent.getChildren();
                this.artifact = parent.getArtifact();
            } else {
                this.parent = null;
                this.artifact = null;
            }
            this.node = node;
            this.scopes = scope;
            this.optionalities = optional ? OPTIONAL_TRUE : OPTIONAL_FALSE;
        }

        /**
         * Determines whether the specified conflict item is a sibling of this item.
         *
         * @param item the other conflict item, must not be {@code null}
         * @return {@code true} if the given item has the same parent as this item, {@code false} otherwise
         */
        @Override
        public boolean isSibling(ConflictResolver.ConflictItem item) {
            return parent == ((ConflictItem) item).parent;
        }

        /**
         * Gets the dependency node involved in the conflict.
         *
         * @return the involved dependency node, never {@code null}
         */
        @Override
        public DependencyNode getNode() {
            return node;
        }

        /**
         * Gets the dependency involved in the conflict, short for {@code getNode.getDependency()}.
         *
         * @return the involved dependency, never {@code null}
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
         * @return the zero-based depth of the node in the graph
         */
        @Override
        public int getDepth() {
            return depth;
        }

        /**
         * Gets the derived scopes of the dependency. In general, the same dependency node could be reached via
         * different paths and each path might result in a different derived scope.
         *
         * @return the (read-only) set of derived scopes of the dependency, never {@code null}
         * @see ConflictResolver.ScopeDeriver
         */
        @SuppressWarnings("unchecked")
        @Override
        public Collection<String> getScopes() {
            if (scopes instanceof String) {
                return Collections.singleton((String) scopes);
            }
            return (Collection<String>) scopes;
        }

        @SuppressWarnings("unchecked")
        void addScope(String scope) {
            if (scopes instanceof Collection) {
                ((Collection<String>) scopes).add(scope);
            } else if (!scopes.equals(scope)) {
                Collection<String> set = new HashSet<>();
                set.add((String) scopes);
                set.add(scope);
                scopes = set;
            }
        }

        /**
         * Gets the derived optionalities of the dependency. In general, the same dependency node could be reached via
         * different paths and each path might result in a different derived optionality.
         *
         * @return a bit field consisting of {@link ConflictResolver.ConflictItem#OPTIONAL_FALSE} and/or
         *         {@link ConflictResolver.ConflictItem#OPTIONAL_TRUE} indicating the derived optionalities the
         *         dependency was encountered with
         */
        @Override
        public int getOptionalities() {
            return optionalities;
        }

        void addOptional(boolean optional) {
            optionalities |= optional ? OPTIONAL_TRUE : OPTIONAL_FALSE;
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
     *                change without notice and only exists to enable unit testing
     */
    private static final class ConflictContext extends ConflictResolver.ConflictContext {
        final DependencyNode root;
        final Map<DependencyNode, String> conflictIds;
        final Collection<ConflictResolver.ConflictItem> items;

        String conflictId;
        ConflictResolver.ConflictItem winner;
        String scope;
        Boolean optional;

        private ConflictContext(
                DependencyNode root, Map<DependencyNode, String> conflictIds, Collection<ConflictItem> items) {
            this.root = root;
            this.conflictIds = conflictIds;
            this.items = Collections.unmodifiableCollection(items);
        }

        /**
         * Gets the root node of the dependency graph being transformed.
         *
         * @return the root node of the dependency graph, never {@code null}
         */
        @Override
        public DependencyNode getRoot() {
            return root;
        }

        /**
         * Determines whether the specified dependency node belongs to this conflict context.
         *
         * @param node the dependency node to check, must not be {@code null}
         * @return {@code true} if the given node belongs to this conflict context, {@code false} otherwise
         */
        @Override
        public boolean isIncluded(DependencyNode node) {
            return conflictId.equals(conflictIds.get(node));
        }

        /**
         * Gets the collection of conflict items in this context.
         *
         * @return the (read-only) collection of conflict items in this context, never {@code null}
         */
        @Override
        public Collection<ConflictResolver.ConflictItem> getItems() {
            return items;
        }

        /**
         * Gets the conflict item which has been selected as the winner among the conflicting dependencies.
         *
         * @return the winning conflict item or {@code null} if not set yet
         */
        @Override
        public ConflictResolver.ConflictItem getWinner() {
            return winner;
        }

        /**
         * Sets the conflict item which has been selected as the winner among the conflicting dependencies.
         *
         * @param winner the winning conflict item, may be {@code null}
         */
        @Override
        public void setWinner(ConflictResolver.ConflictItem winner) {
            this.winner = winner;
        }

        /**
         * Gets the effective scope of the winning dependency.
         *
         * @return the effective scope of the winning dependency or {@code null} if none
         */
        @Override
        public String getScope() {
            return scope;
        }

        /**
         * Sets the effective scope of the winning dependency.
         *
         * @param scope the effective scope, may be {@code null}
         */
        @Override
        public void setScope(String scope) {
            this.scope = scope;
        }

        /**
         * Gets the effective optional flag of the winning dependency.
         *
         * @return the effective optional flag or {@code null} if none
         */
        @Override
        public Boolean getOptional() {
            return optional;
        }

        /**
         * Sets the effective optional flag of the winning dependency.
         *
         * @param optional the effective optional flag, may be {@code null}
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
