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
package org.eclipse.aether.internal.impl.filter;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.impl.MetadataResolver;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.internal.impl.filter.prefixes.PrefixesSource;
import org.eclipse.aether.internal.impl.filter.ruletree.PrefixTree;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.MetadataRequest;
import org.eclipse.aether.resolution.MetadataResult;
import org.eclipse.aether.spi.connector.filter.RemoteRepositoryFilter;
import org.eclipse.aether.spi.connector.layout.RepositoryLayout;
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutProvider;
import org.eclipse.aether.transfer.NoRepositoryLayoutException;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.repository.RepositoryIdHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

/**
 * Remote repository filter source filtering on path prefixes. It is backed by a file that lists all allowed path
 * prefixes from remote repository. Artifact that layout converted path (using remote repository layout) results in
 * path with no corresponding prefix present in this file is filtered out.
 * <p>
 * The file can be authored manually: format is one prefix per line, comments starting with "#" (hash) and empty lines
 * for structuring are supported, The "/" (slash) character is used as file separator. Some remote repositories and
 * MRMs publish these kind of files, they can be downloaded from corresponding URLs.
 * <p>
 * The prefix file is expected on path "${basedir}/prefixes-${repository.id}.txt".
 * <p>
 * The prefixes file is once loaded and cached, so in-flight prefixes file change during component existence are not
 * noticed.
 * <p>
 * Examples of published prefix files:
 * <ul>
 *     <li>Central: <a href="https://repo.maven.apache.org/maven2/.meta/prefixes.txt">prefixes.txt</a></li>
 *     <li>Apache Releases:
 *     <a href="https://repository.apache.org/content/repositories/releases/.meta/prefixes.txt">prefixes.txt</a></li>
 * </ul>
 *
 * @since 1.9.0
 */
@Singleton
@Named(PrefixesRemoteRepositoryFilterSource.NAME)
public final class PrefixesRemoteRepositoryFilterSource extends RemoteRepositoryFilterSourceSupport {
    public static final String NAME = "prefixes";

    private static final String CONFIG_PROPS_PREFIX =
            RemoteRepositoryFilterSourceSupport.CONFIG_PROPS_PREFIX + NAME + ".";

    private static final String PREFIX_FILE_PATH = ".meta/prefixes.txt";

    /**
     * Configuration to enable the Prefixes filter (enabled by default). Can be fine-tuned per repository using
     * repository ID suffixes.
     * <strong>Important:</strong> For this filter to take effect, configuration files must be available. Without
     * configuration files, the enabled filter remains dormant and does not interfere with resolution.
     * <strong>Configuration File Resolution:</strong>
     * <ol>
     * <li><strong>User-provided files:</strong> Checked first from directory specified by {@link #CONFIG_PROP_BASEDIR}
     *     (defaults to {@code $LOCAL_REPO/.remoteRepositoryFilters})</li>
     * <li><strong>Auto-discovery:</strong> If not found, attempts to download from remote repository and cache locally</li>
     * </ol>
     * <strong>File Naming:</strong> {@code prefixes-$(repository.id).txt}
     * <strong>Recommended Setup (Auto-Discovery with Override Capability):</strong>
     * Start with auto-discovery, but prepare for project-specific overrides. Add to {@code .mvn/maven.config}:
     * <pre>
     * -Daether.remoteRepositoryFilter.prefixes=true
     * -Daether.remoteRepositoryFilter.prefixes.basedir=${session.rootDirectory}/.mvn/rrf/
     * </pre>
     * <strong>Initial setup:</strong> Don't provide any files - rely on auto-discovery as repositories are accessed.
     * <strong>Override when needed:</strong> Create {@code prefixes-myrepoId.txt} files in {@code .mvn/rrf/} and
     * commit to version control.
     * <strong>Caching:</strong> Auto-discovered prefix files are cached in the local repository.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationRepoIdSuffix Yes
     * @configurationDefaultValue {@link #DEFAULT_ENABLED}
     */
    public static final String CONFIG_PROP_ENABLED = RemoteRepositoryFilterSourceSupport.CONFIG_PROPS_PREFIX + NAME;

    public static final boolean DEFAULT_ENABLED = true;

    /**
     * Configuration to skip the Prefixes filter for given request. This configuration is evaluated and if {@code true}
     * the prefixes remote filter will not kick in. Main use case is by filter itself, to prevent recursion during
     * discovery of remote prefixes file, but this also allows other components to control prefix filter discovery, while
     * leaving configuration like {@link #CONFIG_PROP_ENABLED} still show the "real state".
     *
     * @since 2.0.14
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationRepoIdSuffix Yes
     * @configurationDefaultValue {@link #DEFAULT_SKIPPED}
     */
    public static final String CONFIG_PROP_SKIPPED =
            RemoteRepositoryFilterSourceSupport.CONFIG_PROPS_PREFIX + NAME + ".skipped";

