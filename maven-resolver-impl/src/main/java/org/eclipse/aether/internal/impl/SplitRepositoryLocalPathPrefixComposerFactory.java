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
 * Split repository composer. Similar to "split", but factors in {@link RemoteRepository#getId()} for remotePrefix
 * as well, effectively splitting cache per origin.
 *
 * @since TBD
 */
@Singleton
@Named( SplitRepositoryLocalPathPrefixComposerFactory.NAME )
public final class SplitRepositoryLocalPathPrefixComposerFactory extends LocalPathPrefixComposerFactorySupport
{
    public static final String NAME = "split-repository";

    @Override
    public LocalPathPrefixComposer createComposer( RepositorySystemSession session )
    {
        return new SplitRepositoryLocalPathPrefixComposer( getLocalPrefix( session ), isSplitLocal( session ),
                getRemotePrefix( session ), isSplitRemote( session ), getReleasePrefix( session ),
                getSnapshotPrefix( session ) );
    }

    /**
     * Split repository {@link LocalPathPrefixComposer} that extends
     * {@link SplitLocalPathPrefixComposerFactory.SplitLocalPathPrefixComposer} and for remote artifacts and metadata
     * factors in {@link RemoteRepository#getId()} as well.
     */
    public static class SplitRepositoryLocalPathPrefixComposer
            extends SplitLocalPathPrefixComposerFactory.SplitLocalPathPrefixComposer
    {
        public SplitRepositoryLocalPathPrefixComposer( String localPrefix, boolean splitLocal, String remotePrefix,
                                                       boolean splitRemote, String releasePrefix,
                                                       String snapshotPrefix )
        {
            super( localPrefix, splitLocal, remotePrefix, splitRemote, releasePrefix, snapshotPrefix );
        }

        @Override
        public String getPathPrefixForRemoteArtifact( Artifact artifact, RemoteRepository repository, String context )
        {
            return super.getPathPrefixForRemoteArtifact( artifact, repository, context ) + "/" + repository.getId();
        }

        @Override
        public String getPathPrefixForRemoteMetadata( Metadata metadata, RemoteRepository repository, String context )
        {
            return super.getPathPrefixForRemoteMetadata( metadata, repository, context ) + "/" + repository.getId();
        }
    }
}
