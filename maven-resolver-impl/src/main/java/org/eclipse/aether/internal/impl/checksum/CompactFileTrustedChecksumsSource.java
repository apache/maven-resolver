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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactory;
import org.eclipse.aether.spi.connector.checksum.ProvidedChecksumsSource;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.artifact.ArtifactIdUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compact local filesystem backed {@link ProvidedChecksumsSource} implementation that use specified directory as base
 * directory, where it expects a "summary" file named as "checksums.${checksumExt}" for each checksum algorithm, and
 * file format is artifact ID and checksum separated by space, per line. The format supports comments "#" (hash) and
 * empty lines (both are ignored).
 *
 * @since TBD
 */
@Singleton
@Named( CompactFileTrustedChecksumsSource.NAME )
public final class CompactFileTrustedChecksumsSource
        extends FileTrustedChecksumsSourceSupport
{
    public static final String NAME = "file-compact";

    private static final String CHECKSUM_FILE_PREFIX = "checksums.";

    private static final String CONF_NAME_ORIGIN_AWARE = "originAware";

    private static final Logger LOGGER = LoggerFactory.getLogger( CompactFileTrustedChecksumsSource.class );

    private final ConcurrentHashMap<Path, ConcurrentHashMap<String, ConcurrentHashMap<String, String>>> checksumCache;

    @Inject
    public CompactFileTrustedChecksumsSource()
    {
        super( NAME );
        this.checksumCache = new ConcurrentHashMap<>();
    }

    @Override
    protected Map<String, String> performLookup( RepositorySystemSession session,
                                                 Path basedir,
                                                 Artifact artifact,
                                                 ArtifactRepository artifactRepository,
                                                 List<ChecksumAlgorithmFactory> checksumAlgorithmFactories )
    {
        final HashMap<String, String> checksums = new HashMap<>();
        final ConcurrentHashMap<String, ConcurrentHashMap<String, String>> baseDirProvidedHashes = checksumCache
                .computeIfAbsent( basedir, b -> new ConcurrentHashMap<>() );
        final boolean originAware = ConfigUtils.getBoolean(
                session, false, configPropKey( CONF_NAME_ORIGIN_AWARE ) );
        final String prefix;
        if ( originAware && artifactRepository != null )
        {
            prefix = artifactRepository.getId() + "-" + CHECKSUM_FILE_PREFIX;
        }
        else
        {
            prefix = CHECKSUM_FILE_PREFIX;
        }
        for ( ChecksumAlgorithmFactory checksumAlgorithmFactory : checksumAlgorithmFactories )
        {
            ConcurrentHashMap<String, String> algorithmHashes = baseDirProvidedHashes.computeIfAbsent(
                    checksumAlgorithmFactory.getName(),
                    algName -> loadProvidedHashes(
                            basedir.resolve( prefix + checksumAlgorithmFactory.getFileExtension() )
                    )
            );
            String checksum = algorithmHashes.get( ArtifactIdUtils.toId( artifact ) );
            if ( checksum != null )
            {
                checksums.put( checksumAlgorithmFactory.getName(), checksum );
            }
        }
        return checksums;
    }

    private ConcurrentHashMap<String, String> loadProvidedHashes( Path checksumFile )
    {
        ConcurrentHashMap<String, String> result = new ConcurrentHashMap<>();
        try
        {
            try ( BufferedReader reader = Files.newBufferedReader( checksumFile, StandardCharsets.UTF_8 ) )
            {
                LOGGER.debug( "Loading provided checksums file '{}'", checksumFile );
                String line = reader.readLine();
                while ( line != null )
                {
                    if ( !line.startsWith( "#" ) && !line.isEmpty() )
                    {
                        String[] parts = line.split( " ", 2 );
                        if ( parts.length == 2 )
                        {
                            String old = result.put( parts[0], parts[1] );
                            if ( old != null )
                            {
                                LOGGER.warn( "Checksums file '{}' contains duplicate checksums for artifact {}: "
                                        + "old '{}' replaced by new '{}'", checksumFile, parts[0], old, parts[1] );
                            }
                        }
                        else
                        {
                            LOGGER.warn( "Checksums file '{}' ignored malformed line '{}'", checksumFile, line );
                        }
                    }
                    line = reader.readLine();
                }
            }
        }
        catch ( NoSuchFileException e )
        {
            // ignore, will return empty result
            LOGGER.debug( "Checksums file '{}' not found", checksumFile );
        }
        catch ( IOException e )
        {
            throw new UncheckedIOException( e );
        }
        return result;
    }
}
