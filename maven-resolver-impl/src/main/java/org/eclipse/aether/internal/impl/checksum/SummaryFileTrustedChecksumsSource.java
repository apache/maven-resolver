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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.aether.MultiRuntimeException;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.internal.impl.LocalPathComposer;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactory;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

/**
 * Compact file {@link FileTrustedChecksumsSourceSupport} implementation that use specified directory as base
 * directory, where it expects a "summary" file named as "checksums.${checksumExt}" for each checksum algorithm.
 * File format is GNU Coreutils compatible: each line holds checksum followed by two spaces and artifact relative path
 * (from local repository root, without leading "./"). This means that trusted checksums summary file can be used to
 * validate artifacts or generate it using standard GNU tools like GNU {@code sha1sum} is (for BSD derivatives same
 * file can be used with {@code -r} switch).
 * <p>
 * The format supports comments "#" (hash) and empty lines for easier structuring the file content, and both are
 * ignored. Also, their presence makes the summary file incompatible with GNU Coreutils format. On save of the
 * summary file, the comments and empty lines are lost, and file is sorted by path names for easier diffing
 * (2nd column in file).
 * <p>
 * The source by default is "origin aware", and it will factor in origin repository ID as well into summary file name,
 * for example "checksums-central.sha256".
 * <p>
 * Example commands for managing summary file (in examples will use repository ID "central"):
 * <ul>
 *     <li>To create summary file: {@code find * -not -name "checksums-central.sha256" -type f -print0 |
 *       xargs -0 sha256sum | sort -k 2 > checksums-central.sha256}</li>
 *     <li>To verify artifacts using summary file: {@code sha256sum --quiet -c checksums-central.sha256}</li>
 * </ul>
 * <p>
 * The checksums summary file is lazily loaded and remains cached in session, so file changes during lifecycle of the
 * session are not picked up. This implementation can be simultaneously used to lookup and also write checksums. The
 * written checksums will become visible only for writer session, and newly written checksums, if any, will be flushed
 * at session end, merged with existing ones on disk, unless {@code truncateOnSave} is enabled.
 * <p>
 * The name of this implementation is "summary-file".
 *
 * @since 1.9.0
 * @see <a href="https://man7.org/linux/man-pages/man1/sha1sum.1.html">sha1sum man page</a>
 * @see <a href="https://www.gnu.org/software/coreutils/manual/coreutils.html#md5sum-invocation">GNU Coreutils: md5sum</a>
 */
