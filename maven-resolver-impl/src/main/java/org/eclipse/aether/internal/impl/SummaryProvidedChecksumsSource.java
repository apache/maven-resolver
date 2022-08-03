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
import org.eclipse.aether.util.ConfigUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Local filesystem backed {@link ProvidedChecksumsSource} implementation that use specified directory as base
 * directory, where it expects summary files per checksum type in the format 'artifact checksum' per line.
 * Each file is expected to be named after its represented algorithm, e.g. 'summary.sha256' for SHA-256
 * checksums.
 *
 * @since 1.8.3
 */
@Singleton
@Named( SummaryProvidedChecksumsSource.NAME )
public final class SummaryProvidedChecksumsSource
    implements ProvidedChecksumsSource
{
    public static final String NAME = "summary";

    static final String CONFIG_PROP_FILE = "aether.artifactResolver.providedChecksumsSource.summary.baseDir";

    static final String LOCAL_REPO_PREFIX = ".checksums";

    private static final Logger LOGGER = LoggerFactory.getLogger( SummaryProvidedChecksumsSource.class );

    private final ConcurrentMap<Path, ConcurrentMap<String, Map<String, String>>> cached = new ConcurrentHashMap<>();

    private final Set<Path> read = new HashSet<>();

    @Inject
    public SummaryProvidedChecksumsSource()
    {
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
        ConcurrentMap<String, Map<String, String>> cache = cached.computeIfAbsent(
                baseDir,
                path -> new ConcurrentHashMap<>() );
        readChecksums( baseDir, checksumAlgorithmFactories, cache );
        Map<String, String> values = cache.get( transfer.getArtifact().toString() );
        return values == null ? null : Collections.unmodifiableMap( values );
    }

    /**
     * Populates the cache of previously read files.
     */
    private void readChecksums( Path baseDir,
                                List<ChecksumAlgorithmFactory> checksumAlgorithmFactories,
                                ConcurrentMap<String, Map<String, String>> cache )
    {
        for ( ChecksumAlgorithmFactory checksumAlgorithmFactory : checksumAlgorithmFactories )
        {
            Path file = baseDir.resolve( "summary." + checksumAlgorithmFactory.getFileExtension() );
            if ( Files.isRegularFile( file )
                    && Files.isReadable( file )
                    && read.add( file ) )
            {
                try ( BufferedReader reader = Files.newBufferedReader( file ) )
                {
                    String line;
                    while ( ( line = reader.readLine() ) != null )
                    {
                        String[] segments = line.split( " ", 2 );
                        if ( segments.length != 2 )
                        {
                            LOGGER.debug(
                                    "Ignored line '{}' in '{}' which does not follow the expected checksum format",
                                    line, file );
                        }
                        else
                        {
                            cache.merge( segments[0],
                                    Collections.singletonMap( checksumAlgorithmFactory.getName(), segments[1] ),
                                    ( left, right ) ->
                                    {
                                        Map<String, String> merged = new HashMap<>();
                                        merged.putAll( left );
                                        merged.putAll( right );
                                        right.forEach( ( key, value ) ->
                                        {
                                            String previous = left.get( key );
                                            if ( previous != null  && !previous.equals( value ) )
                                            {
                                                LOGGER.warn(
                                                        "Found both checksums '{}' and '{}' for '{}', using latter",
                                                        previous, value, checksumAlgorithmFactory.getName() );
                                            }
                                        } );
                                        return merged;
                                    } );
                        }
                    }
                }
                catch ( IOException e )
                {
                    LOGGER.warn( "Could not read provided checksum for '{}' at path '{}'",
                            checksumAlgorithmFactory.getName(), file, e );
                }
            }
        }
    }

    private Path getBaseDir( RepositorySystemSession session )
    {
        String baseDirPath = ConfigUtils.getString( session, null, CONFIG_PROP_FILE );
        Path baseDir;
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
}
