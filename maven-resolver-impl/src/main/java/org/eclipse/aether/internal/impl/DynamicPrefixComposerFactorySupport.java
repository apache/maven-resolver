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
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.util.ConfigUtils;

/**
 * Support class for {@link DynamicPrefixComposerFactory} implementations.
 *
 * @since TBD
 */
public abstract class DynamicPrefixComposerFactorySupport implements DynamicPrefixComposerFactory
{
    @Override
    public DynamicPrefixComposer createComposer( RepositorySystemSession session )
    {
        String localPrefix = ConfigUtils.getString(
                session, "local", "aether.enhancedLocalRepository.localPrefix" );
        String remotePrefix = ConfigUtils.getString(
                session, "remote", "aether.enhancedLocalRepository.remotePrefix" );
        String releasePrefix = ConfigUtils.getString(
                session, "release", "aether.enhancedLocalRepository.releasePrefix" );
        String snapshotPrefix = ConfigUtils.getString(
                session, "snapshot", "aether.enhancedLocalRepository.snapshotPrefix" );

        return dpCreateComposer( session, localPrefix, remotePrefix, releasePrefix, snapshotPrefix );
    }

    protected abstract DynamicPrefixComposer dpCreateComposer( RepositorySystemSession session, String localPrefix,
                                                               String remotePrefix, String releasePrefix,
                                                               String snapshotPrefix );

    /**
     * Support class for composers.
     */
    protected abstract static class DynamicPrefixComposerSupport implements DynamicPrefixComposer
    {
        protected final String localPrefix;

        protected final String remotePrefix;

        protected final String releasePrefix;

        protected final String snapshotPrefix;

        protected DynamicPrefixComposerSupport( String localPrefix,
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

    protected static boolean isSnapshot( Metadata metadata )
    {
        return metadata.getVersion() != null && metadata.getVersion().endsWith( "SNAPSHOT" );
    }
}
