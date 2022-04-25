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
 * Support class for {@link LocalPathPrefixComposerFactory} implementations: it predefines and makes re-usable
 * common configuration getters, and defines a support class for {@link LocalPathPrefixComposer} carrying same.
 *
 * @since TBD
 */
public abstract class LocalPathPrefixComposerFactorySupport implements LocalPathPrefixComposerFactory
{
    private static final String CONF_PROP_SPLIT = "aether.enhancedLocalRepository.split";

    private static final boolean DEFAULT_SPLIT = false;
    private static final String CONF_PROP_LOCAL_PREFIX = "aether.enhancedLocalRepository.localPrefix";

    private static final String DEFAULT_LOCAL_PREFIX = "installed";

    private static final String CONF_PROP_SPLIT_LOCAL = "aether.enhancedLocalRepository.splitLocal";

    private static final boolean DEFAULT_SPLIT_LOCAL = false;

    private static final String CONF_PROP_REMOTE_PREFIX = "aether.enhancedLocalRepository.remotePrefix";

    private static final String DEFAULT_REMOTE_PREFIX = "cached";

    private static final String CONF_PROP_SPLIT_REMOTE = "aether.enhancedLocalRepository.splitRemote";

    private static final boolean DEFAULT_SPLIT_REMOTE = false;

    private static final String CONF_PROP_SPLIT_REMOTE_REPOSITORY =
            "aether.enhancedLocalRepository.splitRemoteRepository";

    private static final boolean DEFAULT_SPLIT_REMOTE_REPOSITORY = false;

    private static final String CONF_PROP_SPLIT_REMOTE_REPOSITORY_LAST =
            "aether.enhancedLocalRepository.splitRemoteRepositoryLast";

    private static final boolean DEFAULT_SPLIT_REMOTE_REPOSITORY_LAST = false;

    private static final String CONF_PROP_RELEASE_PREFIX = "aether.enhancedLocalRepository.releasePrefix";

    private static final String DEFAULT_RELEASE_PREFIX = "release";

    private static final String CONF_PROP_SNAPSHOT_PREFIX = "aether.enhancedLocalRepository.snapshotPrefix";

    private static final String DEFAULT_SNAPSHOT_PREFIX = "snapshot";

    protected boolean isSplit( RepositorySystemSession session )
    {
        return ConfigUtils.getBoolean(
                session, DEFAULT_SPLIT, CONF_PROP_SPLIT );
    }

    protected String getLocalPrefix( RepositorySystemSession session )
    {
        return ConfigUtils.getString(
                session, DEFAULT_LOCAL_PREFIX, CONF_PROP_LOCAL_PREFIX );
    }

    protected boolean isSplitLocal( RepositorySystemSession session )
    {
        return ConfigUtils.getBoolean(
                session, DEFAULT_SPLIT_LOCAL, CONF_PROP_SPLIT_LOCAL );
    }

    protected String getRemotePrefix( RepositorySystemSession session )
    {
        return ConfigUtils.getString(
                session, DEFAULT_REMOTE_PREFIX, CONF_PROP_REMOTE_PREFIX );
    }

    protected boolean isSplitRemote( RepositorySystemSession session )
    {
        return ConfigUtils.getBoolean(
                session, DEFAULT_SPLIT_REMOTE, CONF_PROP_SPLIT_REMOTE );
    }

    protected boolean isSplitRemoteRepository( RepositorySystemSession session )
    {
        return ConfigUtils.getBoolean(
                session, DEFAULT_SPLIT_REMOTE_REPOSITORY, CONF_PROP_SPLIT_REMOTE_REPOSITORY );
    }

    protected boolean isSplitRemoteRepositoryLast( RepositorySystemSession session )
    {
        return ConfigUtils.getBoolean(
                session, DEFAULT_SPLIT_REMOTE_REPOSITORY_LAST, CONF_PROP_SPLIT_REMOTE_REPOSITORY_LAST );
    }

    protected String getReleasePrefix( RepositorySystemSession session )
    {
        return ConfigUtils.getString(
                session, DEFAULT_RELEASE_PREFIX, CONF_PROP_RELEASE_PREFIX );
    }

    protected String getSnapshotPrefix( RepositorySystemSession session )
    {
        return ConfigUtils.getString(
                session, DEFAULT_SNAPSHOT_PREFIX, CONF_PROP_SNAPSHOT_PREFIX );
    }

    /**
     * Support class for composers: it defines protected members for all the predefined configuration values.
     */
    @SuppressWarnings( "checkstyle:parameternumber" )
    protected abstract static class LocalPathPrefixComposerSupport implements LocalPathPrefixComposer
    {
        protected final boolean split;

        protected final String localPrefix;

        protected final boolean splitLocal;

        protected final String remotePrefix;

        protected final boolean splitRemote;

        protected final boolean splitRemoteRepository;

        protected final boolean splitRemoteRepositoryLast;

        protected final String releasePrefix;

        protected final String snapshotPrefix;

        protected LocalPathPrefixComposerSupport( boolean split,
                                                  String localPrefix,
                                                  boolean splitLocal,
                                                  String remotePrefix,
                                                  boolean splitRemote,
                                                  boolean splitRemoteRepository,
                                                  boolean splitRemoteRepositoryLast,
                                                  String releasePrefix,
                                                  String snapshotPrefix )
        {
            this.split = split;
            this.localPrefix = localPrefix;
            this.splitLocal = splitLocal;
            this.remotePrefix = remotePrefix;
            this.splitRemote = splitRemote;
            this.splitRemoteRepository = splitRemoteRepository;
            this.splitRemoteRepositoryLast = splitRemoteRepositoryLast;
            this.releasePrefix = releasePrefix;
            this.snapshotPrefix = snapshotPrefix;
        }

        protected boolean isSnapshot( Metadata metadata )
        {
            return !metadata.getVersion().isEmpty()
                    && metadata.getVersion().endsWith( "-SNAPSHOT" );
        }
    }
}
