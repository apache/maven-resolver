/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.eclipse.aether.internal.impl;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.ConfigUtils;

/**
 * Support class for {@link LocalPathPrefixComposerFactory} implementations: it predefines and makes re-usable
 * common configuration getters, and defines a support class for {@link LocalPathPrefixComposer} carrying same
 * configuration and providing default implementation for all methods.
 *
 * Implementors should extend this class to implement custom split strategies. If one needs to alter default
 * configuration, they should override any configuration getter from this class.
 *
 * @see DefaultLocalPathPrefixComposerFactory
 * @since 1.8.1
 */
public abstract class LocalPathPrefixComposerFactorySupport implements LocalPathPrefixComposerFactory {
    protected static final String CONF_PROP_SPLIT = "aether.enhancedLocalRepository.split";

    protected static final boolean DEFAULT_SPLIT = false;

    protected static final String CONF_PROP_LOCAL_PREFIX = "aether.enhancedLocalRepository.localPrefix";

    protected static final String DEFAULT_LOCAL_PREFIX = "installed";

    protected static final String CONF_PROP_SPLIT_LOCAL = "aether.enhancedLocalRepository.splitLocal";

    protected static final boolean DEFAULT_SPLIT_LOCAL = false;

    protected static final String CONF_PROP_REMOTE_PREFIX = "aether.enhancedLocalRepository.remotePrefix";

    protected static final String DEFAULT_REMOTE_PREFIX = "cached";

    protected static final String CONF_PROP_SPLIT_REMOTE = "aether.enhancedLocalRepository.splitRemote";

    protected static final boolean DEFAULT_SPLIT_REMOTE = false;

    protected static final String CONF_PROP_SPLIT_REMOTE_REPOSITORY =
            "aether.enhancedLocalRepository.splitRemoteRepository";

    protected static final boolean DEFAULT_SPLIT_REMOTE_REPOSITORY = false;

    protected static final String CONF_PROP_SPLIT_REMOTE_REPOSITORY_LAST =
            "aether.enhancedLocalRepository.splitRemoteRepositoryLast";

    protected static final boolean DEFAULT_SPLIT_REMOTE_REPOSITORY_LAST = false;

    protected static final String CONF_PROP_RELEASES_PREFIX = "aether.enhancedLocalRepository.releasesPrefix";

    protected static final String DEFAULT_RELEASES_PREFIX = "releases";

    protected static final String CONF_PROP_SNAPSHOTS_PREFIX = "aether.enhancedLocalRepository.snapshotsPrefix";

    protected static final String DEFAULT_SNAPSHOTS_PREFIX = "snapshots";

    protected boolean isSplit(RepositorySystemSession session) {
        return ConfigUtils.getBoolean(session, DEFAULT_SPLIT, CONF_PROP_SPLIT);
    }

    protected String getLocalPrefix(RepositorySystemSession session) {
        return ConfigUtils.getString(session, DEFAULT_LOCAL_PREFIX, CONF_PROP_LOCAL_PREFIX);
    }

    protected boolean isSplitLocal(RepositorySystemSession session) {
        return ConfigUtils.getBoolean(session, DEFAULT_SPLIT_LOCAL, CONF_PROP_SPLIT_LOCAL);
    }

    protected String getRemotePrefix(RepositorySystemSession session) {
        return ConfigUtils.getString(session, DEFAULT_REMOTE_PREFIX, CONF_PROP_REMOTE_PREFIX);
    }

    protected boolean isSplitRemote(RepositorySystemSession session) {
        return ConfigUtils.getBoolean(session, DEFAULT_SPLIT_REMOTE, CONF_PROP_SPLIT_REMOTE);
    }

    protected boolean isSplitRemoteRepository(RepositorySystemSession session) {
        return ConfigUtils.getBoolean(session, DEFAULT_SPLIT_REMOTE_REPOSITORY, CONF_PROP_SPLIT_REMOTE_REPOSITORY);
    }

