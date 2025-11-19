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

import java.util.function.BiFunction;
import java.util.function.Function;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.ConfigUtils;

/**
 * Support class for {@link LocalPathPrefixComposerFactory} implementations: it predefines and makes re-usable
 * common configuration getters, and defines a support class for {@link LocalPathPrefixComposer} carrying same
 * configuration and providing default implementation for all methods.
 * <p>
 * Implementors should extend this class to implement custom split strategies. If one needs to alter default
 * configuration, they should override any configuration getter from this class.
 *
 * @see DefaultLocalPathPrefixComposerFactory
 * @since 1.8.1
 */
public abstract class LocalPathPrefixComposerFactorySupport implements LocalPathPrefixComposerFactory {

    /**
     * Whether LRM should split local and remote artifacts.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationDefaultValue {@link #DEFAULT_SPLIT}
     */
    public static final String CONFIG_PROP_SPLIT = EnhancedLocalRepositoryManagerFactory.CONFIG_PROPS_PREFIX + "split";

    public static final boolean DEFAULT_SPLIT = false;

    /**
     * The prefix to use for locally installed artifacts.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     * @configurationDefaultValue {@link #DEFAULT_LOCAL_PREFIX}
     */
    public static final String CONFIG_PROP_LOCAL_PREFIX =
            EnhancedLocalRepositoryManagerFactory.CONFIG_PROPS_PREFIX + "localPrefix";

    public static final String DEFAULT_LOCAL_PREFIX = "installed";

    /**
     * Whether locally installed artifacts should be split by version (release/snapshot).
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationDefaultValue {@link #DEFAULT_SPLIT_LOCAL}
     */
    public static final String CONFIG_PROP_SPLIT_LOCAL =
            EnhancedLocalRepositoryManagerFactory.CONFIG_PROPS_PREFIX + "splitLocal";

    public static final boolean DEFAULT_SPLIT_LOCAL = false;

    /**
     * The prefix to use for remotely cached artifacts.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     * @configurationDefaultValue {@link #DEFAULT_REMOTE_PREFIX}
     */
    public static final String CONFIG_PROP_REMOTE_PREFIX =
            EnhancedLocalRepositoryManagerFactory.CONFIG_PROPS_PREFIX + "remotePrefix";

    public static final String DEFAULT_REMOTE_PREFIX = "cached";

    /**
     * Whether cached artifacts should be split by version (release/snapshot).
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationDefaultValue {@link #DEFAULT_SPLIT_REMOTE}
     */
    public static final String CONFIG_PROP_SPLIT_REMOTE =
            EnhancedLocalRepositoryManagerFactory.CONFIG_PROPS_PREFIX + "splitRemote";

    public static final boolean DEFAULT_SPLIT_REMOTE = false;

    /**
     * Whether cached artifacts should be split by origin repository (repository ID).
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationDefaultValue {@link #DEFAULT_SPLIT_REMOTE_REPOSITORY}
     */
    public static final String CONFIG_PROP_SPLIT_REMOTE_REPOSITORY =
            EnhancedLocalRepositoryManagerFactory.CONFIG_PROPS_PREFIX + "splitRemoteRepository";

    public static final boolean DEFAULT_SPLIT_REMOTE_REPOSITORY = false;

    /**
     * For cached artifacts, if both splitRemote and splitRemoteRepository are set to true sets the splitting order:
     * by default it is repositoryId/version (false) or version/repositoryId (true)
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationDefaultValue {@link #DEFAULT_SPLIT_REMOTE_REPOSITORY_LAST}
     */
    public static final String CONFIG_PROP_SPLIT_REMOTE_REPOSITORY_LAST =
            EnhancedLocalRepositoryManagerFactory.CONFIG_PROPS_PREFIX + "splitRemoteRepositoryLast";

    public static final boolean DEFAULT_SPLIT_REMOTE_REPOSITORY_LAST = false;

    /**
     * The prefix to use for release artifacts.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     * @configurationDefaultValue {@link #DEFAULT_RELEASES_PREFIX}
     */
    public static final String CONFIG_PROP_RELEASES_PREFIX =
            EnhancedLocalRepositoryManagerFactory.CONFIG_PROPS_PREFIX + "releasesPrefix";

    public static final String DEFAULT_RELEASES_PREFIX = "releases";

    /**
     * The prefix to use for snapshot artifacts.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     * @configurationDefaultValue {@link #DEFAULT_SNAPSHOTS_PREFIX}
     */
    public static final String CONFIG_PROP_SNAPSHOTS_PREFIX =
            EnhancedLocalRepositoryManagerFactory.CONFIG_PROPS_PREFIX + "snapshotsPrefix";

    public static final String DEFAULT_SNAPSHOTS_PREFIX = "snapshots";

    // Legacy support: properties were renamed in Resolver 2.0.x, but we should support 1.9.x properties as well
    // These below are Resolver 1.9.x properties, are undocumented and shall be removed with Resolver 2.1.x (or later).

    private static final String R1_CONF_PROP_SPLIT = "aether.enhancedLocalRepository.split";

    private static final String R1_CONF_PROP_LOCAL_PREFIX = "aether.enhancedLocalRepository.localPrefix";

    private static final String R1_CONF_PROP_SPLIT_LOCAL = "aether.enhancedLocalRepository.splitLocal";

    private static final String R1_CONF_PROP_REMOTE_PREFIX = "aether.enhancedLocalRepository.remotePrefix";