@Singleton
@Named( SummaryFileTrustedChecksumsSource.NAME )
public final class SummaryFileTrustedChecksumsSource
        extends FileTrustedChecksumsSourceSupport
{
    public static final String NAME = "summary-file";

    private static final String CHECKSUMS_FILE_PREFIX = "checksums";

    private static final String CONF_NAME_TRUNCATE_ON_SAVE = "truncateOnSave";

    /**
     * Session key for path -> artifactId -> checksum nested map. The trick is that 1st level key "path" composition
     * may change across sessions, based on session being origin aware or not.
     */
    private static final String CHECKSUMS_KEY = SummaryFileTrustedChecksumsSource.class.getName() + ".checksums";

    private static final String ON_CLOSE_HANDLER_REG_KEY = SummaryFileTrustedChecksumsSource.class.getName()
            + ".onCloseHandlerRwg";

    private static final String NEW_CHECKSUMS_RECORDED_KEY = SummaryFileTrustedChecksumsSource.class.getName()
            + ".newChecksumsRecorded";

    private static final Logger LOGGER = LoggerFactory.getLogger( SummaryFileTrustedChecksumsSource.class );

    private final LocalPathComposer localPathComposer;

    @Inject
    public SummaryFileTrustedChecksumsSource( LocalPathComposer localPathComposer )
    {
        super( NAME );
        this.localPathComposer = requireNonNull( localPathComposer );
    }

    @Override
    protected Map<String, String> doGetTrustedArtifactChecksums(
            RepositorySystemSession session, Artifact artifact, ArtifactRepository artifactRepository,
            List<ChecksumAlgorithmFactory> checksumAlgorithmFactories )
    {
        final HashMap<String, String> checksums = new HashMap<>();
        final Path basedir = getBasedir( session, false );
        if ( Files.isDirectory( basedir ) )
        {
            final String artifactPath = localPathComposer.getPathForArtifact( artifact, false );
            final boolean originAware = isOriginAware( session );
            final ConcurrentHashMap<Path, ConcurrentHashMap<String, String>> cache = cache( session );
            for ( ChecksumAlgorithmFactory checksumAlgorithmFactory : checksumAlgorithmFactories )
            {
                Path summaryFile = summaryFile( basedir, originAware, artifactRepository.getId(),
                        checksumAlgorithmFactory.getFileExtension() );
                ConcurrentHashMap<String, String> algorithmChecksums = cache.computeIfAbsent( summaryFile, f ->
                        {
                            ConcurrentHashMap<String, String> loaded = loadProvidedChecksums( summaryFile );
                            if ( Files.isRegularFile( summaryFile ) )
                            {
                                LOGGER.info( "Loaded {} {} trusted checksums for remote repository {}",
                                        loaded.size(), checksumAlgorithmFactory.getName(), artifactRepository.getId() );
                            }
                            return loaded;
                        }
                );
                String checksum = algorithmChecksums.get( artifactPath );
                if ( checksum != null )
                {
                    checksums.put( checksumAlgorithmFactory.getName(), checksum );
                }
            }
        }
        return checksums;
    }

    @Override
    protected SummaryFileWriter doGetTrustedArtifactChecksumsWriter( RepositorySystemSession session )
    {
        if ( onCloseHandlerRegistered( session ).compareAndSet( false, true ) )
        {
            session.addOnCloseHandler( this::saveSessionRecordedLines );
        }
        return new SummaryFileWriter( session, cache( session ), getBasedir( session, true ),
                isOriginAware( session ) );
    }

    @SuppressWarnings( "unchecked" )
    private ConcurrentHashMap<Path, ConcurrentHashMap<String, String>> cache( RepositorySystemSession session )
    {
        return (ConcurrentHashMap<Path, ConcurrentHashMap<String, String>>) session.getData()
                .computeIfAbsent( CHECKSUMS_KEY, ConcurrentHashMap::new );
    }

    /**
     * Returns the summary file path. The file itself and its parent directories may not exist, this method merely
     * calculate the path.
     */
    private Path summaryFile( Path basedir, boolean originAware, String repositoryId, String checksumExtension )
    {
        String fileName = CHECKSUMS_FILE_PREFIX;
        if ( originAware )
        {
            fileName += "-" + repositoryId;
        }
        return basedir.resolve( fileName + "." + checksumExtension );
    }

    private ConcurrentHashMap<String, String> loadProvidedChecksums( Path summaryFile )
    {
        ConcurrentHashMap<String, String> result = new ConcurrentHashMap<>();
        if ( Files.isRegularFile( summaryFile ) )
        {
            try ( BufferedReader reader = Files.newBufferedReader( summaryFile, StandardCharsets.UTF_8 ) )
            {
                String line;
                while ( ( line = reader.readLine() ) != null )
                {
                    if ( !line.startsWith( "#" ) && !line.isEmpty() )
                    {
                        String[] parts = line.split( "  ", 2 );
                        if ( parts.length == 2 )
                        {
                            String newChecksum = parts[0];
                            String artifactPath = parts[1];
                            String oldChecksum = result.put( artifactPath, newChecksum );
                            if ( oldChecksum != null )
                            {
                                if ( Objects.equals( oldChecksum, newChecksum ) )
                                {
                                    LOGGER.warn(
                                            "Checksums file '{}' contains duplicate checksums for artifact {}: {}",
                                            summaryFile, artifactPath, oldChecksum );
                                }
                                else
                                {
                                    LOGGER.warn(
                                            "Checksums file '{}' contains different checksums for artifact {}: "
                                                    + "old '{}' replaced by new '{}'", summaryFile, artifactPath,
                                            oldChecksum, newChecksum );
                                }
                            }
                        }
                        else
                        {
                            LOGGER.warn( "Checksums file '{}' ignored malformed line '{}'", summaryFile, line );
                        }
                    }
                }
            }
            catch ( IOException e )
            {
                throw new UncheckedIOException( e );
            }
        }
        return result;
    }

    private class SummaryFileWriter implements Writer
    {
        private final RepositorySystemSession session;
        private final ConcurrentHashMap<Path, ConcurrentHashMap<String, String>> cache;

        private final Path basedir;

        private final boolean originAware;

        private SummaryFileWriter( RepositorySystemSession session,
                                   ConcurrentHashMap<Path, ConcurrentHashMap<String, String>> cache,
                                   Path basedir,
                                   boolean originAware )
        {
            this.session = session;
            this.cache = cache;
            this.basedir = basedir;
            this.originAware = originAware;
        }

        @Override
        public void addTrustedArtifactChecksums( Artifact artifact,
                                                 ArtifactRepository artifactRepository,
                                                 List<ChecksumAlgorithmFactory> checksumAlgorithmFactories,
                                                 Map<String, String> trustedArtifactChecksums )
        {
            String artifactPath = localPathComposer.getPathForArtifact( artifact, false );
            for ( ChecksumAlgorithmFactory checksumAlgorithmFactory : checksumAlgorithmFactories )
            {
                Path summaryFile = summaryFile( basedir, originAware, artifactRepository.getId(),
                        checksumAlgorithmFactory.getFileExtension() );
                String checksum = requireNonNull(
                        trustedArtifactChecksums.get( checksumAlgorithmFactory.getName() ) );

                String oldChecksum = cache.computeIfAbsent( summaryFile, k -> loadProvidedChecksums( summaryFile ) )
                        .put( artifactPath, checksum );

                if ( oldChecksum == null )
                {
                    newChecksumsRecorded( session ).set( true ); // new
                }
                else if ( !Objects.equals( oldChecksum, checksum ) )
                {
                    newChecksumsRecorded( session ).set( true ); // updated
                    LOGGER.info( "Trusted checksum for artifact {} replaced: old {}, new {}",
                            artifact, oldChecksum, checksum );
                }
            }
        }

        @Override
        public void close()
        {
            // nop
        }
    }

    /**
     * Returns {@code true} if on save existing checksums file should be truncated. Otherwise, existing file is merged
     * with newly recorded checksums.
     * <p>
     * Default value is {@code false}.
     */
    private boolean isTruncateOnSave( RepositorySystemSession session )
    {
        return ConfigUtils.getBoolean( session, false, configPropKey( CONF_NAME_TRUNCATE_ON_SAVE ) );
    }

    /**
     * Flag to preserve on-close handler registration state.
     */
    private AtomicBoolean onCloseHandlerRegistered( RepositorySystemSession session )
    {
        return (AtomicBoolean) session.getData().computeIfAbsent( ON_CLOSE_HANDLER_REG_KEY,
                () -> new AtomicBoolean( false ) );
    }

    /**
     * Flag to preserve new checksums recorded state.
     */
    private AtomicBoolean newChecksumsRecorded( RepositorySystemSession session )
    {
        return (AtomicBoolean) session.getData().computeIfAbsent( NEW_CHECKSUMS_RECORDED_KEY,
                () -> new AtomicBoolean( false ) );
    }

    /**
     * On-close handler that saves recorded checksums, if any.
     */
    private void saveSessionRecordedLines( RepositorySystemSession session )
    {
        if ( !newChecksumsRecorded( session ).get() )
        {
            return;
        }

        Map<Path, ConcurrentHashMap<String, String>> cache = cache( session );
        ArrayList<Exception> exceptions = new ArrayList<>();
        for ( Map.Entry<Path, ConcurrentHashMap<String, String>> entry : cache.entrySet() )
        {
            Path summaryFile = entry.getKey();
            ConcurrentHashMap<String, String> recordedLines = entry.getValue();
            if ( !recordedLines.isEmpty() )
            {
                try
                {
                    ConcurrentHashMap<String, String> result = new ConcurrentHashMap<>();
                    if ( !isTruncateOnSave( session ) )
                    {
                        result.putAll( loadProvidedChecksums( summaryFile ) );
                    }
                    result.putAll( recordedLines );

                    LOGGER.info( "Saving {} checksums to '{}'", result.size(), summaryFile );
                    FileUtils.writeFileWithBackup(
                            summaryFile,
                            p -> Files.write( p,
                                    result.entrySet().stream()
                                            .sorted( Map.Entry.comparingByValue() )
                                            .map( e -> e.getValue() + "  " + e.getKey() )
                                            .collect( toList() )
                            )
                    );
                }
                catch ( IOException e )
                {
                    exceptions.add( e );
                }
            }
        }
        MultiRuntimeException.mayThrow( "session save checksums failure", exceptions );
    }
}
