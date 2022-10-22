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
}
