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
package org.eclipse.aether.spi.artifact.decorator;

import org.eclipse.aether.RepositorySystemSession;

/**
 * A factory to create artifact decorators.
 *
 * @since 2.0.0
 */
public interface ArtifactDecoratorFactory {
    /**
     * Creates a new artifact decorator for the session.
     *
     * @param session The repository system session from which to configure the decorator, must not be {@code null}.
     * @return The artifact decorator for the session, never {@code null}.
     */
    ArtifactDecorator newInstance(RepositorySystemSession session);

    /**
     * The priority of this factory. Factories with higher priority are invoked before those with lower priority.
     *
     * @return The priority of this factory.
     */
    float getPriority();
}
