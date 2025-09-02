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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.collection.DependencyGraphTransformationContext;
import org.eclipse.aether.collection.UnsolvableVersionConflictException;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.graph.transformer.ConflictResolver.ConflictContext;
import org.eclipse.aether.util.graph.transformer.ConflictResolver.ConflictItem;
import org.eclipse.aether.util.graph.transformer.ConflictResolver.VersionSelector;
import org.eclipse.aether.util.graph.visitor.PathRecordingDependencyVisitor;
import org.eclipse.aether.util.graph.visitor.TreeDependencyVisitor;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionConstraint;

import static java.util.Objects.requireNonNull;

/**
 * A configurable version selector for use with {@link ConflictResolver} that resolves version conflicts using a
 * specified strategy. If there is no single node that satisfies all encountered version ranges, the selector will fail.
 * Based on configuration, this selector may fail for other reasons as well.
 *
 * @since 2.0.0
 */
public class ConfigurableVersionSelector extends VersionSelector {
    /**
     * The name of the version selection strategy to use in conflict resolution: "nearest" (default) or "highest".
     *
     * @since 2.0.11
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     * @configurationDefaultValue {@link #DEFAULT_SELECTION_STRATEGY}
     */
    public static final String CONFIG_PROP_SELECTION_STRATEGY =
            ConfigurationProperties.PREFIX_AETHER + "conflictResolver.versionSelector.selectionStrategy";

    public static final String NEAREST_SELECTION_STRATEGY = "nearest";
    public static final String HIGHEST_SELECTION_STRATEGY = "highest";

    public static final String DEFAULT_SELECTION_STRATEGY = NEAREST_SELECTION_STRATEGY;

    /**
     * The strategy how "winner" is being selected.
     */
    public interface SelectionStrategy {
        /**
         * Invoked for every "candidate" when winner is already set (very first candidate is set as winner).
         * <p>
         * This method should determine is candidate "better" or not and should replace current winner. This method
         * is invoked whenever {@code candidate} is "considered" (fits any constraint in effect, if any).
         */
        boolean isBetter(ConflictItem candidate, ConflictItem winner);
        /**
         * Method invoked at version selection end, just before version selector returns. Note: {@code winner} may
         * be {@code null}, while the rest of parameters cannot. The parameter {@code candidates} contains all the
         * "considered candidates", dependencies that fulfil all constraints, if present. The {@code context} on the
         * other hand contains all items participating in conflict.
         * <p>
         * This method by default just returns the passed in {@code winner}, but can do much more.
         */
        default ConflictItem winnerSelected(
                ConflictItem winner, Collection<ConflictItem> candidates, ConflictContext context)
                throws UnsolvableVersionConflictException {
            return winner;
        }
    }

    /**
     * The strategy of winner selection, never {@code null}.
     */
    protected final SelectionStrategy selectionStrategy;

    /**
     * Creates a new instance of this version selector that will use configured selection strategy dynamically.
     */
    public ConfigurableVersionSelector() {
        this.selectionStrategy = null;
    }

    /**
     * Creates a new instance of this version selector using passed in selection strategy always.
     *
     * @param selectionStrategy The winner selection strategy, must not be {@code null}. Maven3
     *                          used {@link Nearest} strategy.
     */
    public ConfigurableVersionSelector(SelectionStrategy selectionStrategy) {
        this.selectionStrategy = requireNonNull(selectionStrategy, "selectionStrategy");
    }

    @Override
    public VersionSelector getInstance(DependencyNode root, DependencyGraphTransformationContext context)
            throws RepositoryException {
        if (selectionStrategy == null) {
            String ss = ConfigUtils.getString(
                    context.getSession(), DEFAULT_SELECTION_STRATEGY, CONFIG_PROP_SELECTION_STRATEGY);
            SelectionStrategy strategy;
            if (NEAREST_SELECTION_STRATEGY.equals(ss)) {
                strategy = new Nearest();
            } else if (HIGHEST_SELECTION_STRATEGY.equals(ss)) {
                strategy = new Highest();
            } else {
                throw new IllegalArgumentException("Unknown selection strategy: " + ss + "; known are "
                        + Arrays.asList(NEAREST_SELECTION_STRATEGY, HIGHEST_SELECTION_STRATEGY));
            }
            return new ConfigurableVersionSelector(strategy);
        } else {
            return this;
        }
    }

