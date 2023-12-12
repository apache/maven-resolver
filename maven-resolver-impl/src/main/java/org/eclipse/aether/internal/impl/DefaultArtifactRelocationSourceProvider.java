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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.spi.relocation.ArtifactRelocationSource;
import org.eclipse.aether.spi.relocation.ArtifactRelocationSourceProvider;

import static java.util.Objects.requireNonNull;

/**
 * Default Artifact relocation source provider.
 *
 * @since 2.0.0
 */
@Singleton
@Named
public final class DefaultArtifactRelocationSourceProvider implements ArtifactRelocationSourceProvider {

    private final Map<String, ArtifactRelocationSource> artifactRelocationSources;

    @Inject
    public DefaultArtifactRelocationSourceProvider(Map<String, ArtifactRelocationSource> artifactRelocationSources) {
        this.artifactRelocationSources = requireNonNull(artifactRelocationSources);
    }

    @Override
    public List<ArtifactRelocationSource> getSources(RepositorySystemSession session) {
        return PrioritizedComponents.reuseOrCreate(
                        session, artifactRelocationSources, ArtifactRelocationSource::getPriority)
                .getEnabled()
                .stream()
                .map(PrioritizedComponent::getComponent)
                .collect(Collectors.toList());
    }
}
