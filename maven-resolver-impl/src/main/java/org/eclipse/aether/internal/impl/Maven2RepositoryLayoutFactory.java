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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.internal.impl.checksum.DefaultChecksumAlgorithmFactorySelector;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactory;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactorySelector;
import org.eclipse.aether.spi.connector.layout.RepositoryLayout;
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutFactory;
import org.eclipse.aether.transfer.NoRepositoryLayoutException;
import org.eclipse.aether.util.ConfigUtils;

import static java.util.Objects.requireNonNull;

/**
 * Provides a Maven-2 repository layout for repositories with content type {@code "default"}.
 */
@Singleton
@Named( "maven2" )
public final class Maven2RepositoryLayoutFactory
        implements RepositoryLayoutFactory
{

    public static final String CONFIG_PROP_CHECKSUMS_ALGORITHMS = "aether.checksums.algorithms";

    private static final String DEFAULT_CHECKSUMS_ALGORITHMS = "SHA-1,MD5";

    public static final String CONFIG_PROP_OMIT_CHECKSUMS_FOR_EXTENSIONS =
            "aether.checksums.omitChecksumsForExtensions";

    private static final String DEFAULT_OMIT_CHECKSUMS_FOR_EXTENSIONS = ".asc";

    private float priority;

    private final ChecksumAlgorithmFactorySelector checksumAlgorithmFactorySelector;

    public float getPriority()
    {
        return priority;
    }

    /**
     * Service locator ctor.
     */
    @Deprecated
    public Maven2RepositoryLayoutFactory()
    {
        this( new DefaultChecksumAlgorithmFactorySelector() );
    }

    @Inject
    public Maven2RepositoryLayoutFactory( ChecksumAlgorithmFactorySelector checksumAlgorithmFactorySelector )
    {
        this.checksumAlgorithmFactorySelector = requireNonNull( checksumAlgorithmFactorySelector );
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
        requireNonNull( session, "session cannot be null" );
        requireNonNull( repository, "repository cannot be null" );
        if ( !"default".equals( repository.getContentType() ) )
        {
            throw new NoRepositoryLayoutException( repository );
        }
        // ensure order and uniqueness of (potentially user set) algorithm list
        LinkedHashSet<String> checksumsAlgorithmNames = Arrays.stream( ConfigUtils.getString(
                        session, DEFAULT_CHECKSUMS_ALGORITHMS, CONFIG_PROP_CHECKSUMS_ALGORITHMS )
                .split( "," )
        ).filter( s -> s != null && !s.trim().isEmpty() ).collect( Collectors.toCollection( LinkedHashSet::new ) );

        // validation: this loop implicitly validates the list above: selector will throw on unknown algorithm
        List<ChecksumAlgorithmFactory> checksumsAlgorithms = new ArrayList<>( checksumsAlgorithmNames.size() );
        for ( String checksumsAlgorithmName : checksumsAlgorithmNames )
        {
            checksumsAlgorithms.add( checksumAlgorithmFactorySelector.select( checksumsAlgorithmName ) );
        }

        // ensure uniqueness of (potentially user set) extension list
        Set<String> omitChecksumsForExtensions = Arrays.stream( ConfigUtils.getString(
                        session, DEFAULT_OMIT_CHECKSUMS_FOR_EXTENSIONS, CONFIG_PROP_OMIT_CHECKSUMS_FOR_EXTENSIONS )
                .split( "," )
        ).filter( s -> s != null && !s.trim().isEmpty() ).collect( Collectors.toSet() );

        // validation: enforce that all strings in this set are having leading dot
        if ( omitChecksumsForExtensions.stream().anyMatch( s -> !s.startsWith( "." ) ) )
        {
            throw new IllegalArgumentException(
                    String.format(
                            "The configuration %s contains illegal values: %s (all entries must start with '.' (dot)",
                            CONFIG_PROP_OMIT_CHECKSUMS_FOR_EXTENSIONS,
                            omitChecksumsForExtensions
                    )
            );
        }

        return new Maven2RepositoryLayout(
                checksumsAlgorithms,
                omitChecksumsForExtensions
        );
    }

    private static class Maven2RepositoryLayout
            implements RepositoryLayout
    {

        private final List<ChecksumAlgorithmFactory> checksumAlgorithms;

        private final Set<String> extensionsWithoutChecksums;

        private Maven2RepositoryLayout( List<ChecksumAlgorithmFactory> checksumAlgorithms,
                                        Set<String> extensionsWithoutChecksums )
        {
            this.checksumAlgorithms = Collections.unmodifiableList( checksumAlgorithms );
            this.extensionsWithoutChecksums = requireNonNull( extensionsWithoutChecksums );
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

        @Override
        public List<ChecksumAlgorithmFactory> getChecksumAlgorithmFactories()
        {
            return checksumAlgorithms;
        }

        @Override
        public boolean hasChecksums( Artifact artifact )
        {
            String artifactExtension = artifact.getExtension(); // ie. pom.asc
            for ( String extensionWithoutChecksums : extensionsWithoutChecksums )
            {
                if ( artifactExtension.endsWith( extensionWithoutChecksums ) )
                {
                    return false;
                }
            }
            return true;
        }

        @Override
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

        @Override
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

        @Override
        public List<ChecksumLocation> getChecksumLocations( Artifact artifact, boolean upload, URI location )
        {
            if ( !hasChecksums( artifact ) || isChecksum( artifact.getExtension() ) )
            {
                return Collections.emptyList();
            }
            return getChecksumLocations( location );
        }

        @Override
        public List<ChecksumLocation> getChecksumLocations( Metadata metadata, boolean upload, URI location )
        {
            return getChecksumLocations( location );
        }

        private List<ChecksumLocation> getChecksumLocations( URI location )
        {
            List<ChecksumLocation> checksumLocations = new ArrayList<>( checksumAlgorithms.size() );
            for ( ChecksumAlgorithmFactory checksumAlgorithmFactory : checksumAlgorithms )
            {
                checksumLocations.add( ChecksumLocation.forLocation( location, checksumAlgorithmFactory ) );
            }
            return checksumLocations;
        }

        private boolean isChecksum( String extension )
        {
            return checksumAlgorithms.stream().anyMatch( a -> extension.endsWith( "." + a.getFileExtension() ) );
        }
    }
}
