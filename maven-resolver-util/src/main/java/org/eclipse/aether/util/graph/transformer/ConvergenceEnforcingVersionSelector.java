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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;

import static java.util.Objects.requireNonNull;

/**
 * A version selector for use with {@link ConflictResolver} that enforces there are no version conflicts. In case
 * they are found, the selector will fail.
 */
public final class ConvergenceEnforcingVersionSelector extends VersionSelector {
    private final boolean enforce;
    private final VersionSelector delegate;

    /**
     * Creates a new instance of this version selector.
     */
    public ConvergenceEnforcingVersionSelector(boolean enforce, VersionSelector delegate) {
        this.enforce = enforce;
        this.delegate = requireNonNull(delegate, "delegate");
    }

    @Override
    public void selectVersion(ConflictContext context) throws RepositoryException {
        HashSet<String> versions = new HashSet<>();
        int uniqueVersions = 0;
        for (ConflictItem item : context.getItems()) {
            uniqueVersions += versions.add(item.getDependency().getArtifact().getVersion()) ? 1 : 0;
        }
        if (uniqueVersions != 1) {
            throw newFailure(context);
        }
        delegate.selectVersion(context);
    }

    private UnsolvableVersionConflictException newFailure(final ConflictContext context) {
        DependencyFilter filter = (node, parents) -> {
            requireNonNull(node, "node cannot be null");
            requireNonNull(parents, "parents cannot be null");
            return context.isIncluded(node);
        };
        PathRecordingDependencyVisitor visitor = new PathRecordingDependencyVisitor(filter);
        context.getRoot().accept(new TreeDependencyVisitor(visitor));
        return new UnsolvableVersionConflictException("convergence", visitor.getPaths());
    }
}
