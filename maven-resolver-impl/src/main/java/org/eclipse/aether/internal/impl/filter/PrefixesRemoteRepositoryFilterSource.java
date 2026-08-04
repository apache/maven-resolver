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

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.Keys;
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
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactory;
import org.eclipse.aether.spi.connector.filter.RemoteRepositoryFilter;
import org.eclipse.aether.spi.connector.layout.RepositoryLayout;
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutProvider;
import org.eclipse.aether.spi.connector.transport.PeekTask;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.connector.transport.TransporterProvider;
import org.eclipse.aether.spi.remoterepo.RepositoryKeyFunctionFactory;
import org.eclipse.aether.transfer.NoRepositoryLayoutException;
import org.eclipse.aether.util.ConfigUtils;
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

    static final String PREFIX_FILE_TYPE = ".meta/prefixes.txt";

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
     * Determines what happens when the filter is enabled, but has no prefixes available for given remote repository
     * to work with. When set to {@code true} (default), the filter allows all requests to proceed for given remote
     * repository when no prefixes are available. When set to {@code false}, the filter blocks all requests toward
     * given remote repository when no prefixes are available. This setting allows repoId suffix, hence, can
     * determine "global" or "repository targeted" behaviors.
     *
     * @since 2.0.14
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationRepoIdSuffix Yes
     * @configurationDefaultValue {@link #DEFAULT_NO_INPUT_OUTCOME}
     */
    public static final String CONFIG_PROP_NO_INPUT_OUTCOME =
            RemoteRepositoryFilterSourceSupport.CONFIG_PROPS_PREFIX + NAME + ".noInputOutcome";

    public static final boolean DEFAULT_NO_INPUT_OUTCOME = true;

    /**
     * Configuration to allow Prefixes file resolution attempt from remote repository as "auto discovery". If this
     * configuration set to {@code false} only user-provided prefixes will be used.
     *
     * @since 2.0.14
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationRepoIdSuffix Yes
     * @configurationDefaultValue {@link #DEFAULT_RESOLVE_PREFIX_FILES}
     */
    public static final String CONFIG_PROP_RESOLVE_PREFIX_FILES =
            RemoteRepositoryFilterSourceSupport.CONFIG_PROPS_PREFIX + NAME + ".resolvePrefixFiles";

    public static final boolean DEFAULT_RESOLVE_PREFIX_FILES = true;

    /**
     * Configuration to allow Prefixes filter to auto-discover prefixes from mirrored repositories as well. For this to
     * work <em>Maven should be aware</em> that given remote repository is mirror and is usually backed by MRM. Given
     * multiple MRM implementations messes up prefixes file, is better to just skip these. In other case, one may use
     * {@link #CONFIG_PROP_ENABLED} with repository ID suffix.
     *
     * @since 2.0.14
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationRepoIdSuffix Yes
     * @configurationDefaultValue {@link #DEFAULT_USE_MIRRORED_REPOSITORIES}
     */
    public static final String CONFIG_PROP_USE_MIRRORED_REPOSITORIES =
            RemoteRepositoryFilterSourceSupport.CONFIG_PROPS_PREFIX + NAME + ".useMirroredRepositories";

    public static final boolean DEFAULT_USE_MIRRORED_REPOSITORIES = false;

    /**
     * Configuration to allow Prefixes filter to auto-discover prefixes from repository managers as well. For this to
     * work <em>Maven should be aware</em> that given remote repository is backed by repository manager.
     * Given multiple MRM implementations messes up prefixes file, is better to just skip these. In other case, one may use
     * {@link #CONFIG_PROP_ENABLED} with repository ID suffix.
     * <em>Note: as of today, nothing sets this on remote repositories, but is added for future.</em>
     *
     * @since 2.0.14
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationRepoIdSuffix Yes
     * @configurationDefaultValue {@link #DEFAULT_USE_REPOSITORY_MANAGERS}
     */
    public static final String CONFIG_PROP_USE_REPOSITORY_MANAGERS =
            RemoteRepositoryFilterSourceSupport.CONFIG_PROPS_PREFIX + NAME + ".useRepositoryManagers";

    public static final boolean DEFAULT_USE_REPOSITORY_MANAGERS = false;

    /**
     * Configuration to verify the first denied path per remote repository when the effective prefixes were
     * auto-discovered: the denied path existence is checked directly against the remote repository, and if the
     * path exists, the auto-discovered prefixes file is provably wrong (it denies content the repository actually
     * serves) and is dropped for the rest of the session with a warning (the filter then behaves as if no input
     * was available, see {@link #CONFIG_PROP_NO_INPUT_OUTCOME}). If the path does not exist, the prefixes file is
     * consistent with reality for this witness and stays trusted; no further verification happens for given remote
     * repository, keeping the extra cost bounded to at most one existence check per remote repository per session.
     * <p>
     * This protects builds from broken repository managers that "leak" a member repository prefixes file through
     * a group/virtual repository, silently disabling the whole repository. User-provided prefix files are
     * authoritative and are never verified. Verification is skipped in offline mode.
     *
     * @since 2.0.21
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationRepoIdSuffix Yes
     * @configurationDefaultValue {@link #DEFAULT_VERIFY_DENIED}
     */
    public static final String CONFIG_PROP_VERIFY_DENIED =
            RemoteRepositoryFilterSourceSupport.CONFIG_PROPS_PREFIX + NAME + ".verifyDenied";

    public static final boolean DEFAULT_VERIFY_DENIED = true;

    /**
     * The basedir where to store filter files. If path is relative, it is resolved from local repository root.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     * @configurationDefaultValue {@link #LOCAL_REPO_PREFIX_DIR}
     */
    public static final String CONFIG_PROP_BASEDIR =
            RemoteRepositoryFilterSourceSupport.CONFIG_PROPS_PREFIX + NAME + ".basedir";

    public static final String LOCAL_REPO_PREFIX_DIR = ".remoteRepositoryFilters";

    static final String PREFIXES_FILE_PREFIX = "prefixes-";

    static final String PREFIXES_FILE_SUFFIX = ".txt";

    private final Logger logger = LoggerFactory.getLogger(PrefixesRemoteRepositoryFilterSource.class);

    private final Supplier<MetadataResolver> metadataResolver;

    private final Supplier<RemoteRepositoryManager> remoteRepositoryManager;

    private final RepositoryLayoutProvider repositoryLayoutProvider;

    private final TransporterProvider transporterProvider;

    @Inject
    public PrefixesRemoteRepositoryFilterSource(
            RepositoryKeyFunctionFactory repositoryKeyFunctionFactory,
            Supplier<MetadataResolver> metadataResolver,
            Supplier<RemoteRepositoryManager> remoteRepositoryManager,
            RepositoryLayoutProvider repositoryLayoutProvider,
            TransporterProvider transporterProvider) {
        super(repositoryKeyFunctionFactory);
        this.metadataResolver = requireNonNull(metadataResolver);
        this.remoteRepositoryManager = requireNonNull(remoteRepositoryManager);
        this.repositoryLayoutProvider = requireNonNull(repositoryLayoutProvider);
        this.transporterProvider = requireNonNull(transporterProvider);
    }

    private static final Object PREFIXES_KEY = Keys.of(PrefixesRemoteRepositoryFilterSource.class, "prefixes");

    @SuppressWarnings("unchecked")
    private ConcurrentMap<RemoteRepository, CachedPrefixes> prefixes(RepositorySystemSession session) {
        return (ConcurrentMap<RemoteRepository, CachedPrefixes>)
                session.getData().computeIfAbsent(PREFIXES_KEY, ConcurrentHashMap::new);
    }

    private static final Object LAYOUTS_KEY = Keys.of(PrefixesRemoteRepositoryFilterSource.class, "layouts");

    @SuppressWarnings("unchecked")
    private ConcurrentMap<RemoteRepository, RepositoryLayout> layouts(RepositorySystemSession session) {
        return (ConcurrentMap<RemoteRepository, RepositoryLayout>)
                session.getData().computeIfAbsent(LAYOUTS_KEY, ConcurrentHashMap::new);
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
     * Caches layout instances for remote repository. In case of unknown layout it returns {@link #NOT_SUPPORTED}.
     *
     * @return the layout instance or {@link #NOT_SUPPORTED} if layout not supported.
     */
    private RepositoryLayout cacheLayout(RepositorySystemSession session, RemoteRepository remoteRepository) {
        return layouts(session).computeIfAbsent(normalizeRemoteRepository(session, remoteRepository), r -> {
            try {
                return repositoryLayoutProvider.newRepositoryLayout(session, remoteRepository);
            } catch (NoRepositoryLayoutException e) {
                return NOT_SUPPORTED;
            }
        });
    }

    private CachedPrefixes cachePrefixes(
            RepositorySystemSession session, Path basedir, RemoteRepository remoteRepository) {
        return prefixes(session)
                .computeIfAbsent(
                        normalizeRemoteRepository(session, remoteRepository),
                        r -> loadPrefixes(session, basedir, remoteRepository));
    }

    private static final PrefixTree DISABLED = new PrefixTree("disabled");
    private static final PrefixTree ENABLED_NO_INPUT = new PrefixTree("enabled-no-input");
    private static final PrefixTree BROKEN = new PrefixTree("broken");

    /**
     * The cached per remote repository prefixes state: the effective {@link PrefixTree}, whether it was
     * auto-discovered (as only auto-discovered prefixes are subject to denied path verification, see
     * {@link #CONFIG_PROP_VERIFY_DENIED}) and whether verification happened already.
     */
    private static final class CachedPrefixes {
        private static final CachedPrefixes DISABLED_PREFIXES = new CachedPrefixes(DISABLED, false);
        private static final CachedPrefixes NO_INPUT_PREFIXES = new CachedPrefixes(ENABLED_NO_INPUT, false);

        private volatile PrefixTree prefixTree;
        private final boolean autoDiscovered;
        private final AtomicBoolean verifyClaimed = new AtomicBoolean(false);

        private CachedPrefixes(PrefixTree prefixTree, boolean autoDiscovered) {
            this.prefixTree = prefixTree;
            this.autoDiscovered = autoDiscovered;
        }

        private PrefixTree prefixTree() {
            return prefixTree;
        }

        private boolean autoDiscovered() {
            return autoDiscovered;
        }

        private boolean claimVerification() {
            return verifyClaimed.compareAndSet(false, true);
        }

        private void drop() {
            this.prefixTree = BROKEN;
        }
    }

    private CachedPrefixes loadPrefixes(
            RepositorySystemSession session, Path baseDir, RemoteRepository remoteRepository) {
        if (isRepositoryFilteringEnabled(session, remoteRepository)) {
            String origin = "user-provided";
            Path filePath = resolvePrefixesFromLocalConfiguration(session, baseDir, remoteRepository);
            if (filePath == null) {
                if (!supportedResolvePrefixesForRemoteRepository(session, remoteRepository)) {
                    origin = "unsupported";
                } else {
                    origin = "auto-discovered";
                    filePath = resolvePrefixesFromRemoteRepository(session, remoteRepository);
                }
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
                    return new CachedPrefixes(prefixTree, "auto-discovered".equals(origin));
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
            return CachedPrefixes.NO_INPUT_PREFIXES;
        }
        logger.debug("Prefix file for remote repository {} disabled", remoteRepository);
        return CachedPrefixes.DISABLED_PREFIXES;
    }

    private Path resolvePrefixesFromLocalConfiguration(
            RepositorySystemSession session, Path baseDir, RemoteRepository remoteRepository) {
        Path filePath =
                baseDir.resolve(PREFIXES_FILE_PREFIX + repositoryKey(session, remoteRepository) + PREFIXES_FILE_SUFFIX);
        if (Files.isReadable(filePath)) {
            return filePath;
        } else {
            return null;
        }
    }

    private boolean supportedResolvePrefixesForRemoteRepository(
            RepositorySystemSession session, RemoteRepository remoteRepository) {
        if (!ConfigUtils.getBoolean(
                session,
                DEFAULT_RESOLVE_PREFIX_FILES,
                CONFIG_PROP_RESOLVE_PREFIX_FILES + "." + remoteRepository.getId(),
                CONFIG_PROP_RESOLVE_PREFIX_FILES)) {
            return false;
        }
        if (remoteRepository.isRepositoryManager()) {
            return ConfigUtils.getBoolean(
                    session, DEFAULT_USE_REPOSITORY_MANAGERS, CONFIG_PROP_USE_REPOSITORY_MANAGERS);
        } else {
            return remoteRepository.getMirroredRepositories().isEmpty()
                    || ConfigUtils.getBoolean(
                            session, DEFAULT_USE_MIRRORED_REPOSITORIES, CONFIG_PROP_USE_MIRRORED_REPOSITORIES);
        }
    }

    private Path resolvePrefixesFromRemoteRepository(
            RepositorySystemSession session, RemoteRepository remoteRepository) {
        MetadataResolver mr = metadataResolver.get();
        RemoteRepositoryManager rm = remoteRepositoryManager.get();
        if (mr != null && rm != null) {
            // retrieve prefix as metadata from repository
            MetadataResult result = mr.resolveMetadata(
                            new DefaultRepositorySystemSession(session)
                                    .setTransferListener(null)
                                    .setConfigProperty(CONFIG_PROP_SKIPPED, Boolean.TRUE.toString()),
                            Collections.singleton(new MetadataRequest(
                                            new DefaultMetadata(PREFIX_FILE_TYPE, Metadata.Nature.RELEASE_OR_SNAPSHOT))
                                    .setRepository(remoteRepository)
                                    .setDeleteLocalCopyIfMissing(true)
                                    .setFavorLocalRepository(true)))
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
            if (repositoryLayout == NOT_SUPPORTED) {
                return result(true, NAME, "Unsupported layout: " + remoteRepository);
            }
            return acceptPrefix(
                    remoteRepository,
                    repositoryLayout.getLocation(artifact, false).getPath());
        }

        @Override
        public Result acceptMetadata(RemoteRepository remoteRepository, Metadata metadata) {
            RepositoryLayout repositoryLayout = cacheLayout(session, remoteRepository);
            if (repositoryLayout == NOT_SUPPORTED) {
                return result(true, NAME, "Unsupported layout: " + remoteRepository);
            }
            return acceptPrefix(
                    remoteRepository,
                    repositoryLayout.getLocation(metadata, false).getPath());
        }

        private Result acceptPrefix(RemoteRepository repository, String path) {
            CachedPrefixes cachedPrefixes = cachePrefixes(session, basedir, repository);
            PrefixTree prefixTree = cachedPrefixes.prefixTree();
            if (prefixTree == DISABLED) {
                return result(true, NAME, "Disabled");
            } else if (prefixTree == ENABLED_NO_INPUT) {
                return noInputResult(repository, "No input available");
            } else if (prefixTree == BROKEN) {
                return noInputResult(repository, "Broken auto-discovered prefixes dropped");
            }
            boolean accepted = prefixTree.acceptedPath(path);
            if (!accepted && cachedPrefixes.autoDiscovered() && isVerifyDeniedEnabled(repository)) {
                // synchronized: only the first denial is verified; concurrent denials wait for the verdict
                synchronized (cachedPrefixes) {
                    if (cachedPrefixes.claimVerification() && remoteRepositoryServesPath(repository, path)) {
                        logger.warn(
                                "Remote repository {} serves a broken prefixes file: it denies path {} that the "
                                        + "repository actually serves; ignoring auto-discovered prefixes for this "
                                        + "repository (report this to the repository administrator)",
                                repository.getId(),
                                path);
                        cachedPrefixes.drop();
                    }
                }
                if (cachedPrefixes.prefixTree() == BROKEN) {
                    return noInputResult(repository, "Broken auto-discovered prefixes dropped");
                }
            }
            return result(
                    accepted,
                    NAME,
                    accepted
                            ? "Path " + path + " allowed from " + repository.getId()
                            : "Path " + path + " NOT allowed from " + repository.getId());
        }

        private Result noInputResult(RemoteRepository repository, String reasoning) {
            return result(
                    ConfigUtils.getBoolean(
                            session,
                            DEFAULT_NO_INPUT_OUTCOME,
                            CONFIG_PROP_NO_INPUT_OUTCOME + "." + repository.getId(),
                            CONFIG_PROP_NO_INPUT_OUTCOME),
                    NAME,
                    reasoning);
        }

        private boolean isVerifyDeniedEnabled(RemoteRepository repository) {
            return !session.isOffline()
                    && ConfigUtils.getBoolean(
                            session,
                            DEFAULT_VERIFY_DENIED,
                            CONFIG_PROP_VERIFY_DENIED + "." + repository.getId(),
                            CONFIG_PROP_VERIFY_DENIED);
        }

        /**
         * Checks whether the remote repository actually serves given path, using a lightweight existence check
         * (the transporter sits below the filtering connector, so no recursion can happen). Any failure (path
         * not present, transport problem) yields {@code false}: the prefixes file is dropped only when the
         * remote repository provably serves the denied path.
         */
        private boolean remoteRepositoryServesPath(RemoteRepository repository, String path) {
            try (Transporter transporter = transporterProvider.newTransporter(session, repository)) {
                transporter.peek(new PeekTask(new URI(null, null, path, null)));
                return true;
            } catch (URISyntaxException e) {
                logger.debug("Cannot construct URI for denied path {} of {}", path, repository, e);
                return false;
            } catch (Exception e) {
                logger.debug("Verification of denied path {} against {} failed", path, repository, e);
                return false;
            }
        }
    }

    private static final RepositoryLayout NOT_SUPPORTED = new RepositoryLayout() {
        @Override
        public List<ChecksumAlgorithmFactory> getChecksumAlgorithmFactories() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasChecksums(Artifact artifact) {
            throw new UnsupportedOperationException();
        }

        @Override
        public URI getLocation(Artifact artifact, boolean upload) {
            throw new UnsupportedOperationException();
        }

        @Override
        public URI getLocation(Metadata metadata, boolean upload) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ChecksumLocation> getChecksumLocations(Artifact artifact, boolean upload, URI location) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ChecksumLocation> getChecksumLocations(Metadata metadata, boolean upload, URI location) {
            throw new UnsupportedOperationException();
        }
    };
}
