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
package org.eclipse.aether.internal.impl.scope;

import java.util.Collection;
import java.util.Objects;

import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.Dependency;

import static java.util.Objects.requireNonNull;

/**
 * A dependency selector that filters transitive dependencies based on their scope. It is configurable from which level
 * applies, as it depend on "as project" and "as dependency" use cases.
 * <p>
 * <em>Important note:</em> equals/hashCode must factor in starting state, as instances of this class
 * (potentially differentially configured) are used now in session, but are kept in a set.
 * <p>
 * <em>Note:</em> This filter does not assume any relationships between the scopes.
 * In particular, the filter is not aware of scopes that logically include other scopes.
 *
 * @see Dependency#getScope()
 */
public final class ScopeDependencySelector implements DependencySelector {
    /**
     * This enables "legacy" mode (proper): in Resolver 1.x "transitive" state depended on the presence or
     * absence of the root node.
     */
    public static ScopeDependencySelector legacy(Collection<String> included, Collection<String> excluded) {
        return new ScopeDependencySelector(
                Objects.hash(true, 0, 1, Integer.MAX_VALUE, included, excluded),
                true,
                0,
                1,
                Integer.MAX_VALUE,
                included,
                excluded);
    }

    /**
     * Selects dependencies by scope always (from root).
     */
    public static ScopeDependencySelector fromRoot(Collection<String> included, Collection<String> excluded) {
        return from(1, included, excluded);
    }

    /**
     * Selects dependencies by scope starting from direct dependencies.
     */
    public static ScopeDependencySelector fromDirect(Collection<String> included, Collection<String> excluded) {
        return from(2, included, excluded);
    }

    /**
     * Selects dependencies by scope starting from given depth (1=root, 2=direct, 3=transitives of direct ones...).
     */
    public static ScopeDependencySelector from(
            int applyFrom, Collection<String> included, Collection<String> excluded) {
        return fromTo(applyFrom, Integer.MAX_VALUE, included, excluded);
    }

    /**
     * Selects dependencies by scope starting from given depth (1=root, 2=direct, 3=transitives of direct ones...) to
     * given depth.
     */
    public static ScopeDependencySelector fromTo(
            int applyFrom, int applyTo, Collection<String> included, Collection<String> excluded) {
        if (applyFrom < 1) {
            throw new IllegalArgumentException("applyFrom must be non-zero and positive");
        }
        if (applyFrom > applyTo) {
            throw new IllegalArgumentException("applyTo must be greater or equal than applyFrom");
        }
        return new ScopeDependencySelector(
                Objects.hash(false, 0, applyFrom, applyTo, included, excluded),
                false,
                0,
                applyFrom,
                applyTo,
                included,
                excluded);
    }

    private final int seed;
    private final boolean shiftIfRootNull;
    private final int depth;
    private final int applyFrom;
    private final int applyTo;
    private final Collection<String> included;
    private final Collection<String> excluded;

    private ScopeDependencySelector(
            int seed,
            boolean shiftIfRootNull,
            int depth,
            int applyFrom,
            int applyTo,
            Collection<String> included,
            Collection<String> excluded) {
        this.seed = seed;
        this.shiftIfRootNull = shiftIfRootNull;
        this.depth = depth;
        this.applyFrom = applyFrom;
        this.applyTo = applyTo;
        this.included = included;
        this.excluded = excluded;
    }

    @Override
    public boolean selectDependency(Dependency dependency) {
        requireNonNull(dependency, "dependency cannot be null");
        if (depth < applyFrom || depth > applyTo) {
            return true;
        }

        String scope = dependency.getScope();
        return (included == null || included.contains(scope)) && (excluded == null || !excluded.contains(scope));
    }

    @Override
    public DependencySelector deriveChildSelector(DependencyCollectionContext context) {
        requireNonNull(context, "context cannot be null");
        if (depth == 0 && shiftIfRootNull && context.getDependency() == null) {
            return new ScopeDependencySelector(
                    seed, shiftIfRootNull, depth + 1, applyFrom + 1, applyTo, included, excluded);
        } else {
            return new ScopeDependencySelector(
                    seed, shiftIfRootNull, depth + 1, applyFrom, applyTo, included, excluded);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (null == obj || !getClass().equals(obj.getClass())) {
            return false;
        }

        ScopeDependencySelector that = (ScopeDependencySelector) obj;
        return seed == that.seed
                && shiftIfRootNull == that.shiftIfRootNull
                && depth == that.depth
                && applyFrom == that.applyFrom
                && applyTo == that.applyTo
                && Objects.equals(included, that.included)
                && Objects.equals(excluded, that.excluded);
    }

    @Override
    public int hashCode() {
        int hash = 17;
        hash = hash * 31 + seed;
        hash = hash * 31 + (shiftIfRootNull ? 0 : 1);
        hash = hash * 31 + depth;
        hash = hash * 31 + applyFrom;
        hash = hash * 31 + applyTo;
        hash = hash * 31 + (included != null ? included.hashCode() : 0);
        hash = hash * 31 + (excluded != null ? excluded.hashCode() : 0);
        return hash;
    }

    @Override
    public String toString() {
        return String.format(
                "%s(included: %s, excluded: %s, applied: %s)",
                getClass().getSimpleName(), included, excluded, depth >= applyFrom);
    }
}
