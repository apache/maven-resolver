package org.eclipse.aether.internal.impl.filter;

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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.aether.MultiRuntimeException;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.spi.connector.filter.RemoteRepositoryFilter;
import org.eclipse.aether.spi.resolution.ArtifactResolverPostProcessor;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Remote repository filter source filtering on G coordinate. It is backed by a file that lists all allowed groupIds
 * and groupId not present in this file are filtered out.
 * <p>
 * The file can be authored manually: format is one groupId per line, comments starting with "#" (hash) amd empty lines
 * for structuring are supported. The file can also be pre-populated by "record" functionality of this filter.
 * When "recording", this filter will not filter out anything, but will instead populate the file with all encountered
 * groupIds.
 * <p>
 * The groupId file is expected on path "${basedir}/groupId-${repository.id}.txt".
 * <p>
 * The groupId file once loaded are cached in session, so in-flight groupId file change during session are NOT
 * noticed.
 *
 * @since TBD
 */
@Singleton
@Named( GroupIdRemoteRepositoryFilterSource.NAME )
public final class GroupIdRemoteRepositoryFilterSource
        extends RemoteRepositoryFilterSourceSupport
        implements ArtifactResolverPostProcessor
{
    public static final String NAME = "groupId";

    private static final String CONF_NAME_RECORD = "record";

    private static final String CONF_NAME_TRUNCATE_ON_SAVE = "truncateOnSave";

    static final String GROUP_ID_FILE_PREFIX = "groupId-";

    static final String GROUP_ID_FILE_SUFFIX = ".txt";

    /**
     * Key of rules cache in session data as remoteRepository.id -> Set(groupID).
     */
    private static final String RULES_KEY = GroupIdRemoteRepositoryFilterSource.class.getName() + ".rules";

    private static final String ON_CLOSE_HANDLER_REG_KEY = GroupIdRemoteRepositoryFilterSource.class.getName()
            + ".onCloseHandlerReg";

    private static final String NEW_GROUP_ID_RECORDED_KEY = GroupIdRemoteRepositoryFilterSource.class.getName()
            + ".newGroupIdRecorded";

    private static final Logger LOGGER = LoggerFactory.getLogger( GroupIdRemoteRepositoryFilterSource.class );

    @Inject
    public GroupIdRemoteRepositoryFilterSource()
    {
        super( NAME );
    }

    @Override
    public RemoteRepositoryFilter getRemoteRepositoryFilter( RepositorySystemSession session )
    {
        if ( isEnabled( session ) && !isRecord( session ) )
        {
            if ( Files.isDirectory( getBasedir( session, false ) ) )
            {
                return new GroupIdFilter( session );
            }
        }
        return null;
    }

    @Override
    public void postProcess( RepositorySystemSession session, List<ArtifactResult> artifactResults )
    {
        if ( isEnabled( session ) && isRecord( session ) )
        {
            if ( onCloseHandlerRegistered( session ).compareAndSet( false, true ) )
            {
                session.addOnCloseHandler( this::saveSessionRecordedLines );
            }
            ConcurrentHashMap<String, Set<String>> cache = cache( session );
            for ( ArtifactResult artifactResult : artifactResults )
            {
                if ( artifactResult.isResolved() && artifactResult.getRepository() instanceof RemoteRepository )
                {
                    boolean newGroupId = cache.computeIfAbsent( artifactResult.getRepository().getId(),
                                    f -> Collections.synchronizedSet( new TreeSet<>() ) )
                            .add( artifactResult.getArtifact().getGroupId() );
                    if ( newGroupId )
                    {
                        newGroupIdRecorded( session ).set( true );
                    }
                }
            }
        }
    }

    /**
     * Returns the groupId path. The file and parents may not exist, this method merely calculate the path.
     */
    private Path filePath( Path basedir, String remoteRepositoryId )
    {
        return basedir.resolve(
                GROUP_ID_FILE_PREFIX + remoteRepositoryId + GROUP_ID_FILE_SUFFIX );
    }

    @SuppressWarnings( "unchecked" )
    private ConcurrentHashMap<String, Set<String>> cache( RepositorySystemSession session )
    {
        return ( (ConcurrentHashMap<String, Set<String>>) session.getData()
                .computeIfAbsent( RULES_KEY, ConcurrentHashMap::new ) );
    }

    private Set<String> cacheRules( RepositorySystemSession session,
                                    RemoteRepository remoteRepository )
    {
        return cache( session ).computeIfAbsent( remoteRepository.getId(), id ->
                {
                    Set<String> rules = loadRepositoryRules(
                            filePath( getBasedir( session, false ), remoteRepository.getId() ) );
                    if ( rules != NOT_PRESENT )
                    {
                        LOGGER.info( "Loaded {} groupId for remote repository {}", rules.size(),
                                remoteRepository.getId() );
                    }
                    return rules;
                }
        );
    }

    private Set<String> loadRepositoryRules( Path filePath )
    {
        if ( Files.isReadable( filePath ) )
        {
            try ( BufferedReader reader = Files.newBufferedReader( filePath, StandardCharsets.UTF_8 ) )
            {
                TreeSet<String> result = new TreeSet<>();
                String groupId;
                while ( ( groupId = reader.readLine() ) != null )
                {
                    if ( !groupId.startsWith( "#" ) && !groupId.trim().isEmpty() )
                    {
                        result.add( groupId );
                    }
                }
                return Collections.unmodifiableSet( result );
            }
            catch ( IOException e )
            {
                throw new UncheckedIOException( e );
            }
        }
        return NOT_PRESENT;
    }

    private static final TreeSet<String> NOT_PRESENT = new TreeSet<>();

    private class GroupIdFilter implements RemoteRepositoryFilter
    {
        private final RepositorySystemSession session;

        private GroupIdFilter( RepositorySystemSession session )
        {
            this.session = session;
        }

        @Override
        public Result acceptArtifact( RemoteRepository remoteRepository, Artifact artifact )
        {
            return acceptGroupId( remoteRepository, artifact.getGroupId() );
        }

        @Override
        public Result acceptMetadata( RemoteRepository remoteRepository, Metadata metadata )
        {
            return acceptGroupId( remoteRepository, metadata.getGroupId() );
        }

        private Result acceptGroupId( RemoteRepository remoteRepository, String groupId )
        {
            Set<String> groupIds = cacheRules( session, remoteRepository );
            if ( NOT_PRESENT == groupIds )
            {
                return NOT_PRESENT_RESULT;
            }

            if ( groupIds.contains( groupId ) )
            {
                return new SimpleResult( true,
                        "G:" + groupId + " allowed from " + remoteRepository );
            }
            else
            {
                return new SimpleResult( false,
                        "G:" + groupId + " NOT allowed from " + remoteRepository );
            }
        }
    }

    private static final RemoteRepositoryFilter.Result NOT_PRESENT_RESULT = new SimpleResult(
            true, "GroupId file not present" );

    /**
     * Returns {@code true} if given session is recording.
     */
    private boolean isRecord( RepositorySystemSession session )
    {
        return ConfigUtils.getBoolean( session, false, configPropKey( CONF_NAME_RECORD ) );
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
     * Flag to preserve new groupId recorded state.
     */
    private AtomicBoolean newGroupIdRecorded( RepositorySystemSession session )
    {
        return (AtomicBoolean) session.getData().computeIfAbsent( NEW_GROUP_ID_RECORDED_KEY,
                () -> new AtomicBoolean( false ) );
    }

    /**
     * Returns {@code true} if truncate requested by session.
     */
    private boolean isTruncateOnSave( RepositorySystemSession session )
    {
        return ConfigUtils.getBoolean( session, false, configPropKey( CONF_NAME_TRUNCATE_ON_SAVE ) );
    }

    private void saveSessionRecordedLines( RepositorySystemSession session )
    {
        Map<String, Set<String>> recorded = cache ( session );
        if ( !newGroupIdRecorded( session ).get() )
        {
            return;
        }

        Path basedir = getBasedir( session, true );
        ArrayList<Exception> exceptions = new ArrayList<>();
        for ( Map.Entry<String, Set<String>> entry : recorded.entrySet() )
        {
            Path groupIdPath = filePath( basedir, entry.getKey() );
            Set<String> recordedLines = entry.getValue();
            if ( !recordedLines.isEmpty() )
            {
                try
                {
                    TreeSet<String> result = new TreeSet<>();
                    if ( !isTruncateOnSave( session ) )
                    {
                        result.addAll( loadRepositoryRules( groupIdPath ) );
                    }
                    result.addAll( recordedLines );

                    LOGGER.info( "Saving {} groupIds to '{}'", result.size(), groupIdPath );
                    FileUtils.writeFileWithBackup( groupIdPath, p -> Files.write( p, result ) );
                }
                catch ( IOException e )
                {
                    exceptions.add( e );
                }
            }
        }
        MultiRuntimeException.mayThrow( "session save groupIds failure", exceptions );
    }
}