    private static final String R1_CONF_PROP_SPLIT_REMOTE = "aether.enhancedLocalRepository.splitRemote";

    private static final String R1_CONF_PROP_SPLIT_REMOTE_REPOSITORY =
            "aether.enhancedLocalRepository.splitRemoteRepository";

    private static final String R1_CONF_PROP_SPLIT_REMOTE_REPOSITORY_LAST =
            "aether.enhancedLocalRepository.splitRemoteRepositoryLast";

    private static final String R1_CONF_PROP_RELEASES_PREFIX = "aether.enhancedLocalRepository.releasesPrefix";

    private static final String R1_CONF_PROP_SNAPSHOTS_PREFIX = "aether.enhancedLocalRepository.snapshotsPrefix";

    protected boolean isSplit(RepositorySystemSession session) {
        return ConfigUtils.getBoolean(session, DEFAULT_SPLIT, CONFIG_PROP_SPLIT, R1_CONF_PROP_SPLIT);
    }

    protected String getLocalPrefix(RepositorySystemSession session) {
        return ConfigUtils.getString(
                session, DEFAULT_LOCAL_PREFIX, CONFIG_PROP_LOCAL_PREFIX, R1_CONF_PROP_LOCAL_PREFIX);
    }

    protected boolean isSplitLocal(RepositorySystemSession session) {
        return ConfigUtils.getBoolean(session, DEFAULT_SPLIT_LOCAL, CONFIG_PROP_SPLIT_LOCAL, R1_CONF_PROP_SPLIT_LOCAL);
    }

    protected String getRemotePrefix(RepositorySystemSession session) {
        return ConfigUtils.getString(
                session, DEFAULT_REMOTE_PREFIX, CONFIG_PROP_REMOTE_PREFIX, R1_CONF_PROP_REMOTE_PREFIX);
    }

    protected boolean isSplitRemote(RepositorySystemSession session) {
        return ConfigUtils.getBoolean(
                session, DEFAULT_SPLIT_REMOTE, CONFIG_PROP_SPLIT_REMOTE, R1_CONF_PROP_SPLIT_REMOTE);
    }

    protected boolean isSplitRemoteRepository(RepositorySystemSession session) {
        return ConfigUtils.getBoolean(
                session,
                DEFAULT_SPLIT_REMOTE_REPOSITORY,
                CONFIG_PROP_SPLIT_REMOTE_REPOSITORY,
                R1_CONF_PROP_SPLIT_REMOTE_REPOSITORY);
    }

    protected boolean isSplitRemoteRepositoryLast(RepositorySystemSession session) {
        return ConfigUtils.getBoolean(
                session,
                DEFAULT_SPLIT_REMOTE_REPOSITORY_LAST,
                CONFIG_PROP_SPLIT_REMOTE_REPOSITORY_LAST,
                R1_CONF_PROP_SPLIT_REMOTE_REPOSITORY_LAST);
    }

    protected String getReleasesPrefix(RepositorySystemSession session) {
        return ConfigUtils.getString(
                session, DEFAULT_RELEASES_PREFIX, CONFIG_PROP_RELEASES_PREFIX, R1_CONF_PROP_RELEASES_PREFIX);
    }

    protected String getSnapshotsPrefix(RepositorySystemSession session) {
        return ConfigUtils.getString(
                session, DEFAULT_SNAPSHOTS_PREFIX, CONFIG_PROP_SNAPSHOTS_PREFIX, R1_CONF_PROP_SNAPSHOTS_PREFIX);
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

        protected final BiFunction<RemoteRepository, String, String> repositoryKeyFunction;

        protected LocalPathPrefixComposerSupport(
                boolean split,
                String localPrefix,
                boolean splitLocal,
                String remotePrefix,
                boolean splitRemote,
                boolean splitRemoteRepository,
                boolean splitRemoteRepositoryLast,
                String releasesPrefix,
                String snapshotsPrefix,
                BiFunction<RemoteRepository, String, String> repositoryKeyFunction) {
            this.split = split;
            this.localPrefix = localPrefix;
            this.splitLocal = splitLocal;
            this.remotePrefix = remotePrefix;
            this.splitRemote = splitRemote;
            this.splitRemoteRepository = splitRemoteRepository;
            this.splitRemoteRepositoryLast = splitRemoteRepositoryLast;
            this.releasesPrefix = releasesPrefix;
            this.snapshotsPrefix = snapshotsPrefix;
            this.repositoryKeyFunction = repositoryKeyFunction;
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
                result += "/" + repositoryKeyFunction.apply(repository, null);
            }
            if (splitRemote) {
                result += "/" + (artifact.isSnapshot() ? snapshotsPrefix : releasesPrefix);
            }
            if (splitRemoteRepositoryLast && splitRemoteRepository) {
                result += "/" + repositoryKeyFunction.apply(repository, null);
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
                result += "/" + repositoryKeyFunction.apply(repository, null);
            }
            if (splitRemote) {
                result += "/" + (isSnapshot(metadata) ? snapshotsPrefix : releasesPrefix);
            }
            if (splitRemoteRepositoryLast && splitRemoteRepository) {
                result += "/" + repositoryKeyFunction.apply(repository, null);
            }
            return result;
        }

        protected boolean isSnapshot(Metadata metadata) {
            return !metadata.getVersion().isEmpty() && metadata.getVersion().endsWith("-SNAPSHOT");
        }
    }
}
