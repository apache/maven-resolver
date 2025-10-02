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
package org.eclipse.aether.spi.platform;

import org.eclipse.aether.RepositorySystemSession;

/**
 * A platform locator factory.
 */
public interface PlatformLocatorFactory {
    /**
     * Tries to create a platform locator based on session.
     *
     * @param session The repository system session from which to configure the manager, must not be {@code null}.
     * @return The manager or {@code null}.
     */
    PlatformLocator newInstance(RepositorySystemSession session);

    /**
     * The priority of this factory. Factories with higher priority are preferred over those with lower priority.
     *
     * @return The priority of this factory.
     */
    float getPriority();
}