    @Override
    public void selectVersion(ConflictContext context) throws RepositoryException {
        ConflictGroup group = new ConflictGroup();
        for (ConflictItem candidate : context.getItems()) {
            DependencyNode node = candidate.getNode();
            VersionConstraint constraint = node.getVersionConstraint();

            boolean backtrack = false;
            boolean hardConstraint = constraint.getRange() != null;

            if (hardConstraint) {
                if (group.constraints.add(constraint)) {
                    if (group.winner != null
                            && !constraint.containsVersion(
                                    group.winner.getNode().getVersion())) {
                        backtrack = true;
                    }
                }
            }

            if (isAcceptableByConstraints(group, node.getVersion())) {
                group.candidates.add(candidate);

                if (backtrack) {
                    backtrack(group, context);
                } else if (group.winner == null || selectionStrategy.isBetter(candidate, group.winner)) {
                    group.winner = candidate;
                }
            } else if (backtrack) {
                backtrack(group, context);
            }
        }
        context.setWinner(selectionStrategy.winnerSelected(group.winner, group.candidates, context));
    }

    protected void backtrack(ConflictGroup group, ConflictContext context) throws UnsolvableVersionConflictException {
        group.winner = null;

        for (Iterator<ConflictItem> it = group.candidates.iterator(); it.hasNext(); ) {
            ConflictItem candidate = it.next();

            if (!isAcceptableByConstraints(group, candidate.getNode().getVersion())) {
                it.remove();
            } else if (group.winner == null || selectionStrategy.isBetter(candidate, group.winner)) {
                group.winner = candidate;
            }
        }

        if (group.winner == null) {
            throw newFailure("Unsolvable hard constraint combination", context);
        }
    }

