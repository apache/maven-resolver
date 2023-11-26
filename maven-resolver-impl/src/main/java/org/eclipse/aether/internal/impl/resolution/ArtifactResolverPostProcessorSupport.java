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
package org.eclipse.aether.internal.impl.resolution;

import java.util.List;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.internal.impl.DefaultArtifactResolver;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.spi.resolution.ArtifactResolverPostProcessor;

/**
 * Support class to implement {@link ArtifactResolverPostProcessor}.
 *
 * @since 1.9.0
 */
public abstract class ArtifactResolverPostProcessorSupport implements ArtifactResolverPostProcessor {
    protected static final String CONFIG_PROPS_PREFIX = DefaultArtifactResolver.CONFIG_PROPS_PREFIX + "postProcessor.";

    /**
     * This implementation will call into underlying code only if enabled.
     */
    @Override
    public void postProcess(RepositorySystemSession session, List<ArtifactResult> artifactResults) {
        if (isEnabled(session)) {
            doPostProcess(session, artifactResults);
        }
    }

    protected abstract void doPostProcess(RepositorySystemSession session, List<ArtifactResult> artifactResults);

    /**
     * Returns {@code true} if session configuration marks this instance as enabled.
     * <p>
     * Default value is {@code false}.
     */
    protected abstract boolean isEnabled(RepositorySystemSession session);
}
