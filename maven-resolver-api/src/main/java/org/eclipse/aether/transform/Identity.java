package org.eclipse.aether.transform;

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

import java.util.Collection;
import java.util.Collections;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;

import static java.util.Objects.requireNonNull;

/**
 * Identity {@link TransformedArtifact}, {@link ArtifactTransformer} and {@link ArtifactTransformerManager}.
 *
 * @since 1.9.3
 */
public final class Identity
{
    /**
     * Returns "identity" transform artifact, in essence the same {@link Artifact} that was passed to this method.
     */
    public static TransformedArtifact identity( Artifact artifact )
    {
        return new IdentityTransformedArtifact( artifact );
    }

    private static final class IdentityTransformedArtifact extends TransformedArtifact
    {
        private final Artifact artifact;

        private IdentityTransformedArtifact( Artifact artifact )
        {
            this.artifact = requireNonNull( artifact );
        }

        @Override
        public Artifact getTransformedArtifact()
        {
            return artifact;
        }
    }

    /**
     * The "identity" transformer.
     */
    public static final ArtifactTransformer TRANSFORMER = new IdentityArtifactTransformer();

    private static class IdentityArtifactTransformer implements ArtifactTransformer
    {
        @Override
        public TransformedArtifact transformInstallArtifact( RepositorySystemSession session, Artifact artifact )
        {
            requireNonNull( session );
            return identity( artifact );
        }

        @Override
        public TransformedArtifact transformDeployArtifact( RepositorySystemSession session, Artifact artifact )
        {
            requireNonNull( session );
            return identity( artifact );
        }
    }

    /**
     * The "identity" transformer manager.
     */
    public static final ArtifactTransformerManager MANAGER = new IdentityArtifactTransformerManager();

    private static class IdentityArtifactTransformerManager implements ArtifactTransformerManager
    {
        private final Collection<ArtifactTransformer> identity = Collections.singletonList( TRANSFORMER );

        @Override
        public Collection<ArtifactTransformer> getTransformersForArtifact( Artifact artifact )
        {
            return identity;
        }
    }
}
