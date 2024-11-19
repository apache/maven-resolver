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
package org.eclipse.aether.spi.artifact.transformer;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.installation.InstallRequest;

/**
 * Install and deploy artifact transformer. This component can mangle install and deploy requests, replace artifacts,
 * add new artifacts and so on.
 *
 * @since TBD
 */
public interface ArtifactTransformer {
    /**
     * Transform install artifacts.
     *
     * @param session never {@code null}
     * @param request never {@code null}
     * @return the transformed request, never {@code null}
     */
    default InstallRequest transformInstallArtifacts(RepositorySystemSession session, InstallRequest request) {
        return request;
    }

    /**
     * Transform deploy artifacts.
     *
     * @param session never {@code null}
     * @param request never {@code null}
     * @return the transformed request, never {@code null}
     */
    default DeployRequest transformDeployArtifacts(RepositorySystemSession session, DeployRequest request) {
        return request;
    }
}
