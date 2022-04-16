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
 * Split composer: splits to localPrefix (locally built and installed) and remotePrefix (cached). Both may be further
 * split by release or snapshots.
 *
 * @since TBD
 */
@Singleton
@Named( SplitLocalPathPrefixComposerFactory.NAME )
public final class SplitLocalPathPrefixComposerFactory extends LocalPathPrefixComposerFactorySupport
{
    public static final String NAME = "split";

    @Override
    public LocalPathPrefixComposer createComposer( RepositorySystemSession session )
    {
        return new SplitLocalPathPrefixComposer( getLocalPrefix( session ), isSplitLocal( session ),
                getRemotePrefix( session ), isSplitRemote( session ), getReleasePrefix( session ),
                getSnapshotPrefix( session ) );
    }

    /**
     * Split {@link LocalPathPrefixComposer} that splits installed and cached. Optionally, splits them by release or
     * snapshot as well.
     */
    public static class SplitLocalPathPrefixComposer extends LocalPathPrefixComposerSupport
    {
        public SplitLocalPathPrefixComposer( String localPrefix, boolean splitLocal, String remotePrefix,
                                             boolean splitRemote, String releasePrefix, String snapshotPrefix )
        {
            super( localPrefix, splitLocal, remotePrefix, splitRemote, releasePrefix, snapshotPrefix );
        }

        @Override
        public String getPathPrefixForLocalArtifact( Artifact artifact )
        {
            if ( splitLocal )
            {
                return localPrefix + "/" + ( artifact.isSnapshot() ? snapshotPrefix : releasePrefix );
            }
            else
            {
                return localPrefix;
            }
        }

        @Override
        public String getPathPrefixForRemoteArtifact( Artifact artifact, RemoteRepository repository, String context )
        {
            if ( splitRemote )
            {
                return remotePrefix + "/" + ( artifact.isSnapshot() ? snapshotPrefix : releasePrefix );
            }
            else
            {
                return remotePrefix;
            }
        }

        @Override
        public String getPathPrefixForLocalMetadata( Metadata metadata )
        {
            if ( splitLocal )
            {
                return localPrefix + "/" + ( isSnapshot( metadata ) ? snapshotPrefix : releasePrefix );
            }
            else
            {
                return localPrefix;
            }
        }

        @Override
        public String getPathPrefixForRemoteMetadata( Metadata metadata, RemoteRepository repository, String context )
        {
            if ( splitRemote )
            {
                return remotePrefix + "/" + ( isSnapshot( metadata ) ? snapshotPrefix : releasePrefix );
            }
            else
            {
                return remotePrefix;
            }
        }
    }
}
