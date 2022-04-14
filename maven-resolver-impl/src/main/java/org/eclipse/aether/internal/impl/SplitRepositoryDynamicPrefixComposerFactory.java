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
 * Default implementation of {@link DynamicPrefixComposerFactory}.
 *
 * @since TBD
 */
@Singleton
@Named( SplitRepositoryDynamicPrefixComposerFactory.NAME )
public final class SplitRepositoryDynamicPrefixComposerFactory extends DynamicPrefixComposerFactorySupport
{
    public static final String NAME = "split-repository";

    @Override
    protected DynamicPrefixComposer dpCreateComposer( RepositorySystemSession session, String localPrefix,
                                                      String remotePrefix, String releasePrefix, String snapshotPrefix )
    {
        return new SplitRepositoryDynamicPrefixComposer( localPrefix, remotePrefix, releasePrefix, snapshotPrefix );
    }

    private static final class SplitRepositoryDynamicPrefixComposer extends DynamicPrefixComposerSupport
    {
        private SplitRepositoryDynamicPrefixComposer( String localPrefix,
                                                      String remotePrefix,
                                                      String releasePrefix,
                                                      String snapshotPrefix )
        {
            super( localPrefix, remotePrefix, releasePrefix, snapshotPrefix );
        }

        @Override
        public boolean isRemoteSplitByOrigin()
        {
            return true;
        }

        @Override
        public String getPrefixForLocalArtifact( Artifact artifact )
        {
            return localPrefix;
        }

        @Override
        public String getPrefixForRemoteArtifact( Artifact artifact, RemoteRepository repository, String context )
        {
            return remotePrefix + "/"
                    + ( artifact.isSnapshot() ? snapshotPrefix : releasePrefix ) + '/'
                    + repository.getId();
        }

        @Override
        public String getPrefixForLocalMetadata( Metadata metadata )
        {
            return localPrefix;
        }

        @Override
        public String getPrefixForRemoteMetadata( Metadata metadata, RemoteRepository repository, String context )
        {
            return remotePrefix + "/"
                    + ( isSnapshot( metadata ) ? snapshotPrefix : releasePrefix ) + '/'
                    + repository.getId();
        }
    }
}
