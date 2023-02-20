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

import java.util.Arrays;
import java.util.Collection;

import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.VersionFilter;

/**
 * A version filter that combines multiple version filters into a chain where each filter gets invoked one after the
 * other, thereby accumulating their filtering effects.
 */
public final class ChainedVersionFilter implements VersionFilter {

    private final VersionFilter[] filters;

    private int hashCode;

    /**
     * Chains the specified version filters.
     *
     * @param filter1 The first version filter, may be {@code null}.
     * @param filter2 The second version filter, may be {@code null}.
     * @return The chained version filter or {@code null} if both input filters are {@code null}.
     */
    public static VersionFilter newInstance(VersionFilter filter1, VersionFilter filter2) {
        if (filter1 == null) {
            return filter2;
        }
        if (filter2 == null) {
            return filter1;
        }
        return new ChainedVersionFilter(new VersionFilter[] {filter1, filter2});
    }

    /**
     * Chains the specified version filters.
     *
     * @param filters The version filters to chain, must not be {@code null} or contain {@code null}.
     * @return The chained version filter or {@code null} if the input array is empty.
     */
    public static VersionFilter newInstance(VersionFilter... filters) {
        if (filters.length <= 1) {
            if (filters.length <= 0) {
                return null;
            }
            return filters[0];
        }
        return new ChainedVersionFilter(filters.clone());
    }

    /**
     * Chains the specified version filters.
     *
     * @param filters The version filters to chain, must not be {@code null} or contain {@code null}.
     * @return The chained version filter or {@code null} if the input collection is empty.
     */
    public static VersionFilter newInstance(Collection<? extends VersionFilter> filters) {
        if (filters.size() <= 1) {
            if (filters.isEmpty()) {
                return null;
            }
            return filters.iterator().next();
        }
        return new ChainedVersionFilter(filters.toArray(new VersionFilter[0]));
    }

    private ChainedVersionFilter(VersionFilter[] filters) {
        this.filters = filters;
    }

    public void filterVersions(VersionFilterContext context) throws RepositoryException {
        for (int i = 0, n = filters.length; i < n && context.getCount() > 0; i++) {
            filters[i].filterVersions(context);
        }
    }

    public VersionFilter deriveChildFilter(DependencyCollectionContext context) {
        VersionFilter[] children = null;
        int removed = 0;
        for (int i = 0, n = filters.length; i < n; i++) {
            VersionFilter child = filters[i].deriveChildFilter(context);
            if (children != null) {
                children[i - removed] = child;
            } else if (child != filters[i]) {
                children = new VersionFilter[filters.length];
                System.arraycopy(filters, 0, children, 0, i);
                children[i - removed] = child;
            }
            if (child == null) {
                removed++;
            }
        }
        if (children == null) {
            return this;
        }
        if (removed > 0) {
            int count = filters.length - removed;
            if (count <= 0) {
                return null;
            }
            if (count == 1) {
                return children[0];
            }
            VersionFilter[] tmp = new VersionFilter[count];
            System.arraycopy(children, 0, tmp, 0, count);
            children = tmp;
        }
        return new ChainedVersionFilter(children);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (null == obj || !getClass().equals(obj.getClass())) {
            return false;
        }

        ChainedVersionFilter that = (ChainedVersionFilter) obj;
        return Arrays.equals(filters, that.filters);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            int hash = getClass().hashCode();
            hash = hash * 31 + Arrays.hashCode(filters);
            hashCode = hash;
        }
        return hashCode;
    }
}
