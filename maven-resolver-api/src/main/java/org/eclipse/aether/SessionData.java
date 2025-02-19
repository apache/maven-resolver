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
package org.eclipse.aether;

import java.util.function.Supplier;

/**
 * A container for data that is specific to a repository system session. Both components within the repository system
 * and clients of the system may use this storage to associate arbitrary data with a session.
 * <p>
 * Unlike a cache, this session data is not subject to purging. For this same reason, session data should also not be
 * abused as a cache (i.e. for storing values that can be re-calculated) to avoid memory exhaustion.
 * <p>
 * <strong>Note:</strong> Actual implementations must be thread-safe.
 *
 * @see RepositorySystemSession#getData()
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 */
public interface SessionData {

    /**
     * Associates the specified session data with the given key.
     *
     * @param key The key under which to store the session data, must not be {@code null}.
     * @param value The data to associate with the key, may be {@code null} to remove the mapping.
     */
    void set(Object key, Object value);

    /**
     * Associates the specified session data with the given key if the key is currently mapped to the given value. This
     * method provides an atomic compare-and-update of some key's value.
     *
     * @param key The key under which to store the session data, must not be {@code null}.
     * @param oldValue The expected data currently associated with the key, may be {@code null}.
     * @param newValue The data to associate with the key, may be {@code null} to remove the mapping.
     * @return {@code true} if the key mapping was successfully updated from the old value to the new value,
     *         {@code false} if the current key mapping didn't match the expected value and was not updated.
     */
    boolean set(Object key, Object oldValue, Object newValue);

    /**
     * Gets the session data associated with the specified key.
     *
     * @param key The key for which to retrieve the session data, must not be {@code null}.
     * @return The session data associated with the key or {@code null} if none.
     */
    Object get(Object key);

    /**
     * Retrieve of compute the data associated with the specified key.
     *
     * @param key The key for which to retrieve the session data, must not be {@code null}.
     * @param supplier The supplier will compute the new value.
     * @return The session data associated with the key.
     */
    Object computeIfAbsent(Object key, Supplier<Object> supplier);
}
