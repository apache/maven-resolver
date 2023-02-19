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

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.VersionFilter;
import org.eclipse.aether.util.ConfigUtils;

/**
 * A version filter that blocks "*-SNAPSHOT" versions if the
 * {@link org.eclipse.aether.collection.CollectRequest#getRootArtifact() root artifact} of the dependency graph is not a
 * snapshot. Alternatively, this filter can be forced to always ban snapshot versions by setting the boolean
 * {@link RepositorySystemSession#getConfigProperties() configuration property} {@link #CONFIG_PROP_ENABLE} to
 * {@code true}.
 */
public final class ContextualSnapshotVersionFilter implements VersionFilter {

    /**
     * The key in the repository session's {@link RepositorySystemSession#getConfigProperties() configuration
     * properties} used to store a {@link Boolean} flag whether this filter should be forced to ban snapshots. By
     * default, snapshots are only filtered if the root artifact is not a snapshot.
     */
    public static final String CONFIG_PROP_ENABLE = "aether.snapshotFilter";

    private final SnapshotVersionFilter filter;

    /**
     * Creates a new instance of this version filter.
     */
    public ContextualSnapshotVersionFilter() {
        filter = new SnapshotVersionFilter();
    }

    private boolean isEnabled(RepositorySystemSession session) {
        return ConfigUtils.getBoolean(session, false, CONFIG_PROP_ENABLE);
    }

    public void filterVersions(VersionFilterContext context) {
        if (isEnabled(context.getSession())) {
            filter.filterVersions(context);
        }
    }

    public VersionFilter deriveChildFilter(DependencyCollectionContext context) {
        if (!isEnabled(context.getSession())) {
            Artifact artifact = context.getArtifact();
            if (artifact == null) {
                // no root artifact to test, allow snapshots and recheck once we reach the direct dependencies
                return this;
            }
            if (artifact.isSnapshot()) {
                // root is a snapshot, allow snapshots all the way down
                return null;
            }
        }
        // artifact is a non-snapshot or filter explicitly enabled, block snapshots all the way down
        return filter;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (null == obj || !getClass().equals(obj.getClass())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
