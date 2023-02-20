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
package org.eclipse.aether.repository;

import java.util.Map;

/**
 * The authentication to use for accessing a protected resource. This acts basically as an extensible callback mechanism
 * from which network operations can request authentication data like username and password when needed.
 */
public interface Authentication {

    /**
     * Fills the given authentication context with the data from this authentication callback. To do so, implementors
     * have to call {@link AuthenticationContext#put(String, Object)}. <br>
     * <br>
     * The {@code key} parameter supplied to this method acts merely as a hint for interactive callbacks that want to
     * prompt the user for only that authentication data which is required. Implementations are free to ignore this
     * parameter and put all the data they have into the authentication context at once.
     *
     * @param context The authentication context to populate, must not be {@code null}.
     * @param key The key denoting a specific piece of authentication data that is being requested for a network
     *            operation, may be {@code null}.
     * @param data Any (read-only) extra data in form of key value pairs that might be useful when getting the
     *            authentication data, may be {@code null}.
     */
    void fill(AuthenticationContext context, String key, Map<String, String> data);

    /**
     * Updates the given digest with data from this authentication callback. To do so, implementors have to call the
     * {@code update()} methods in {@link AuthenticationDigest}.
     *
     * @param digest The digest to update, must not be {@code null}.
     */
    void digest(AuthenticationDigest digest);
}
