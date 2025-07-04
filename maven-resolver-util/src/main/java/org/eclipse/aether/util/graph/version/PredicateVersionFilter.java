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

import java.util.Iterator;
import java.util.Objects;
import java.util.function.Predicate;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.VersionFilter;
import org.eclipse.aether.version.Version;

import static java.util.Objects.requireNonNull;

/**
 * A version filter that excludes any version that is blacklisted.
 *
 * @since 2.0.0
 */
public class PredicateVersionFilter implements VersionFilter {
    private final Predicate<Artifact> artifactPredicate;

    /**
     * Creates a new instance of this version filter.
     */
    public PredicateVersionFilter(Predicate<Artifact> artifactPredicate) {
        this.artifactPredicate = requireNonNull(artifactPredicate);
    }

    @Override
    public void filterVersions(VersionFilterContext context) {
        Artifact dependencyArtifact = context.getDependency().getArtifact();
        Iterator<Version> it = context.iterator();
        while (it.hasNext()) {
            Version version = it.next();
            if (!artifactPredicate.test(dependencyArtifact.setVersion(version.toString()))) {
                it.remove();
            }
        }
    }

    @Override
    public VersionFilter deriveChildFilter(DependencyCollectionContext context) {
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PredicateVersionFilter that = (PredicateVersionFilter) o;
        return Objects.equals(artifactPredicate, that.artifactPredicate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(artifactPredicate);
    }
}
