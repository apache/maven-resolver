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
package org.eclipse.aether.impl;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.eclipse.aether.named.NamedLockFactory;

/**
 * Selector for system-wide use of named locks.
 *
 * @since 2.0.17
 */
public interface NamedLockFactorySelector {
    /**
     * Returns immutable set of available lock factory names, to be used for logging purposes or alike.
     * Never returns {@code null}.
     */
    Set<String> getAvailableLockFactories();

    /**
     * Selects {@link NamedLockFactory} based on configuration.
     * Never returns {@code null} but may throw in case of invalid configuration.
     *
     * @param configuration The configuration maps, must be not {@code null}.
     * @throws IllegalArgumentException In case of invalid configuration.
     */
    NamedLockFactory getNamedLockFactory(Map<String, ?> configuration);

    /**
     * Returns the maximum amount of time to be blocked, while obtaining a named lock, based on configuration.
     * May throw in case of invalid configuration.
     *
     * @param configuration The configuration maps, must be not {@code null}.
     * @throws IllegalArgumentException In case of invalid configuration.
     */
    long getLockWaitTime(Map<String, ?> configuration);

    /**
     * Returns the unit of maximum amount of time based on configuration.
     * Never returns {@code null} but may throw in case of invalid configuration.
     *
     * @param configuration The configuration maps, must be not {@code null}.
     * @throws IllegalArgumentException In case of invalid configuration.
     */
    TimeUnit getLockWaitTimeUnit(Map<String, ?> configuration);
}
