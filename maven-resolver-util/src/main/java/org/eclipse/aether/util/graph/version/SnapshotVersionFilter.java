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

import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.VersionFilter;
import org.eclipse.aether.version.Version;

/**
 * A version filter that (unconditionally) blocks "*-SNAPSHOT" versions. For practical purposes,
 * {@link ContextualSnapshotVersionFilter} is usually more desirable.
 */
public final class SnapshotVersionFilter implements VersionFilter {

    /**
     * Creates a new instance of this version filter.
     */
    public SnapshotVersionFilter() {}

    @Override
    public void filterVersions(VersionFilterContext context) {
        for (Iterator<Version> it = context.iterator(); it.hasNext(); ) {
            String version = it.next().toString();
            if (version.endsWith("SNAPSHOT")) {
                it.remove();
            }
        }
    }

    @Override
    public VersionFilter deriveChildFilter(DependencyCollectionContext context) {
        return this;
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
