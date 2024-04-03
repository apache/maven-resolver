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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.collection.UnsolvableVersionConflictException;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
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
     * The strategy how "winner" is being selected.
     */
    public enum SelectionStrategy {
        /**
         * This is how Maven3 works, chooses "nearer" dependency for winner.
         */
        NEARER,
        /**
         * This is new mode, chooses "higher version" dependency for winner.
         */
        HIGHER_VERSION;
    }
    /**
     * The strategy of version acceptance check.
     */
    public interface AcceptanceStrategy {
        /**
         * This method should determine are candidate version acceptable or not. This method is invoked whenever
         * {@code candidate} is "considered" (does not have to be selected as "winner").
         */
        boolean isAcceptableVersion(ConflictItem candidate, ConflictItem winner);
        /**
         * Method invoked at version selection end, just before version selector returns. Note: {@code winner} may
         * be {@code null}, while the rest of parameters cannot.
         */
        void atSelectVersionEnd(
                ConflictItem winner,
                Collection<ConflictItem> candidates,
                Collection<ConflictItem> notAcceptedCandidates,
                ConflictContext context)
                throws UnsolvableVersionConflictException;
    }
    /**
     * If set, this version selector will use it to detect "incompatible versions" among candidates. If incompatible
     * versions reported, this selector will fail.
     */
    protected final AcceptanceStrategy acceptanceStrategy;
    /**
     * The strategy of winner selection.
     */
    protected final SelectionStrategy selectionStrategy;

    /**
     * Creates a new instance of this version selector that works "as Maven did so far".
     */
    public ConfigurableVersionSelector() {
        this(null, SelectionStrategy.NEARER);
    }

    /**
     * Creates a new instance of this version selector.
     *
     * @param acceptanceStrategy The strategy to use for "version acceptance" checks, may be {@code null}. If not
     *                              set, this selector will not detect any non-acceptable versions. Maven3 used
     *                              {@code null} here.
     * @param selectionStrategy The winner selection strategy, must not be {@code null}. Maven3
     *                          used {@link SelectionStrategy#NEARER} strategy.
     */
    public ConfigurableVersionSelector(AcceptanceStrategy acceptanceStrategy, SelectionStrategy selectionStrategy) {
        this.acceptanceStrategy = acceptanceStrategy;
        this.selectionStrategy = requireNonNull(selectionStrategy, "selectionStrategy");
    }

    @Override
    public void selectVersion(ConflictContext context) throws RepositoryException {
        ConflictGroup group = new ConflictGroup();
        for (ConflictItem item : context.getItems()) {
            DependencyNode node = item.getNode();
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
                group.candidates.add(item);
                if (group.winner != null && acceptanceStrategy != null) {
                    if (!acceptanceStrategy.isAcceptableVersion(item, group.winner)) {
                        group.notAcceptedCandidates.add(item);
                    }
                }

                if (backtrack) {
                    backtrack(group, context);
                } else if (group.winner == null || isBetter(item, group.winner)) {
                    group.winner = item;
                }
            } else if (backtrack) {
                backtrack(group, context);
            }
        }
        context.setWinner(group.winner);
        if (acceptanceStrategy != null) {
            acceptanceStrategy.atSelectVersionEnd(group.winner, group.candidates, group.notAcceptedCandidates, context);
        }
    }

    protected void backtrack(ConflictGroup group, ConflictContext context) throws UnsolvableVersionConflictException {
        group.winner = null;
        group.notAcceptedCandidates.clear();

        for (Iterator<ConflictItem> it = group.candidates.iterator(); it.hasNext(); ) {
            ConflictItem candidate = it.next();

            if (!isAcceptableByConstraints(group, candidate.getNode().getVersion())) {
                it.remove();
            } else {
                if (group.winner != null && acceptanceStrategy != null) {
                    if (!acceptanceStrategy.isAcceptableVersion(candidate, group.winner)) {
                        group.notAcceptedCandidates.add(candidate);
                    }
                }
                if (group.winner == null || isBetter(candidate, group.winner)) {
                    group.winner = candidate;
                }
            }
        }

        if (group.winner == null) {
            throw newFailure("Unsolvable hard constraint", context);
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

    protected boolean isBetter(ConflictItem candidate, ConflictItem winner) {
        boolean result;
        switch (selectionStrategy) {
            case NEARER:
                result = isNearer(candidate, winner);
                break;
            case HIGHER_VERSION:
                result = isHigherVersion(candidate, winner);
                break;
            default:
                throw new IllegalStateException("Unknown strategy");
        }
        return result;
    }

    protected boolean isNearer(ConflictItem candidate, ConflictItem winner) {
        if (candidate.isSibling(winner)) {
            return candidate.getNode().getVersion().compareTo(winner.getNode().getVersion()) > 0;
        } else {
            return candidate.getDepth() < winner.getDepth();
        }
    }

    protected boolean isHigherVersion(ConflictItem candidate, ConflictItem winner) {
        return candidate.getNode().getVersion().compareTo(winner.getNode().getVersion()) > 0;
    }

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

        final Collection<ConflictItem> notAcceptedCandidates;

        ConflictItem winner;

        ConflictGroup() {
            constraints = new HashSet<>();
            candidates = new ArrayList<>(64);
            notAcceptedCandidates = new ArrayList<>();
        }

        @Override
        public String toString() {
            return String.valueOf(winner);
        }
    }

    /**
     * Example compatibility strategy (used in tests and demos), is not recommended to be used in production.
     * <p>
     * Compatibility strategy that enforces dependency convergence.
     */
    public static class VersionConvergence implements AcceptanceStrategy {
        @Override
        public boolean isAcceptableVersion(ConflictItem candidate, ConflictItem winner) {
            return Objects.equals(
                    candidate.getDependency().getArtifact().getVersion(),
                    winner.getDependency().getArtifact().getVersion());
        }

        @Override
        public void atSelectVersionEnd(
                ConflictItem winner,
                Collection<ConflictItem> candidates,
                Collection<ConflictItem> notAcceptedCandidates,
                ConflictContext context)
                throws UnsolvableVersionConflictException {
            if (context.winner != null
                    && context.winner.getNode().getVersionConstraint().getRange() == null) {
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
        }
    }

    /**
     * Example compatibility strategy (used in tests and demos), is not recommended to be used in production.
     * <p>
     * Compatibility strategy that tries to extract "major version" from artifact version string and if it is possible,
     * makes sure they are same.
     */
    public static class MajorVersion implements AcceptanceStrategy {
        @Override
        public boolean isAcceptableVersion(ConflictItem candidate, ConflictItem winner) {
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

        @Override
        public void atSelectVersionEnd(
                ConflictItem winner,
                Collection<ConflictItem> candidates,
                Collection<ConflictItem> notAcceptedCandidates,
                ConflictContext context)
                throws UnsolvableVersionConflictException {
            if (winner != null && !notAcceptedCandidates.isEmpty()) {
                Set<String> allVersions = candidates.stream()
                        .map(c -> c.getDependency().getArtifact().getVersion())
                        .collect(Collectors.toSet());
                Set<String> incompatibleVersions = notAcceptedCandidates.stream()
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
    }
}
