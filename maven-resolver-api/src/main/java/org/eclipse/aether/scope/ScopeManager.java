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
    Optional<DependencyScope> getSystemScope();

    /**
     * Returns a specific dependency scope by label.
     * <p>
     * Note: despite returns optional, this method may throw as well, if manager set in "strict" mode.
     */
    Optional<DependencyScope> getDependencyScope(String id);

    /**
     * Returns the "universe" (all) of dependency scopes.
     */
    Collection<DependencyScope> getDependencyScopeUniverse();

    /**
     * Returns a specific resolution scope by label.
     * <p>
     * Note: despite returns optional, this method may throw as well, if manager set in "strict" mode.
     */
    Optional<ResolutionScope> getResolutionScope(String id);

    /**
     * Returns the "universe" (all) of resolution scopes.
     */
    Collection<ResolutionScope> getResolutionScopeUniverse();
}
