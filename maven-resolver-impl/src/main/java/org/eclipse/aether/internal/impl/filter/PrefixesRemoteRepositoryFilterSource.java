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

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.impl.MetadataResolver;
import org.eclipse.aether.impl.RemoteRepositoryManager;
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
     * Visible for UT.
     */
    static final String PREFIX_FIRST_LINE = "## repository-prefixes/2.0";

    /**
     * The configuration to enable filter (by default is enabled). It can be "fine-tuned" by repository id appended.
     * Note: for this filter to have effect, aside of enabling it, it must have configuration file available as well.
     * Without configuration file for given repository, the enabled filter remains dormant, the filter does not interfere.
     * The filter will check for user provided configuration file first, that are looked up from directory specified by
     * {@link #CONFIG_PROP_BASEDIR} that defaults to {@code $LOCAL_REPO/.remoteRepositoryFilters} and if not found,
     * will try to discover it (by getting it from remote repository and caching it in local repository). The user
     * provided files for each repository should be named as {@code prefixes-$(repository.id).txt}.
     * <p>
     * The recommended way to set up prefix filtering is to rely on auto discovery, but be prepared for per project
     * (project specific) intervention. To achieve this one can use {@code .mvn/maven.config} file with following entries:
     * <pre>
     * {@code
     * -Daether.remoteRepositoryFilter.prefixes=true
     * -Daether.remoteRepositoryFilter.prefixes.basedir=${session.rootDirectory}/.mvn/rrf/
     * }
     * </pre>
     * And as starting point user should not provide any files. This setup will rely on auto-discovery of required
     * prefix files (as repositories are accessed), while if user override is needed, override files should be named as
     * {@code prefixes-myrepoId.txt} and checked into the {@code .mvn/rrf/} directory.
     * <p>
     * Note: cached prefix file in local repository are made to have unique IDs using {@link RepositoryIdHelper#remoteRepositoryUniqueId(RemoteRepository)}
     * method, to ensure that prefix files does not clash as that may lead to build failures.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationRepoIdSuffix Yes
     * @configurationDefaultValue {@link #DEFAULT_ENABLED}
     */
    public static final String CONFIG_PROP_ENABLED = RemoteRepositoryFilterSourceSupport.CONFIG_PROPS_PREFIX + NAME;

    public static final boolean DEFAULT_ENABLED = true;

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

    private final ConcurrentHashMap<RemoteRepository, Boolean> ongoingUpdates;

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
        this.ongoingUpdates = new ConcurrentHashMap<>();
    }

    @Override
    protected boolean isEnabled(RepositorySystemSession session) {
        return ConfigUtils.getBoolean(session, DEFAULT_ENABLED, CONFIG_PROP_ENABLED);
    }

    private boolean isRepositoryFilteringEnabled(RepositorySystemSession session, RemoteRepository remoteRepository) {
        if (isEnabled(session)) {
            return ConfigUtils.getBoolean(
                    session,
                    ConfigUtils.getBoolean(session, true, CONFIG_PROP_ENABLED + ".*"),
                    CONFIG_PROP_ENABLED + "." + remoteRepository.getId());
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
        return ongoingUpdatesGuard(
                remoteRepository,
                () -> prefixes.computeIfAbsent(
                        remoteRepository, r -> loadPrefixTree(session, basedir, remoteRepository)),
                () -> PrefixTree.SENTINEL);
    }

    private <T> T ongoingUpdatesGuard(RemoteRepository remoteRepository, Supplier<T> unblocked, Supplier<T> blocked) {
        if (!remoteRepository.isBlocked() && null == ongoingUpdates.putIfAbsent(remoteRepository, Boolean.TRUE)) {
            try {
                return unblocked.get();
            } finally {
                ongoingUpdates.remove(remoteRepository);
            }
        }
        return blocked.get();
    }

    private PrefixTree loadPrefixTree(
            RepositorySystemSession session, Path baseDir, RemoteRepository remoteRepository) {
        if (isRepositoryFilteringEnabled(session, remoteRepository)) {
            Path filePath = resolvePrefixesFromLocalConfiguration(session, baseDir, remoteRepository);
            if (filePath == null) {
                filePath = resolvePrefixesFromRemoteRepository(session, remoteRepository);
            }
            if (isPrefixFile(filePath)) {
                logger.debug(
                        "Loading prefixes for remote repository {} from file '{}'", remoteRepository.getId(), filePath);
                try (Stream<String> lines = Files.lines(filePath, StandardCharsets.UTF_8)) {
                    PrefixTree prefixTree = new PrefixTree("");
                    int rules = prefixTree.loadNodes(lines);
                    logger.info("Loaded {} prefixes for remote repository {}", rules, remoteRepository.getId());
                    return prefixTree;
                } catch (FileNotFoundException e) {
                    // strange: we tested for it above, still, we should not fail
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
            logger.debug("Prefix file for remote repository {} not found at '{}'", remoteRepository, filePath);
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

    private boolean isPrefixFile(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return false;
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return PREFIX_FIRST_LINE.equals(reader.readLine());
        } catch (FileNotFoundException e) {
            return false;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
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
            // make it unique
            RemoteRepository unique = new RemoteRepository.Builder(prepared)
                    .setId(RepositoryIdHelper.remoteRepositoryUniqueId(remoteRepository))
                    .build();
            // supplier for path
            Supplier<Path> supplier = () -> {
                MetadataRequest request =
                        new MetadataRequest(new DefaultMetadata(PREFIX_FILE_PATH, Metadata.Nature.RELEASE_OR_SNAPSHOT));
                // use unique repository; this will result in prefix (repository metadata) cached under unique
                // id
                request.setRepository(unique);
                request.setDeleteLocalCopyIfMissing(true);
                request.setFavorLocalRepository(true);
                MetadataResult result = mr.resolveMetadata(session, Collections.singleton(request))
                        .get(0);
                if (result.isResolved()) {
                    return result.getMetadata().getPath();
                } else {
                    return null;
                }
            };

            // prevent recursive calls; but we need extra work if not dealing with Central (as in that case outer call
            // shields us)
            if (Objects.equals(prepared.getId(), unique.getId())) {
                return supplier.get();
            } else {
                return ongoingUpdatesGuard(unique, supplier, () -> null);
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
}
