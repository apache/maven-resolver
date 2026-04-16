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
package org.eclipse.aether.util.graph.version;

import java.util.Objects;
import java.util.function.Predicate;

import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.VersionFilter;

import static java.util.Objects.requireNonNull;

/**
 * A version filter that applies delegate version filter if predicate applies.
 *
 * @since 2.0.17
 */
public class PredicateDelegatingVersionFilter implements VersionFilter {
    private final Predicate<Artifact> artifactPredicate;
    private final VersionFilter delegate;

    /**
     * Creates a new instance of this version filter.
     */
    public PredicateDelegatingVersionFilter(Predicate<Artifact> artifactPredicate, VersionFilter delegate) {
        this.artifactPredicate = requireNonNull(artifactPredicate);
        this.delegate = requireNonNull(delegate);
    }

    @Override
    public void filterVersions(VersionFilterContext context) throws RepositoryException {
        if (artifactPredicate.test(context.getDependency().getArtifact())) {
            delegate.filterVersions(context);
        }
    }

    @Override
    public VersionFilter deriveChildFilter(DependencyCollectionContext context) {
        VersionFilter derived = delegate.deriveChildFilter(context);
        if (derived == delegate) {
            return this;
        } else {
            return new PredicateDelegatingVersionFilter(artifactPredicate, derived);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PredicateDelegatingVersionFilter that = (PredicateDelegatingVersionFilter) o;
        return Objects.equals(artifactPredicate, that.artifactPredicate) && Objects.equals(delegate, that.delegate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(artifactPredicate, delegate);
    }
}
