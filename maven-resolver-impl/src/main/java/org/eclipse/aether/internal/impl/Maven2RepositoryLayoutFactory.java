package org.eclipse.aether.internal.impl;

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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Named;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.layout.RepositoryLayout;
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutFactory;
import org.eclipse.aether.transfer.NoRepositoryLayoutException;
import org.eclipse.aether.util.ConfigUtils;

/**
 * Provides a Maven-2 repository layout for repositories with content type {@code "default"}.
 */
@Named( "maven2" )
public final class Maven2RepositoryLayoutFactory
    implements RepositoryLayoutFactory
{

    static final String CONFIG_PROP_SIGNATURE_CHECKSUMS = "aether.checksums.forSignature";
    static final String CONFIG_PROP_CHECKSUMS_ALGORITHMS = "aether.checksums.algorithms";

    static final String DEFAULT_CHECKSUMS_ALGORITHMS = "SHA-1,MD5";

    private float priority;

    public float getPriority()
    {
        return priority;
    }

    /**
     * Sets the priority of this component.
     *
     * @param priority The priority.
     * @return This component for chaining, never {@code null}.
     */
    public Maven2RepositoryLayoutFactory setPriority( float priority )
    {
        this.priority = priority;
        return this;
    }

    public RepositoryLayout newInstance( RepositorySystemSession session, RemoteRepository repository )
        throws NoRepositoryLayoutException
    {
        if ( !"default".equals( repository.getContentType() ) )
        {
            throw new NoRepositoryLayoutException( repository );
        }
        boolean forSignature = ConfigUtils.getBoolean( session, false, CONFIG_PROP_SIGNATURE_CHECKSUMS );
        List<String> checksumsAlgorithms = Arrays.asList( ConfigUtils.getString( session,
                DEFAULT_CHECKSUMS_ALGORITHMS, CONFIG_PROP_CHECKSUMS_ALGORITHMS ).split( "," ) );

        return forSignature
                ? new Maven2RepositoryLayout( checksumsAlgorithms )
                : new Maven2RepositoryLayoutEx( checksumsAlgorithms );
    }

    private static class Maven2RepositoryLayout
        implements RepositoryLayout
    {

        private final List<String> checksumsAlgorithms;

        protected Maven2RepositoryLayout( List<String> checksumsAlgorithms )
        {
            this.checksumsAlgorithms = checksumsAlgorithms;
        }

        private URI toUri( String path )
        {
            try
            {
                return new URI( null, null, path, null );
            }
            catch ( URISyntaxException e )
            {
                throw new IllegalStateException( e );
            }
        }

        public URI getLocation( Artifact artifact, boolean upload )
        {
            StringBuilder path = new StringBuilder( 128 );

            path.append( artifact.getGroupId().replace( '.', '/' ) ).append( '/' );

            path.append( artifact.getArtifactId() ).append( '/' );

            path.append( artifact.getBaseVersion() ).append( '/' );

            path.append( artifact.getArtifactId() ).append( '-' ).append( artifact.getVersion() );

            if ( artifact.getClassifier().length() > 0 )
            {
                path.append( '-' ).append( artifact.getClassifier() );
            }

            if ( artifact.getExtension().length() > 0 )
            {
                path.append( '.' ).append( artifact.getExtension() );
            }

            return toUri( path.toString() );
        }

        public URI getLocation( Metadata metadata, boolean upload )
        {
            StringBuilder path = new StringBuilder( 128 );

            if ( metadata.getGroupId().length() > 0 )
            {
                path.append( metadata.getGroupId().replace( '.', '/' ) ).append( '/' );

                if ( metadata.getArtifactId().length() > 0 )
                {
                    path.append( metadata.getArtifactId() ).append( '/' );

                    if ( metadata.getVersion().length() > 0 )
                    {
                        path.append( metadata.getVersion() ).append( '/' );
                    }
                }
            }

            path.append( metadata.getType() );

            return toUri( path.toString() );
        }

        public List<Checksum> getChecksums( Artifact artifact, boolean upload, URI location )
        {
            return getChecksums( location );
        }

        public List<Checksum> getChecksums( Metadata metadata, boolean upload, URI location )
        {
            return getChecksums( location );
        }

        private List<Checksum> getChecksums( URI location )
        {
            List<Checksum> checksums = new ArrayList<>( checksumsAlgorithms.size() );
            for ( String algorithm : checksumsAlgorithms )
            {
                checksums.add( Checksum.forLocation( location, algorithm ) );
            }
            return checksums;
        }

    }

    private static class Maven2RepositoryLayoutEx
        extends Maven2RepositoryLayout
    {

        protected Maven2RepositoryLayoutEx( List<String> checksumsAlgorithms )
        {
            super( checksumsAlgorithms );
        }

        @Override
        public List<Checksum> getChecksums( Artifact artifact, boolean upload, URI location )
        {
            if ( isSignature( artifact.getExtension() ) )
            {
                return Collections.emptyList();
            }
            return super.getChecksums( artifact, upload, location );
        }

        private boolean isSignature( String extension )
        {
            return extension.endsWith( ".asc" );
        }

    }

}
