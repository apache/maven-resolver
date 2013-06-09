/*******************************************************************************
 * Copyright (c) 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.internal.impl;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.inject.Named;

import org.codehaus.plexus.component.annotations.Component;
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
@Component( role = RepositoryLayoutFactory.class, hint = "maven2" )
public final class Maven2RepositoryLayoutFactory
    implements RepositoryLayoutFactory
{

    static final String CONFIG_PROP_SIGNATURE_CHECKSUMS = "aether.checksums.forSignature";

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
        return forSignature ? Maven2RepositoryLayout.INSTANCE : Maven2RepositoryLayoutEx.INSTANCE;
    }

    private static class Maven2RepositoryLayout
        implements RepositoryLayout
    {

        public static final RepositoryLayout INSTANCE = new Maven2RepositoryLayout();

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
            return Arrays.asList( Checksum.forLocation( location, "SHA-1" ), Checksum.forLocation( location, "MD5" ) );
        }

    }

    private static class Maven2RepositoryLayoutEx
        extends Maven2RepositoryLayout
    {

        public static final RepositoryLayout INSTANCE = new Maven2RepositoryLayoutEx();

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
