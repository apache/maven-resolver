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
package org.eclipse.aether.util.filter;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;

/**
 * A dependency filter based on dependency scopes. <em>Note:</em> This filter does not assume any relationships between
 * the scopes. In particular, the filter is not aware of scopes that logically include other scopes.
 *
 * @see Dependency#getScope()
 */
public final class ScopeDependencyFilter implements DependencyFilter {

    private final Set<String> included = new HashSet<>();

    private final Set<String> excluded = new HashSet<>();

    /**
     * Creates a new filter using the specified includes and excludes.
     *
     * @param included the set of scopes to include, may be {@code null} or empty to include any scope
     * @param excluded the set of scopes to exclude, may be {@code null} or empty to exclude no scope
     */
    public ScopeDependencyFilter(Collection<String> included, Collection<String> excluded) {
        if (included != null) {
            this.included.addAll(included);
        }
        if (excluded != null) {
            this.excluded.addAll(excluded);
        }
    }

    /**
     * Creates a new filter using the specified excludes.
     *
     * @param excluded the set of scopes to exclude, may be {@code null} or empty to exclude no scope
     */
    public ScopeDependencyFilter(String... excluded) {
        if (excluded != null) {
            this.excluded.addAll(Arrays.asList(excluded));
        }
    }

    public boolean accept(DependencyNode node, List<DependencyNode> parents) {
        Dependency dependency = node.getDependency();

        if (dependency == null) {
            return true;
        }

        String scope = node.getDependency().getScope();
        return (included.isEmpty() || included.contains(scope)) && (excluded.isEmpty() || !excluded.contains(scope));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || !getClass().equals(obj.getClass())) {
            return false;
        }

        ScopeDependencyFilter that = (ScopeDependencyFilter) obj;

        return this.included.equals(that.included) && this.excluded.equals(that.excluded);
    }

    @Override
    public int hashCode() {
        int hash = 17;
        hash = hash * 31 + included.hashCode();
        hash = hash * 31 + excluded.hashCode();
        return hash;
    }
}
