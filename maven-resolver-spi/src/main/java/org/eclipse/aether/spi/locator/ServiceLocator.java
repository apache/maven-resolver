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
package org.eclipse.aether.spi.locator;

import java.util.List;

/**
 * A simple infrastructure to programmatically wire the various components of the repository system together when it is
 * used outside of an IoC container. Once a concrete implementation of a service locator has been setup, clients could
 * use
 *
 * <pre>
 * RepositorySystem repoSystem = serviceLocator.getService( RepositorySystem.class );
 * </pre>
 *
 * to acquire the repository system. Components that implement {@link Service} will be given an opportunity to acquire
 * further components from the locator, thereby allowing to create the complete object graph of the repository system.
 *
 * @deprecated Use some out-of-the-box DI implementation instead.
 */
@Deprecated
public interface ServiceLocator {

    /**
     * Gets an instance of the specified service.
     *
     * @param <T> The service type.
     * @param type The interface describing the service, must not be {@code null}.
     * @return The service instance or {@code null} if the service could not be located/initialized.
     */
    <T> T getService(Class<T> type);

    /**
     * Gets all available instances of the specified service.
     *
     * @param <T> The service type.
     * @param type The interface describing the service, must not be {@code null}.
     * @return The (read-only) list of available service instances, never {@code null}.
     */
    <T> List<T> getServices(Class<T> type);
}
