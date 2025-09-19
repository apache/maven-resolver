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
package org.eclipse.aether.util.graph.manager;

import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencyManagement;
import org.eclipse.aether.collection.DependencyManager;
import org.eclipse.aether.graph.Dependency;

import static java.util.Objects.requireNonNull;

/**
 * A dependency manager that performs no dependency management operations.
 *
 * <h2>Overview</h2>
 * <p>
 * This is a null-object implementation of {@link DependencyManager} that effectively
 * disables all dependency management. It always returns null for management operations
 * and returns itself for child manager derivation, making it a true no-op implementation.
 * </p>
 *
 * <h2>When to Use</h2>
 * <ul>
 * <li><strong>Testing:</strong> When you want to disable dependency management for tests</li>
 * <li><strong>Simple Resolution:</strong> When you want pure dependency resolution without management</li>
 * <li><strong>Performance:</strong> When dependency management overhead is not needed</li>
 * <li><strong>Legacy Systems:</strong> When working with systems that handle management externally</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * This implementation is completely thread-safe and stateless. The {@link #INSTANCE} can be
 * safely shared across multiple threads and throughout the entire application lifecycle.
 * </p>
 *
 * <h2>Comparison with Other Managers</h2>
 * <ul>
 * <li>{@link ClassicDependencyManager}: Maven 2.x compatibility with limited management</li>
 * <li>{@link TransitiveDependencyManager}: Modern transitive management</li>
 * <li>{@link DefaultDependencyManager}: Aggressive management at all levels</li>
 * <li><strong>This manager:</strong> No management at all (fastest, simplest)</li>
 * </ul>
 *
 * @see ClassicDependencyManager
 * @see TransitiveDependencyManager
 * @see DefaultDependencyManager
 */
public final class NoopDependencyManager implements DependencyManager {

    /**
     * A ready-made singleton instance of this dependency manager.
     * <p>
     * This instance can be safely reused throughout an entire application regardless of
     * multi-threading, as this implementation is completely stateless and thread-safe.
     * Using this instance is preferred over creating new instances for performance reasons.
     * </p>
     */
    public static final DependencyManager INSTANCE = new NoopDependencyManager();

    /**
     * Creates a new instance of this dependency manager.
     * <p>
     * <strong>Note:</strong> Usually, {@link #INSTANCE} should be used instead of creating
     * new instances, as this implementation is stateless and the singleton provides better
     * performance characteristics.
     * </p>
     */
    public NoopDependencyManager() {}

    /**
     * Returns this same instance as the child manager (no-op behavior).
     *
     * @param context the dependency collection context (validated but not used)
     * @return this same instance
     * @throws NullPointerException if context is null
     */
    public DependencyManager deriveChildManager(DependencyCollectionContext context) {
        requireNonNull(context, "context cannot be null");
        return this;
    }

    /**
     * Returns null, indicating no dependency management should be applied.
     *
     * @param dependency the dependency to manage (validated but not used)
     * @return null (no management)
     * @throws NullPointerException if dependency is null
     */
    public DependencyManagement manageDependency(Dependency dependency) {
        requireNonNull(dependency, "dependency cannot be null");
        return null;
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
