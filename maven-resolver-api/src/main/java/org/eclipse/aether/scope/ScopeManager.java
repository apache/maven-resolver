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
package org.eclipse.aether.scope;

import java.util.Collection;
import java.util.Optional;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.DependencyFilter;

/**
 * Scope manager.
 *
 * @since 2.0.0
 *
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 */
public interface ScopeManager {
    /**
     * The label.
     */
    String getId();

    /**
     * Returns the "system" scope, if exists.
     * <p>
     * This is a special scope. In this scope case, Resolver should handle it specially, as it has no POM (so is
     * always a leaf on graph), is not in any repository, but is actually hosted on host OS file system. On resolution
     * resolver merely checks is file present or not.
     */
    Optional<SystemDependencyScope> getSystemDependencyScope();

    /**
     * Returns a specific dependency scope by label.
     * <p>
     * Note: despite returns optional, this method may throw as well, if manager set in "strict" mode.
     */
    Optional<DependencyScope> getDependencyScope(String id);

    /**
     * Returns the "universe" (all) of dependency scopes as immutable collection.
     */
    Collection<DependencyScope> getDependencyScopeUniverse();

    /**
     * Returns a specific resolution scope by label.
     * <p>
     * Note: despite returns optional, this method may throw as well, if manager set in "strict" mode.
     */
    Optional<ResolutionScope> getResolutionScope(String id);

    /**
     * Returns the "universe" (all) of resolution scopes as immutable collection.
     */
    Collection<ResolutionScope> getResolutionScopeUniverse();

    /**
     * Resolver scope configuration specific: dependency selector to be used to support this scope (with its dependency
     * and resolution scopes).
     * <p>
     * Important: Resolver 2.x when used with {@link ScopeManager}, the scope semantics is defined by client code.
     * Hence, the scopes cannot be interpreted with fixed logic as it was the case in Maven 3.9 and before, instead
     * the {@link ScopeManager} has to be asked to create selector based on scope configuration.
     */
    DependencySelector getDependencySelector(RepositorySystemSession session, ResolutionScope resolutionScope);

    /**
     * Resolver scope configuration specific: dependency filter to be used to support this scope (with its dependency
     * and resolution scopes).
     * <p>
     * Important: Resolver 2.x when used with {@link ScopeManager}, the scope semantics is defined by client code.
     * Hence, the scopes cannot be interpreted with fixed logic as it was the case in Maven 3.9 and before, instead
     * the {@link ScopeManager} has to be asked to create filter based on scope configuration.
     */
    DependencyFilter getDependencyFilter(RepositorySystemSession session, ResolutionScope resolutionScope);
}
