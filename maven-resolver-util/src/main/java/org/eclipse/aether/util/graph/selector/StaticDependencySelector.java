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
package org.eclipse.aether.util.graph.selector;

import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.Dependency;

import static java.util.Objects.requireNonNull;

/**
 * A dependency selector that always includes or excludes dependencies.
 */
public final class StaticDependencySelector implements DependencySelector {

    private final boolean select;

    /**
     * Creates a new selector with the specified selection behavior.
     *
     * @param select {@code true} to select all dependencies, {@code false} to exclude all dependencies.
     */
    public StaticDependencySelector(boolean select) {
        this.select = select;
    }

    public boolean selectDependency(Dependency dependency) {
        requireNonNull(dependency, "dependency cannot be null");
        return select;
    }

    public DependencySelector deriveChildSelector(DependencyCollectionContext context) {
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

        StaticDependencySelector that = (StaticDependencySelector) obj;
        return select == that.select;
    }

    @Override
    public int hashCode() {
        int hash = getClass().hashCode();
        hash = hash * 31 + (select ? 1 : 0);
        return hash;
    }

    @Override
    public String toString() {
        return String.format("%s(%s)", this.getClass().getSimpleName(), this.select ? "Select all" : "Exclude all");
    }
}
