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

import javax.inject.Named;
import javax.inject.Singleton;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Default local path prefix composer factory: it creates {@link LocalPathPrefixComposer} instances of (internal) type
 * {@link DefaultLocalPathPrefixComposer} that observe and implement all the supported parameters predefined in support
 * class.
 *
 * @since TBD
 */
@Singleton
@Named
public final class DefaultLocalPathPrefixComposerFactory extends LocalPathPrefixComposerFactorySupport
{
    @Override
    public LocalPathPrefixComposer createComposer( RepositorySystemSession session )
    {
        return new DefaultLocalPathPrefixComposer( isSplit( session ), getLocalPrefix( session ),
                isSplitLocal( session ), getRemotePrefix( session ), isSplitRemote( session ),
                isSplitRemoteRepository( session ), isSplitRemoteRepositoryLast( session ),
                getReleasesPrefix( session ), getSnapshotsPrefix( session ) );
    }

    /**
     * {@link LocalPathPrefixComposer} implementation that observe and implement all the supported parameters.
     */
    private static class DefaultLocalPathPrefixComposer extends LocalPathPrefixComposerSupport
    {
        @SuppressWarnings( "checkstyle:parameternumber" )
        private DefaultLocalPathPrefixComposer( boolean split, String localPrefix, boolean splitLocal,
                                               String remotePrefix, boolean splitRemote, boolean splitRemoteRepository,
                                               boolean splitRemoteRepositoryLast,
                                               String releasePrefix, String snapshotPrefix )
        {
            super( split, localPrefix, splitLocal, remotePrefix, splitRemote, splitRemoteRepository,
                    splitRemoteRepositoryLast, releasePrefix, snapshotPrefix );
        }

        @Override
        public String getPathPrefixForLocalArtifact( Artifact artifact )
        {
            if ( !split )
            {
                return null;
            }
            String result = localPrefix;
            if ( splitLocal )
            {
                result += "/" + ( artifact.isSnapshot() ? snapshotsPrefix : releasesPrefix );
            }
            return result;
        }

        @Override
        public String getPathPrefixForRemoteArtifact( Artifact artifact, RemoteRepository repository )
        {
            if ( !split )
            {
                return null;
            }
            String result = remotePrefix;
            if ( !splitRemoteRepositoryLast && splitRemoteRepository )
            {
                result += "/" + repository.getId();
            }
            if ( splitRemote )
            {
                result += "/" + ( artifact.isSnapshot() ? snapshotsPrefix : releasesPrefix );
            }
            if ( splitRemoteRepositoryLast && splitRemoteRepository )
            {
                result += "/" + repository.getId();
            }
            return result;
        }

        @Override
        public String getPathPrefixForLocalMetadata( Metadata metadata )
        {
            if ( !split )
            {
                return null;
            }
            String result = localPrefix;
            if ( splitLocal )
            {
                result += "/" + ( isSnapshot( metadata ) ? snapshotsPrefix : releasesPrefix );
            }
            return result;
        }

        @Override
        public String getPathPrefixForRemoteMetadata( Metadata metadata, RemoteRepository repository )
        {
            if ( !split )
            {
                return null;
            }
            String result = remotePrefix;
            if ( !splitRemoteRepositoryLast && splitRemoteRepository )
            {
                result += "/" + repository.getId();
            }
            if ( splitRemote )
            {
                result += "/" + ( isSnapshot( metadata ) ? snapshotsPrefix : releasesPrefix );
            }
            if ( splitRemoteRepositoryLast && splitRemoteRepository )
            {
                result += "/" + repository.getId();
            }
            return result;
        }
    }
}
