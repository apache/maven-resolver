package org.eclipse.aether.internal.impl.checksum;

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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.internal.impl.LocalPathComposer;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactory;
import org.eclipse.aether.spi.connector.checksum.ProvidedChecksumsSource;
import org.eclipse.aether.spi.io.FileProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

/**
 * Sparse local filesystem backed {@link ProvidedChecksumsSource} implementation that use specified directory as base
 * directory, where it expects artifacts checksums on standard Maven2 "local" layout. This implementation uses Artifact
 * coordinates solely to form path from baseDir in case of SHA-1 checksum).
 *
 * @since TBD
 */
@Singleton
@Named( SparseFileTrustedChecksumsSource.NAME )
public final class SparseFileTrustedChecksumsSource
        extends FileTrustedChecksumsSourceSupport
{
    public static final String NAME = "file-sparse";

    private static final Logger LOGGER = LoggerFactory.getLogger( SparseFileTrustedChecksumsSource.class );

    private final FileProcessor fileProcessor;

    private final LocalPathComposer localPathComposer;

    @Inject
    public SparseFileTrustedChecksumsSource( FileProcessor fileProcessor, LocalPathComposer localPathComposer )
    {
        super( NAME );
        this.fileProcessor = requireNonNull( fileProcessor );
        this.localPathComposer = requireNonNull( localPathComposer );
    }

    @Override
    protected Map<String, String> performLookup( RepositorySystemSession session,
                                                 Path basedir,
                                                 Artifact artifact,
                                                 ArtifactRepository artifactRepository,
                                                 List<ChecksumAlgorithmFactory> checksumAlgorithmFactories )
    {
        final HashMap<String, String> checksums = new HashMap<>();
        final String prefix;
        if ( isOriginAware( session ) )
        {
            if ( artifactRepository != null )
            {
                prefix = artifactRepository.getId() + "/";
            }
            else
            {
                prefix = session.getLocalRepository().getId() + "/";
            }
        }
        else
        {
            prefix = "";
        }

        List<ChecksumFilePath> checksumFilePaths = checksumAlgorithmFactories.stream().map(
                alg -> new ChecksumFilePath( prefix
                        + localPathComposer.getPathForArtifact( artifact, false ) + "." + alg.getFileExtension(),
                        alg
                )
        ).collect( toList() );
        for ( ChecksumFilePath checksumFilePath : checksumFilePaths )
        {
            Path checksumPath = basedir.resolve( checksumFilePath.path );
            try
            {
                String checksum = fileProcessor.readChecksum( checksumPath.toFile() );
                if ( checksum != null )
                {
                    checksums.put( checksumFilePath.checksumAlgorithmFactory.getName(), checksum );
                }
            }
            catch ( FileNotFoundException e )
            {
                LOGGER.debug( "No provided checksum file exist for '{}' at path '{}'", artifact, checksumPath );
            }
            catch ( IOException e )
            {
                LOGGER.warn( "Could not read provided checksum for '{}' at path '{}'", artifact, checksumPath, e );
            }
        }
        return checksums;
    }

    private static final class ChecksumFilePath
    {
        private final String path;

        private final ChecksumAlgorithmFactory checksumAlgorithmFactory;

        private ChecksumFilePath( String path, ChecksumAlgorithmFactory checksumAlgorithmFactory )
        {
            this.path = path;
            this.checksumAlgorithmFactory = checksumAlgorithmFactory;
        }
    }
}
