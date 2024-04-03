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

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;

/**
 * An artifact decorator.
 *
 * @since 2.0.0
 */
public interface ArtifactDecorator {
    /**
     * A "no op" decorator. Usable by factories if factory does not want to decorate, as factory must return
     * non-{@code null} decorator instance.
     */
    ArtifactDecorator NOOP = new ArtifactDecorator() {
        @Override
        public Artifact decorateArtifact(ArtifactDescriptorResult artifactDescriptorResult) {
            return artifactDescriptorResult.getArtifact();
        }
    };

    /**
     * Decorates artifact.
     *
     * @param artifactDescriptorResult the artifact descriptor result of artifact, never {@code null}.
     * @return The decorated artifact, must never return {@code null}.
     */
    Artifact decorateArtifact(ArtifactDescriptorResult artifactDescriptorResult);
}
