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

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactory;
import org.eclipse.aether.spi.connector.checksum.ProvidedChecksumsSource;
import org.eclipse.aether.spi.io.FileProcessor;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.artifact.ArtifactIdUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Local filesystem backed {@link ProvidedChecksumsSource} implementation that use specified directory as base
 * directory, where it expects artifacts checksums on standard Maven2 "local" layout. This implementation uses Artifact
 * (and Metadata) coordinates solely to form path from baseDir (for Metadata file name is
 * {@code maven-metadata-local.xml.sha1} in case of SHA-1 checksum).
 *
 * @since 1.8.0
 */
@Singleton
@Named( FileProvidedChecksumsSource.NAME )
public final class FileProvidedChecksumsSource
    implements ProvidedChecksumsSource
{
    public static final String NAME = "file";

    static final String CONFIG_PROP_BASE_DIR = "aether.artifactResolver.providedChecksumsSource.file.baseDir";

    static final String LOCAL_REPO_PREFIX = ".checksums";

    private static final Logger LOGGER = LoggerFactory.getLogger( FileProvidedChecksumsSource.class );

    private final FileProcessor fileProcessor;

    private final SimpleLocalRepositoryManager simpleLocalRepositoryManager;

    @Inject
    public FileProvidedChecksumsSource( FileProcessor fileProcessor )
    {
        this.fileProcessor = requireNonNull( fileProcessor );
        // we really needs just "local layout" from it (relative paths), so baseDir here is irrelevant
        this.simpleLocalRepositoryManager = new SimpleLocalRepositoryManager( new File( "" ) );
    }

    @Override
    public Map<String, String> getProvidedArtifactChecksums( RepositorySystemSession session,
                                                             ArtifactDownload transfer,
                                                             List<ChecksumAlgorithmFactory> checksumAlgorithmFactories )
    {
        Path baseDir = getBaseDir( session );
        if ( baseDir == null )
        {
            return null;
        }
        ArrayList<ChecksumFilePath> checksumFilePaths = new ArrayList<>( checksumAlgorithmFactories.size() );
        for ( ChecksumAlgorithmFactory checksumAlgorithmFactory : checksumAlgorithmFactories )
        {
            checksumFilePaths.add( new ChecksumFilePath(
                    simpleLocalRepositoryManager.getPathForArtifact( transfer.getArtifact(), false ) + '.'
                    + checksumAlgorithmFactory.getFileExtension(), checksumAlgorithmFactory ) );
        }
        return getProvidedChecksums( baseDir, checksumFilePaths, ArtifactIdUtils.toId( transfer.getArtifact() ) );
    }

    /**
     * May return {@code null}.
     */
    private Map<String, String> getProvidedChecksums( Path baseDir,
                                                      List<ChecksumFilePath> checksumFilePaths,
                                                      String subjectId )
    {
        HashMap<String, String> checksums = new HashMap<>();
        for ( ChecksumFilePath checksumFilePath : checksumFilePaths )
        {
            Path checksumPath =  baseDir.resolve( checksumFilePath.path );
            if ( Files.isReadable( checksumPath ) )
            {
                try
                {
                    String checksum = fileProcessor.readChecksum( checksumPath.toFile() );
                    if ( checksum != null )
                    {
                        LOGGER.debug( "Resolved provided checksum '{}:{}' for '{}'",
                                checksumFilePath.checksumAlgorithmFactory.getName(), checksum, subjectId );

                        checksums.put( checksumFilePath.checksumAlgorithmFactory.getName(), checksum );
                    }
                }
                catch ( IOException e )
                {
                    LOGGER.warn( "Could not read provided checksum for '{}' at path '{}'",
                            subjectId, checksumPath, e );
                }
            }
        }
        return checksums.isEmpty() ? null : checksums;
    }

    /**
     * Returns the base {@link URI} of directory where checksums are laid out, may return {@code null}.
     */
    private Path getBaseDir( RepositorySystemSession session )
    {
        final String baseDirPath = ConfigUtils.getString( session, null, CONFIG_PROP_BASE_DIR );
        final Path baseDir;
        if ( baseDirPath != null )
        {
            baseDir = Paths.get( baseDirPath );
        }
        else
        {
            baseDir = session.getLocalRepository().getBasedir().toPath().resolve( LOCAL_REPO_PREFIX );
        }
        if ( !Files.isDirectory( baseDir ) )
        {
            return null;
        }
        return baseDir;
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
