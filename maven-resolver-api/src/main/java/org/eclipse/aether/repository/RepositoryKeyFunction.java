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

import java.util.function.BiFunction;

/**
 * The repository key function, it produces keys (strings) for given {@link RemoteRepository} instances.
 *
 * @since 2.0.14
 */
@FunctionalInterface
public interface RepositoryKeyFunction extends BiFunction<RemoteRepository, String, String> {
    /**
     * Produces a string representing "repository key" for given {@link RemoteRepository} and
     * optionally (maybe {@code null}) "context".
     *
     * @param repository The {@link RemoteRepository}, may not be {@code null}.
     * @param context    The "context" string, or {@code null}.
     * @return The "repository key" string, never {@code null}.
     */
    @Override
    String apply(RemoteRepository repository, String context);
}
