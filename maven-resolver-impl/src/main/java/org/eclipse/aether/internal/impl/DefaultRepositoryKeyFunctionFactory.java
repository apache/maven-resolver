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
package org.eclipse.aether.internal.impl;

import javax.inject.Named;
import javax.inject.Singleton;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.impl.RepositoryKeyFunctionFactory;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.repository.RepositoryIdHelper;
import org.eclipse.aether.util.repository.RepositoryKeyFunction;

import static java.util.Objects.requireNonNull;

@Singleton
@Named
public class DefaultRepositoryKeyFunctionFactory implements RepositoryKeyFunctionFactory {
    /**
     * Returns system-wide repository key function.
     *
     * @since 2.0.14
     * @see #repositoryKeyFunction(Class, RepositorySystemSession, String, String)
     */
    @Override
    public RepositoryKeyFunction systemRepositoryKeyFunction(RepositorySystemSession session) {
        return repositoryKeyFunction(
                DefaultRepositoryKeyFunctionFactory.class,
                session,
                ConfigurationProperties.DEFAULT_REPOSITORY_SYSTEM_REPOSITORY_KEY_FUNCTION,
                ConfigurationProperties.REPOSITORY_SYSTEM_REPOSITORY_KEY_FUNCTION);
    }

    /**
     * Method that based on configuration returns the "repository key function". The returned function will be session
     * cached if session is equipped with cache, otherwise it will be non cached. Method never returns {@code null}.
     * Only the {@code configurationKey} parameter may be {@code null} in which case no configuration lookup happens
     * but the {@code defaultValue} is directly used instead.
     *
     * @since 2.0.14
     */
    @SuppressWarnings("unchecked")
    @Override
    public RepositoryKeyFunction repositoryKeyFunction(
            Class<?> owner, RepositorySystemSession session, String defaultValue, String configurationKey) {
        requireNonNull(session);
        requireNonNull(defaultValue);
        final RepositoryKeyFunction repositoryKeyFunction = RepositoryIdHelper.getRepositoryKeyFunction(
                configurationKey != null
                        ? ConfigUtils.getString(session, defaultValue, configurationKey)
                        : defaultValue);
        if (session.getCache() != null) {
            // both are expensive methods; cache it in session (repo -> context -> ID)
            return (repository, context) -> ((ConcurrentMap<RemoteRepository, ConcurrentMap<String, String>>)
                            session.getCache()
                                    .computeIfAbsent(
                                            session,
                                            owner.getName() + ".repositoryKeyFunction",
                                            ConcurrentHashMap::new))
                    .computeIfAbsent(repository, k1 -> new ConcurrentHashMap<>())
                    .computeIfAbsent(
                            context == null ? "" : context, k2 -> repositoryKeyFunction.apply(repository, context));
        } else {
            return repositoryKeyFunction;
        }
    }
}