    protected boolean isSplitRemoteRepositoryLast(RepositorySystemSession session) {
        return ConfigUtils.getBoolean(
                session, DEFAULT_SPLIT_REMOTE_REPOSITORY_LAST, CONF_PROP_SPLIT_REMOTE_REPOSITORY_LAST);
    }

    protected String getReleasesPrefix(RepositorySystemSession session) {
        return ConfigUtils.getString(session, DEFAULT_RELEASES_PREFIX, CONF_PROP_RELEASES_PREFIX);
    }

    protected String getSnapshotsPrefix(RepositorySystemSession session) {
        return ConfigUtils.getString(session, DEFAULT_SNAPSHOTS_PREFIX, CONF_PROP_SNAPSHOTS_PREFIX);
    }

    /**
     * Support class for composers: it defines protected members for all the predefined configuration values and
     * provides default implementation for methods. Implementors may change it's behaviour by overriding methods.
     */
    @SuppressWarnings("checkstyle:parameternumber")
    protected abstract static class LocalPathPrefixComposerSupport implements LocalPathPrefixComposer {
        protected final boolean split;

        protected final String localPrefix;

        protected final boolean splitLocal;

        protected final String remotePrefix;

        protected final boolean splitRemote;

        protected final boolean splitRemoteRepository;

        protected final boolean splitRemoteRepositoryLast;

        protected final String releasesPrefix;

        protected final String snapshotsPrefix;

        protected LocalPathPrefixComposerSupport(
                boolean split,
                String localPrefix,
                boolean splitLocal,
                String remotePrefix,
                boolean splitRemote,
                boolean splitRemoteRepository,
                boolean splitRemoteRepositoryLast,
                String releasesPrefix,
                String snapshotsPrefix) {
            this.split = split;
            this.localPrefix = localPrefix;
            this.splitLocal = splitLocal;
            this.remotePrefix = remotePrefix;
            this.splitRemote = splitRemote;
            this.splitRemoteRepository = splitRemoteRepository;
            this.splitRemoteRepositoryLast = splitRemoteRepositoryLast;
            this.releasesPrefix = releasesPrefix;
            this.snapshotsPrefix = snapshotsPrefix;
        }

        @Override
        public String getPathPrefixForLocalArtifact(Artifact artifact) {
            if (!split) {
                return null;
            }
            String result = localPrefix;
            if (splitLocal) {
                result += "/" + (artifact.isSnapshot() ? snapshotsPrefix : releasesPrefix);
            }
            return result;
        }

        @Override
        public String getPathPrefixForRemoteArtifact(Artifact artifact, RemoteRepository repository) {
            if (!split) {
                return null;
            }
            String result = remotePrefix;
            if (!splitRemoteRepositoryLast && splitRemoteRepository) {
                result += "/" + repository.getId();
            }
            if (splitRemote) {
                result += "/" + (artifact.isSnapshot() ? snapshotsPrefix : releasesPrefix);
            }
            if (splitRemoteRepositoryLast && splitRemoteRepository) {
                result += "/" + repository.getId();
            }
            return result;
        }

        @Override
        public String getPathPrefixForLocalMetadata(Metadata metadata) {
            if (!split) {
                return null;
            }
            String result = localPrefix;
            if (splitLocal) {
                result += "/" + (isSnapshot(metadata) ? snapshotsPrefix : releasesPrefix);
            }
            return result;
        }

        @Override
        public String getPathPrefixForRemoteMetadata(Metadata metadata, RemoteRepository repository) {
            if (!split) {
                return null;
            }
            String result = remotePrefix;
            if (!splitRemoteRepositoryLast && splitRemoteRepository) {
                result += "/" + repository.getId();
            }
            if (splitRemote) {
                result += "/" + (isSnapshot(metadata) ? snapshotsPrefix : releasesPrefix);
            }
            if (splitRemoteRepositoryLast && splitRemoteRepository) {
                result += "/" + repository.getId();
            }
            return result;
        }

        protected boolean isSnapshot(Metadata metadata) {
            return !metadata.getVersion().isEmpty() && metadata.getVersion().endsWith("-SNAPSHOT");
        }
    }
}
