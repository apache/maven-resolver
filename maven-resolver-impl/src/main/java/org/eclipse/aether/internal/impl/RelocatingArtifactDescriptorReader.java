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

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.impl.ArtifactDescriptorReader;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.spi.relocation.ArtifactRelocationSource;
import org.eclipse.aether.spi.relocation.ArtifactRelocationSourceProvider;

import static java.util.Objects.requireNonNull;

/**
 * An implementation of {@link ArtifactDescriptorReader} that has knowledge about relocations ahead the time using
 * {@link ArtifactRelocationSource} instances. Hence, unlike plain artifact descriptor reader, this one "knows"
 * relation is asked for (needed) ahead of time. The real work is delegated to other implementation.
 *
 * @since 2.0.0
 * @provisional This type is provisional and can be changed, moved or removed without prior notice.
 */
public final class RelocatingArtifactDescriptorReader implements ArtifactDescriptorReader {
    private final ArtifactDescriptorReader delegate;

    private final ArtifactRelocationSourceProvider artifactRelocationSourceProvider;

    public RelocatingArtifactDescriptorReader(
            ArtifactDescriptorReader delegate, ArtifactRelocationSourceProvider artifactRelocationSourceProvider) {
        this.delegate = requireNonNull(delegate);
        this.artifactRelocationSourceProvider = requireNonNull(artifactRelocationSourceProvider);
    }

    @Override
    public ArtifactDescriptorResult readArtifactDescriptor(
            RepositorySystemSession session, ArtifactDescriptorRequest request) throws ArtifactDescriptorException {
        Artifact originalTarget = request.getArtifact();
        Artifact actualTarget = originalTarget;
        boolean relocated = false;
        for (ArtifactRelocationSource source : artifactRelocationSourceProvider.getSources(session)) {
            actualTarget = source.relocatedTarget(session, originalTarget);
            if (actualTarget != null && !originalTarget.equals(actualTarget)) {
                relocated = true;
                break;
            }
        }
        if (!relocated) {
            return delegate.readArtifactDescriptor(session, request);
        }

        ArtifactDescriptorRequest relocatedRequest =
                new ArtifactDescriptorRequest(actualTarget, request.getRepositories(), request.getRequestContext());
        relocatedRequest.setTrace(request.getTrace());
        ArtifactDescriptorResult relocatedResult = delegate.readArtifactDescriptor(session, relocatedRequest);
        relocatedResult.getRequest().setArtifact(originalTarget);
        relocatedResult.addRelocation(actualTarget);
        return relocatedResult;
    }
}