    protected boolean isAcceptableByConstraints(ConflictGroup group, Version version) {
        for (VersionConstraint constraint : group.constraints) {
            if (!constraint.containsVersion(version)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + (selectionStrategy != null ? selectionStrategy.getClass().getSimpleName() : "not inited") + ")";
    }

    /**
     * Helper method to create failure, creates instance of {@link UnsolvableVersionConflictException}.
     */
    public static UnsolvableVersionConflictException newFailure(String message, ConflictContext context) {
        DependencyFilter filter = (node, parents) -> {
            requireNonNull(node, "node cannot be null");
            requireNonNull(parents, "parents cannot be null");
            return context.isIncluded(node);
        };
        PathRecordingDependencyVisitor visitor = new PathRecordingDependencyVisitor(filter);
        context.getRoot().accept(new TreeDependencyVisitor(visitor));
        return new UnsolvableVersionConflictException(message, visitor.getPaths());
    }

    protected static class ConflictGroup {

        final Collection<VersionConstraint> constraints;

        final Collection<ConflictItem> candidates;

        ConflictItem winner;

        ConflictGroup() {
            constraints = new HashSet<>();
            candidates = new ArrayList<>(64);
        }

        @Override
        public String toString() {
            return String.valueOf(winner);
        }
    }

    /**
     * Selection strategy that selects "nearest" (to the root) version.
     * <p>
     * This is the "classic" Maven strategy.
     * <p>
     * If candidates are siblings, it will select higher version (ie version ranges), otherwise item with smaller
     * depth is selected.
     */
    public static class Nearest implements SelectionStrategy {
        @Override
        public boolean isBetter(ConflictItem candidate, ConflictItem winner) {
            if (candidate.isSibling(winner)) {
                return candidate
                                .getNode()
                                .getVersion()
                                .compareTo(winner.getNode().getVersion())
                        > 0;
            } else {
                return candidate.getDepth() < winner.getDepth();
            }
        }
    }

    /**
     * Selection strategy that selects "highest" version.
     * <p>
     * If winner is level 1 or less (is direct dependency of root), it is kept as winner (as in "real life" it means
     * dependency is enlisted in POM). Then candidate is checked for same thing, and selected if it is direct dependency.
     * Then if both, candidate and winner carries same version (so are same GACEV, same artifact) then "nearest" is selected.
     * Finally, if none of above, higher version is selected out of two.
     */
    public static class Highest implements SelectionStrategy {
        @Override
        public boolean isBetter(ConflictItem candidate, ConflictItem winner) {
            if (winner.getDepth() <= 1) {
                return false;
            } else if (candidate.getDepth() <= 1) {
                return true;
            } else if (candidate.getNode().getVersion().equals(winner.getNode().getVersion())) {
                return candidate.getDepth() < winner.getDepth();
            } else {
                return candidate
                                .getNode()
                                .getVersion()
                                .compareTo(winner.getNode().getVersion())
                        > 0;
            }
        }
    }

    /**
     * Example selection strategy (used in tests and demos), is not recommended to be used in production.
     * <p>
     * Selection strategy that delegates to another selection strategy, and at the end enforces dependency convergence
     * among candidates.
     */
    public static class VersionConvergence implements SelectionStrategy {
        private final SelectionStrategy delegate;

        public VersionConvergence(SelectionStrategy delegate) {
            this.delegate = requireNonNull(delegate, "delegate");
        }

        @Override
        public boolean isBetter(ConflictItem candidate, ConflictItem winner) {
            return delegate.isBetter(candidate, winner);
        }

        @Override
        public ConflictItem winnerSelected(
                ConflictItem winner, Collection<ConflictItem> candidates, ConflictContext context)
                throws UnsolvableVersionConflictException {
            if (winner != null && winner.getNode().getVersionConstraint().getRange() == null) {
                Set<String> versions = candidates.stream()
                        .map(c -> c.getDependency().getArtifact().getVersion())
                        .collect(Collectors.toSet());
                if (versions.size() > 1) {
                    throw newFailure(
                            "Convergence violated for "
                                    + winner.getDependency().getArtifact().getGroupId() + ":"
                                    + winner.getDependency().getArtifact().getArtifactId() + ", versions present: "
                                    + versions,
                            context);
                }
            }
            return winner;
        }
    }

    /**
     * Example selection strategy (used in tests and demos), is not recommended to be used in production.
     * <p>
     * Selection strategy that delegates to another selection strategy, and at end enforces aligned "major versions"
     * among candidates.
     */
    public static class MajorVersionConvergence implements SelectionStrategy {
        private final SelectionStrategy delegate;

        public MajorVersionConvergence(SelectionStrategy delegate) {
            this.delegate = requireNonNull(delegate, "delegate");
        }

        @Override
        public boolean isBetter(ConflictItem candidate, ConflictItem winner) {
            return delegate.isBetter(candidate, winner);
        }

        @Override
        public ConflictItem winnerSelected(
                ConflictItem winner, Collection<ConflictItem> candidates, ConflictContext context)
                throws UnsolvableVersionConflictException {
            if (winner != null && !candidates.isEmpty()) {
                Set<String> incompatibleVersions = candidates.stream()
                        .filter(c -> !sameMajor(c, winner))
                        .map(c -> c.getDependency().getArtifact().getVersion())
                        .collect(Collectors.toSet());
                if (!incompatibleVersions.isEmpty()) {
                    Set<String> allVersions = candidates.stream()
                            .map(c -> c.getDependency().getArtifact().getVersion())
                            .collect(Collectors.toSet());
                    throw newFailure(
                            "Incompatible versions for "
                                    + winner.getDependency().getArtifact().getGroupId() + ":"
                                    + winner.getDependency().getArtifact().getArtifactId() + ", incompatible versions: "
                                    + incompatibleVersions + ", all versions " + allVersions,
                            context);
                }
            }
            return winner;
        }

        private boolean sameMajor(ConflictItem candidate, ConflictItem winner) {
            String candidateVersion = candidate.getDependency().getArtifact().getVersion();
            String winnerVersion = winner.getDependency().getArtifact().getVersion();
            // for now a naive check: major versions should be same
            if (candidateVersion.contains(".") && winnerVersion.contains(".")) {
                String candidateMajor = candidateVersion.substring(0, candidateVersion.indexOf('.'));
                String winnerMajor = winnerVersion.substring(0, winnerVersion.indexOf('.'));
                return Objects.equals(candidateMajor, winnerMajor);
            }
            return true; // cannot determine, so just leave it
        }
    }
}
