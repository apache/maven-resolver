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

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

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
     * Identity {@link TransformedArtifact}.
     */
    public static final class IdentityTransformedArtifact implements TransformedArtifact
    {
        private final Artifact artifact;

        public IdentityTransformedArtifact( Artifact artifact )
        {
            this.artifact = requireNonNull( artifact );
        }

        @Override
        public Artifact getTransformedArtifact()
        {
            return artifact;
        }

        @Override
        public void close()
        {
            // nop
        }

        @Override
        public String getGroupId()
        {
            return artifact.getGroupId();
        }

        @Override
        public String getArtifactId()
        {
            return artifact.getArtifactId();
        }

        @Override
        public String getVersion()
        {
            return artifact.getVersion();
        }

        @Override
        public Artifact setVersion( String version )
        {
            return artifact.setVersion( version );
        }

        @Override
        public String getBaseVersion()
        {
            return artifact.getBaseVersion();
        }

        @Override
        public boolean isSnapshot()
        {
            return artifact.isSnapshot();
        }

        @Override
        public String getClassifier()
        {
            return artifact.getClassifier();
        }

        @Override
        public String getExtension()
        {
            return artifact.getExtension();
        }

        @Override
        public File getFile()
        {
            return artifact.getFile();
        }

        @Override
        public Artifact setFile( File file )
        {
            return artifact.setFile( file );
        }

        @Override
        public String getProperty( String key, String defaultValue )
        {
            return artifact.getProperty( key, defaultValue );
        }

        @Override
        public Map<String, String> getProperties()
        {
            return artifact.getProperties();
        }

        @Override
        public Artifact setProperties( Map<String, String> properties )
        {
            return artifact.setProperties( properties );
        }
    }

    public static final ArtifactTransformer TRANSFORMER = new IdentityArtifactTransformer();

    private static class IdentityArtifactTransformer implements ArtifactTransformer
    {
        @Override
        public TransformedArtifact transformInstallArtifact( RepositorySystemSession session, Artifact artifact )
        {
            requireNonNull( session );
            return new IdentityTransformedArtifact( artifact );
        }

        @Override
        public TransformedArtifact transformDeployArtifact( RepositorySystemSession session, Artifact artifact )
        {
            requireNonNull( session );
            return new IdentityTransformedArtifact( artifact );
        }
    }

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
