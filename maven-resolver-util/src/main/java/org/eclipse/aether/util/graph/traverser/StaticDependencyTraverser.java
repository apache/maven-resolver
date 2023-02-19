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
package org.eclipse.aether.util.graph.traverser;

import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencyTraverser;
import org.eclipse.aether.graph.Dependency;

import static java.util.Objects.requireNonNull;

/**
 * A dependency traverser which always or never traverses children.
 */
public final class StaticDependencyTraverser implements DependencyTraverser {

    private final boolean traverse;

    /**
     * Creates a new traverser with the specified traversal behavior.
     *
     * @param traverse {@code true} to traverse all dependencies, {@code false} to never traverse.
     */
    public StaticDependencyTraverser(boolean traverse) {
        this.traverse = traverse;
    }

    public boolean traverseDependency(Dependency dependency) {
        requireNonNull(dependency, "dependency cannot be null");
        return traverse;
    }

    public DependencyTraverser deriveChildTraverser(DependencyCollectionContext context) {
        requireNonNull(context, "context cannot be null");
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (null == obj || !getClass().equals(obj.getClass())) {
            return false;
        }

        StaticDependencyTraverser that = (StaticDependencyTraverser) obj;
        return traverse == that.traverse;
    }

    @Override
    public int hashCode() {
        int hash = getClass().hashCode();
        hash = hash * 31 + (traverse ? 1 : 0);
        return hash;
    }
}
