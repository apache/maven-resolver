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

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.eclipse.aether.Keys;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryKeyFunction;
import org.eclipse.aether.spi.remoterepo.RepositoryKeyFunctionFactory;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.repository.RepositoryIdHelper;

import static java.util.Objects.requireNonNull;

@Singleton
@Named
public class DefaultRepositoryKeyFunctionFactory implements RepositoryKeyFunctionFactory {
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
    public RepositoryKeyFunction repositoryKeyFunctionMk(
            Class<?> owner, RepositorySystemSession session, String defaultValue, String... configurationKeys) {
        requireNonNull(session);
        requireNonNull(defaultValue);
        RepositoryIdHelper.RepositoryKeyType type =
                RepositoryIdHelper.RepositoryKeyType.valueOf((configurationKeys != null
                                ? ConfigUtils.getString(session, defaultValue, configurationKeys)
                                : defaultValue)
                        .toUpperCase(Locale.ENGLISH));
        final RepositoryKeyFunction repositoryKeyFunction = RepositoryIdHelper.getRepositoryKeyFunction(type.name());
        if (session.getCache() != null) {
            // both are expensive methods; cache it in session (repo -> context -> ID)
            return (repository, context) -> ((ConcurrentMap<RemoteRepository, ConcurrentMap<String, String>>)
                            session.getCache().computeIfAbsent(session, Keys.of(owner, type), ConcurrentHashMap::new))
                    .computeIfAbsent(repository, k1 -> new ConcurrentHashMap<>())
                    .computeIfAbsent(
                            context == null ? "" : context, k2 -> repositoryKeyFunction.apply(repository, context));
        } else {
            return repositoryKeyFunction;
        }
    }
}
