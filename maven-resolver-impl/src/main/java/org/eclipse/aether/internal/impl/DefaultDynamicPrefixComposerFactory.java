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
import org.eclipse.aether.util.ConfigUtils;

/**
 * Default implementation of {@link DynamicPrefixComposerFactory}.
 *
 * @since TBD
 */
@Singleton
@Named
public final class DefaultDynamicPrefixComposerFactory implements DynamicPrefixComposerFactory
{
    private static final String SPLIT_COMPOSER = "split";

    private static final String SPLIT_REPOSITORY_COMPOSER = "split-repository";

    private static final String CONFIG_PROP_COMPOSER = "aether.dynamicLocalRepository.composer";

    private static final String DEFAULT_COMPOSER = SPLIT_REPOSITORY_COMPOSER;

    @Override
    public DynamicPrefixComposer createComposer( RepositorySystemSession session )
    {
        String composer = ConfigUtils.getString( session, DEFAULT_COMPOSER, CONFIG_PROP_COMPOSER );

        if ( SPLIT_COMPOSER.equals( composer ) || SPLIT_REPOSITORY_COMPOSER.equals( composer ) )
        {
            String localPrefix = ConfigUtils.getString(
                    session, "local", "aether.dynamicLocalRepository.localPrefix" );
            String remotePrefix = ConfigUtils.getString(
                    session, "remote", "aether.dynamicLocalRepository.remotePrefix" );
            String releasePrefix = ConfigUtils.getString(
                    session, "release", "aether.dynamicLocalRepository.releasePrefix" );
            String snapshotPrefix = ConfigUtils.getString(
                    session, "snapshot", "aether.dynamicLocalRepository.snapshotPrefix" );

            if ( SPLIT_COMPOSER.equals( composer ) )
            {
                return new SplitDynamicPrefixComposer(
                        localPrefix, remotePrefix, releasePrefix, snapshotPrefix );
            }
            else
            {
                return new SplitRepositoryDynamicPrefixComposer(
                        localPrefix, remotePrefix, releasePrefix, snapshotPrefix );
            }
        }
        // TODO: make composer pluggable
        throw new IllegalArgumentException( "Unknown " + CONFIG_PROP_COMPOSER + " value=" + composer );
    }

    private static final class SplitDynamicPrefixComposer extends DynamicPrefixComposerSupport
    {
        private SplitDynamicPrefixComposer( String localPrefix,
                                           String remotePrefix,
                                           String releasePrefix,
                                           String snapshotPrefix )
        {
            super( localPrefix, remotePrefix, releasePrefix, snapshotPrefix );
        }

        @Override
        public boolean isRemoteSplitByOrigin()
        {
            return false;
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
                    + ( artifact.isSnapshot() ? snapshotPrefix : releasePrefix );
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
                    + ( isSnapshot( metadata ) ? snapshotPrefix : releasePrefix );
        }
    }

    private static final class SplitRepositoryDynamicPrefixComposer extends  DynamicPrefixComposerSupport
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

    private abstract static class DynamicPrefixComposerSupport implements DynamicPrefixComposer
    {
        protected final String localPrefix;

        protected final String remotePrefix;

        protected final String releasePrefix;

        protected final String snapshotPrefix;

        private DynamicPrefixComposerSupport( String localPrefix,
                                             String remotePrefix,
                                             String releasePrefix,
                                             String snapshotPrefix )
        {
            this.localPrefix = localPrefix;
            this.remotePrefix = remotePrefix;
            this.releasePrefix = releasePrefix;
            this.snapshotPrefix = snapshotPrefix;
        }
    }


    private static boolean isSnapshot( Metadata metadata )
    {
        return metadata.getVersion() != null && metadata.getVersion().endsWith( "SNAPSHOT" );
    }
}
