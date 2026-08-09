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
package org.eclipse.aether.collection;

import org.eclipse.aether.RepositorySystemSession;

/**
 * A context used during dependency collection to exchange information within a chain of dependency graph transformers.
 *
 * @see DependencyGraphTransformer
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 */
public interface DependencyGraphTransformationContext {

    /**
     * Gets the repository system session during which the graph transformation happens.
     *
     * @return The repository system session, never {@code null}.
     */
    RepositorySystemSession getSession();

    /**
     * Gets a keyed value from the context.
     *
     * @param key The key used to query the value, must not be {@code null}.
     * @return The queried value or {@code null} if none.
     */
    Object get(Object key);

    /**
     * Puts a keyed value into the context.
     *
     * @param key The key used to store the value, must not be {@code null}.
     * @param value The value to store, may be {@code null} to remove the mapping.
     * @return The previous value associated with the key or {@code null} if none.
     */
    Object put(Object key, Object value);
}