    public static final boolean DEFAULT_SKIPPED = false;

    /**
     * The basedir where to store filter files. If path is relative, it is resolved from local repository root.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     * @configurationDefaultValue {@link #LOCAL_REPO_PREFIX_DIR}
     */
    public static final String CONFIG_PROP_BASEDIR = CONFIG_PROPS_PREFIX + "basedir";

    public static final String LOCAL_REPO_PREFIX_DIR = ".remoteRepositoryFilters";

    static final String PREFIXES_FILE_PREFIX = "prefixes-";

    static final String PREFIXES_FILE_SUFFIX = ".txt";

    private final Logger logger = LoggerFactory.getLogger(PrefixesRemoteRepositoryFilterSource.class);

    private final Supplier<MetadataResolver> metadataResolver;

    private final Supplier<RemoteRepositoryManager> remoteRepositoryManager;

    private final RepositoryLayoutProvider repositoryLayoutProvider;

    private final ConcurrentHashMap<RemoteRepository, PrefixTree> prefixes;

    private final ConcurrentHashMap<RemoteRepository, RepositoryLayout> layouts;

    @Inject
    public PrefixesRemoteRepositoryFilterSource(
            Supplier<MetadataResolver> metadataResolver,
            Supplier<RemoteRepositoryManager> remoteRepositoryManager,
            RepositoryLayoutProvider repositoryLayoutProvider) {
        this.metadataResolver = requireNonNull(metadataResolver);
        this.remoteRepositoryManager = requireNonNull(remoteRepositoryManager);
        this.repositoryLayoutProvider = requireNonNull(repositoryLayoutProvider);
        this.prefixes = new ConcurrentHashMap<>();
        this.layouts = new ConcurrentHashMap<>();
    }

    @Override
    protected boolean isEnabled(RepositorySystemSession session) {
        return ConfigUtils.getBoolean(session, DEFAULT_ENABLED, CONFIG_PROP_ENABLED)
                && !ConfigUtils.getBoolean(session, DEFAULT_SKIPPED, CONFIG_PROP_SKIPPED);
    }

    private boolean isRepositoryFilteringEnabled(RepositorySystemSession session, RemoteRepository remoteRepository) {
        if (isEnabled(session)) {
            return ConfigUtils.getBoolean(
                            session,
                            DEFAULT_ENABLED,
                            CONFIG_PROP_ENABLED + "." + remoteRepository.getId(),
                            CONFIG_PROP_ENABLED + ".*")
                    && !ConfigUtils.getBoolean(
                            session,
                            DEFAULT_SKIPPED,
                            CONFIG_PROP_SKIPPED + "." + remoteRepository.getId(),
                            CONFIG_PROP_SKIPPED + ".*");
        }
        return false;
    }

    @Override
    public RemoteRepositoryFilter getRemoteRepositoryFilter(RepositorySystemSession session) {
        if (isEnabled(session)) {
            return new PrefixesFilter(session, getBasedir(session, LOCAL_REPO_PREFIX_DIR, CONFIG_PROP_BASEDIR, false));
        }
        return null;
    }

    /**
     * Caches layout instances for remote repository. In case of unknown layout it returns {@code null}.
     *
     * @return the layout instance of {@code null} if layout not supported.
     */
    private RepositoryLayout cacheLayout(RepositorySystemSession session, RemoteRepository remoteRepository) {
        return layouts.computeIfAbsent(remoteRepository, r -> {
            try {
                return repositoryLayoutProvider.newRepositoryLayout(session, remoteRepository);
            } catch (NoRepositoryLayoutException e) {
                return null;
            }
        });
    }

    private PrefixTree cachePrefixTree(
            RepositorySystemSession session, Path basedir, RemoteRepository remoteRepository) {
        return prefixes.computeIfAbsent(remoteRepository, r -> loadPrefixTree(session, basedir, remoteRepository));
    }

