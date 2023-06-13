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
package org.eclipse.aether.named;

/**
 * A factory of {@link NamedLock}s.
 */
public interface NamedLockFactory {
    /**
     * Creates or reuses existing {@link NamedLock}. Returns instance MUST BE treated as "resource", best in
     * try-with-resource block.
     *
     * @param name the lock name, must not be {@code null}.
     * @return named lock instance, never {@code null}.
     */
    NamedLock getLock(String name);

    /**
     * Performs a clean shut down of the factory.
     */
    void shutdown();

    /**
     * Method to notify factory about locking failure, to make it possible to provide more (factory specific)
     * information about factory state when a locking operation failed. Factory may alter provided failure or
     * provide information via some other side effect (for example via logging).
     * <p>
     * The default implementation merely does what happened before: adds no extra information.
     *
     * @since 1.9.11
     */
    default <E extends Throwable> E onFailure(E failure) {
        return failure;
    }
}
