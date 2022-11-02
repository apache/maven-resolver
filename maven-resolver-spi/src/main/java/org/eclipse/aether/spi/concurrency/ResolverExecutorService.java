package org.eclipse.aether.spi.concurrency;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.eclipse.aether.RepositorySystemSession;

/**
 * Component providing {@link ResolverExecutor} instances on demand.
 *
 * @since 1.9.0
 */
public interface ResolverExecutorService
{
    /**
     * A hierarchical key to identify use cases for executors. Same {@link Key} designates same
     * {@link ResolverExecutor}.
     */
    interface Key
    {
        String asString();
    }

    /**
     * Creates service key with given parameters.
     *
     * @param service        The service that is to use executor, never {@code null}.
     * @param discriminators Potential (sub) discriminators, if needed.
     */
    Key getKey( Class<?> service, String... discriminators );

    /**
     * Returns a resolver executor for requester service. The {@code service} parameter is used as "key", meaning
     * multiple services using same "key" will share the executor as well. The very first invocation of this method
     * may create thread pool as well (using {@code maxThreads} parameter), and subsequent calls with different
     * parameter will reuse it, hence the parameter may be neglected.
     * <p>
     * None of parameters may be {@code null}.
     *
     * @param session    The session.
     * @param key        A key for service, multiple components using same key will share same executor.
     * @param maxThreads The count of configured threads (must be bigger than zero).
     */
    ResolverExecutor getResolverExecutor( RepositorySystemSession session, Key key, int maxThreads );
}
