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
package org.eclipse.aether.spi.artifact.generator;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.installation.InstallRequest;

/**
 * A factory to create artifact generators. Artifact generators can contribute additional artifacts during the
 * installation/deployment of artifacts.
 *
 * @since 2.0.0
 */
public interface ArtifactGeneratorFactory {
    /**
     * Artifact property that carries generator id. Generated artifacts should have this property set.
     */
    String ARTIFACT_GENERATOR_ID = "artifactGeneratorId";

    /**
     * Creates a new artifact generator for the specified install request.
     *
     * @param session The repository system session from which to configure the generator, must not be {@code null}.
     * @param request The install request the metadata generator is used for, must not be {@code null}.
     * @return The artifact generator for the request or {@code null} if none.
     */
    ArtifactGenerator newInstance(RepositorySystemSession session, InstallRequest request);

    /**
     * Creates a new artifact generator for the specified deploy request.
     *
     * @param session The repository system session from which to configure the generator, must not be {@code null}.
     * @param request The deploy request the metadata generator is used for, must not be {@code null}.
     * @return The artifact generator for the request or {@code null} if none.
     */
    ArtifactGenerator newInstance(RepositorySystemSession session, DeployRequest request);

    /**
     * The priority of this factory. Factories with higher priority are invoked before those with lower priority.
     *
     * @return The priority of this factory.
     */
    float getPriority();
}
