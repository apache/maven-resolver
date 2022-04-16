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
 * Support class for {@link LocalPathPrefixComposerFactory} implementations.
 *
 * @since TBD
 */
public abstract class LocalPathPrefixComposerFactorySupport implements LocalPathPrefixComposerFactory
{
    protected String getLocalPrefix( RepositorySystemSession session )
    {
        return ConfigUtils.getString(
                session, "installed", "aether.enhancedLocalRepository.localPrefix" );
    }

    protected boolean isSplitLocal( RepositorySystemSession session )
    {
        return ConfigUtils.getBoolean(
                session, false, "aether.enhancedLocalRepository.splitLocal" );
    }

    protected String getRemotePrefix( RepositorySystemSession session )
    {
        return ConfigUtils.getString(
                session, "cached", "aether.enhancedLocalRepository.remotePrefix" );
    }

    protected boolean isSplitRemote( RepositorySystemSession session )
    {
        return ConfigUtils.getBoolean(
                session, false, "aether.enhancedLocalRepository.splitRemote" );
    }

    protected String getReleasePrefix( RepositorySystemSession session )
    {
        return ConfigUtils.getString(
                session, "release", "aether.enhancedLocalRepository.releasePrefix" );
    }

    protected String getSnapshotPrefix( RepositorySystemSession session )
    {
        return ConfigUtils.getString(
                session, "snapshot", "aether.enhancedLocalRepository.snapshotPrefix" );
    }

    /**
     * Support class for composers.
     */
    protected abstract static class LocalPathPrefixComposerSupport implements LocalPathPrefixComposer
    {
        protected final String localPrefix;

        protected final boolean splitLocal;

        protected final String remotePrefix;

        protected final boolean splitRemote;

        protected final String releasePrefix;

        protected final String snapshotPrefix;

        protected LocalPathPrefixComposerSupport( String localPrefix,
                                                  boolean splitLocal,
                                                  String remotePrefix,
                                                  boolean splitRemote,
                                                  String releasePrefix,
                                                  String snapshotPrefix )
        {
            this.localPrefix = localPrefix;
            this.splitLocal = splitLocal;
            this.remotePrefix = remotePrefix;
            this.splitRemote = splitRemote;
            this.releasePrefix = releasePrefix;
            this.snapshotPrefix = snapshotPrefix;
        }
    }

    protected static boolean isSnapshot( Metadata metadata )
    {
        // TODO: this is bad: MD not have always versions
        // return metadata.getVersion() != null && metadata.getVersion().endsWith( "SNAPSHOT" );
        // TODO: unsure what Nature really is
        return metadata.getNature() == Metadata.Nature.SNAPSHOT;
    }
}