    private PrefixTree loadPrefixTree(
            RepositorySystemSession session, Path baseDir, RemoteRepository remoteRepository) {
        if (isRepositoryFilteringEnabled(session, remoteRepository)) {
            String origin = "user-provided";
            Path filePath = resolvePrefixesFromLocalConfiguration(session, baseDir, remoteRepository);
            if (filePath == null) {
                origin = "auto-discovered";
                filePath = resolvePrefixesFromRemoteRepository(session, remoteRepository);
            }
            if (filePath != null) {
                PrefixesSource prefixesSource = PrefixesSource.of(remoteRepository, filePath);
                if (prefixesSource.valid()) {
                    logger.debug(
                            "Loaded prefixes for remote repository {} from {} file '{}'",
                            prefixesSource.origin().getId(),
                            origin,
                            prefixesSource.path());
                    PrefixTree prefixTree = new PrefixTree("");
                    int rules = prefixTree.loadNodes(prefixesSource.entries().stream());
                    logger.info(
                            "Loaded {} {} prefixes for remote repository {} ({})",
                            rules,
                            origin,
                            prefixesSource.origin().getId(),
                            prefixesSource.path().getFileName());
                    return prefixTree;
                } else {
                    logger.info(
                            "Rejected {} prefixes for remote repository {} ({}): {}",
                            origin,
                            prefixesSource.origin().getId(),
                            prefixesSource.path().getFileName(),
                            prefixesSource.message());
                }
            }
            logger.debug("Prefix file for remote repository {} not available", remoteRepository);
            return PrefixTree.SENTINEL;
        }
        logger.debug("Prefix file for remote repository {} disabled", remoteRepository);
        return PrefixTree.SENTINEL;
    }

    private Path resolvePrefixesFromLocalConfiguration(
            RepositorySystemSession session, Path baseDir, RemoteRepository remoteRepository) {
        Path filePath = baseDir.resolve(PREFIXES_FILE_PREFIX
                + RepositoryIdHelper.cachedIdToPathSegment(session).apply(remoteRepository)
                + PREFIXES_FILE_SUFFIX);
        if (Files.isReadable(filePath)) {
            return filePath;
        } else {
            return null;
        }
    }

    private Path resolvePrefixesFromRemoteRepository(
            RepositorySystemSession session, RemoteRepository remoteRepository) {
        MetadataResolver mr = metadataResolver.get();
        RemoteRepositoryManager rm = remoteRepositoryManager.get();
        if (mr != null && rm != null) {
            // create "prepared" (auth, proxy and mirror equipped repo)
            RemoteRepository prepared = rm.aggregateRepositories(
                            session, Collections.emptyList(), Collections.singletonList(remoteRepository), true)
                    .get(0);
            // supplier for path
            MetadataRequest request =
                    new MetadataRequest(new DefaultMetadata(PREFIX_FILE_PATH, Metadata.Nature.RELEASE_OR_SNAPSHOT));
            // use unique repository; this will result in prefix (repository metadata) cached under unique id
            request.setRepository(prepared);
            request.setDeleteLocalCopyIfMissing(true);
            request.setFavorLocalRepository(true);
            MetadataResult result = mr.resolveMetadata(
                            new DefaultRepositorySystemSession(session)
                                    .setTransferListener(null)
                                    .setConfigProperty(CONFIG_PROP_SKIPPED, Boolean.TRUE.toString()),
                            Collections.singleton(request))
                    .get(0);
            if (result.isResolved()) {
                return result.getMetadata().getPath();
            } else {
                return null;
            }
        }
        return null;
    }

    private class PrefixesFilter implements RemoteRepositoryFilter {
        private final RepositorySystemSession session;
        private final Path basedir;

        private PrefixesFilter(RepositorySystemSession session, Path basedir) {
            this.session = session;
            this.basedir = basedir;
        }

        @Override
        public Result acceptArtifact(RemoteRepository remoteRepository, Artifact artifact) {
            RepositoryLayout repositoryLayout = cacheLayout(session, remoteRepository);
            if (repositoryLayout == null) {
                return new SimpleResult(true, "Unsupported layout: " + remoteRepository);
            }
            return acceptPrefix(
                    remoteRepository,
                    repositoryLayout.getLocation(artifact, false).getPath());
        }

        @Override
        public Result acceptMetadata(RemoteRepository remoteRepository, Metadata metadata) {
            RepositoryLayout repositoryLayout = cacheLayout(session, remoteRepository);
            if (repositoryLayout == null) {
                return new SimpleResult(true, "Unsupported layout: " + remoteRepository);
            }
            return acceptPrefix(
                    remoteRepository,
                    repositoryLayout.getLocation(metadata, false).getPath());
        }

        private Result acceptPrefix(RemoteRepository remoteRepository, String path) {
            PrefixTree prefixTree = cachePrefixTree(session, basedir, remoteRepository);
            if (PrefixTree.SENTINEL == prefixTree) {
                return NOT_PRESENT_RESULT;
            }
            if (prefixTree.acceptedPath(path)) {
                return new SimpleResult(true, "Path " + path + " allowed from " + remoteRepository);
            } else {
                return new SimpleResult(false, "Prefix " + path + " NOT allowed from " + remoteRepository);
            }
        }
    }

    private static final RemoteRepositoryFilter.Result NOT_PRESENT_RESULT =
            new SimpleResult(true, "Prefix file not present");

    private static final RemoteRepositoryFilter.Result SKIPPED_RESULT = new SimpleResult(true, "Prefix filter skipped");
}
