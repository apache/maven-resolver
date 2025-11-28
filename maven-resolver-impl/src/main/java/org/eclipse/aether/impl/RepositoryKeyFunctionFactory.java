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

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.util.repository.RepositoryKeyFunction;

/**
 * A factory to create {@link RepositoryKeyFunction} instances.
 *
 * @since 2.0.14
 * @provisional This type is provisional and can be changed, moved or removed without prior notice.
 */
public interface RepositoryKeyFunctionFactory {
    /**
     * Returns system-wide repository key function.
     *
     * @param session The repository session, must not be {@code null}.
     * @return The repository key function.
     * @see #repositoryKeyFunction(Class, RepositorySystemSession, String, String)
     * @see org.eclipse.aether.ConfigurationProperties#REPOSITORY_SYSTEM_REPOSITORY_KEY_FUNCTION
     */
    RepositoryKeyFunction systemRepositoryKeyFunction(RepositorySystemSession session);

    /**
     * Method that based on configuration returns the "repository key function". The returned function will be session
     * cached if session is equipped with cache, otherwise it will be non cached. Method never returns {@code null}.
     * Only the {@code configurationKey} parameter may be {@code null} in which case no configuration lookup happens
     * but the {@code defaultValue} is directly used instead.
     *
     * @param owner The "owner" of key function (used to create cache-key), must not be {@code null}.
     * @param session The repository session, must not be {@code null}.
     * @param defaultValue The default value of repository key configuration, must not be {@code null}.
     * @param configurationKey The configuration key to lookup configuration from, may be {@code null}, in which case
     *                         no configuration lookup happens but the {@code defaultValue} is used to create the
     *                         repository key function.
     * @return The repository key function.
     */
    RepositoryKeyFunction repositoryKeyFunction(
            Class<?> owner, RepositorySystemSession session, String defaultValue, String configurationKey);
}
