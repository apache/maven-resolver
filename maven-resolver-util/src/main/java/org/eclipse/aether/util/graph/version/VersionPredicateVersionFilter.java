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

import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.VersionFilter;
import org.eclipse.aether.version.Version;

import static java.util.Objects.requireNonNull;

/**
 * A version filter that excludes any version that is blacklisted.
 *
 * @since 2.0.11
 */
public class VersionPredicateVersionFilter implements VersionFilter {
    private final Predicate<Version> versionPredicate;

    /**
     * Creates a new instance of this version filter. It will filter out versions not matched by predicate.
     * Note: filter always operates with baseVersions.
     */
    public VersionPredicateVersionFilter(Predicate<Version> versionPredicate) {
        this.versionPredicate = requireNonNull(versionPredicate);
    }

    @Override
    public void filterVersions(VersionFilterContext context) {
        for (Iterator<Version> it = context.iterator(); it.hasNext(); ) {
            if (!versionPredicate.test(it.next())) {
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
        VersionPredicateVersionFilter that = (VersionPredicateVersionFilter) o;
        return Objects.equals(versionPredicate, that.versionPredicate);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
