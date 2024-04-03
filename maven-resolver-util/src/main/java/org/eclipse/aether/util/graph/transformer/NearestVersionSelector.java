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
 * A version selector for use with {@link ConflictResolver} that resolves version conflicts using a nearest-wins
 * strategy. If there is no single node that satisfies all encountered version ranges, the selector will fail.
 *
 * @deprecated Use {@link ConfigurableVersionSelector} instead.
 */
@Deprecated
public final class NearestVersionSelector extends VersionSelector {

    /**
     * Creates a new instance of this version selector.
     */
    public NearestVersionSelector() {}

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

            if (isAcceptable(group, node.getVersion())) {
                group.candidates.add(item);

                if (backtrack) {
                    backtrack(group, context);
                } else if (group.winner == null || isNearer(item, group.winner)) {
                    group.winner = item;
                }
            } else if (backtrack) {
                backtrack(group, context);
            }
        }
        context.setWinner(group.winner);
    }

    private void backtrack(ConflictGroup group, ConflictContext context) throws UnsolvableVersionConflictException {
        group.winner = null;

        for (Iterator<ConflictItem> it = group.candidates.iterator(); it.hasNext(); ) {
            ConflictItem candidate = it.next();

            if (!isAcceptable(group, candidate.getNode().getVersion())) {
                it.remove();
            } else if (group.winner == null || isNearer(candidate, group.winner)) {
                group.winner = candidate;
            }
        }

        if (group.winner == null) {
            throw newFailure(context);
        }
    }

    private boolean isAcceptable(ConflictGroup group, Version version) {
        for (VersionConstraint constraint : group.constraints) {
            if (!constraint.containsVersion(version)) {
                return false;
            }
        }
        return true;
    }

    private boolean isNearer(ConflictItem item1, ConflictItem item2) {
        if (item1.isSibling(item2)) {
            return item1.getNode().getVersion().compareTo(item2.getNode().getVersion()) > 0;
        } else {
            return item1.getDepth() < item2.getDepth();
        }
    }

    private UnsolvableVersionConflictException newFailure(final ConflictContext context) {
        DependencyFilter filter = (node, parents) -> {
            requireNonNull(node, "node cannot be null");
            requireNonNull(parents, "parents cannot be null");
            return context.isIncluded(node);
        };
        PathRecordingDependencyVisitor visitor = new PathRecordingDependencyVisitor(filter);
        context.getRoot().accept(new TreeDependencyVisitor(visitor));
        return new UnsolvableVersionConflictException(visitor.getPaths());
    }

    static final class ConflictGroup {

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
}
