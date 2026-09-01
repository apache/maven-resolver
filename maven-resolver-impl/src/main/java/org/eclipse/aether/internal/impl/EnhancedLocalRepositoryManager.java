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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.LocalArtifactRegistration;
import org.eclipse.aether.repository.LocalArtifactRequest;
import org.eclipse.aether.repository.LocalArtifactResult;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryKeyFunction;
import org.eclipse.aether.util.ConfigUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

/**
 * These are implementation details for enhanced local repository manager, subject to change without prior notice.
 * Repositories from which a cached artifact was resolved are tracked in a properties file named
 * <code>_remote.repositories</code>, with content key as filename&gt;repo_id and value as empty string. If a file has
 * been installed in the repository, but not downloaded from a remote repository, it is tracked as empty repository id
 * and always resolved. For example:
 *
 * <pre>
 * artifact-1.0.pom>=
 * artifact-1.0.jar>=
 * artifact-1.0.pom>central=
 * artifact-1.0.jar>central=
 * artifact-1.0.zip>central=
 * artifact-1.0-classifier.zip>central=
 * artifact-1.0.pom>my_repo_id=
 * </pre>
 *
 * The repository id component of a tracking key is produced by the tracking-scoped repository key function, which
 * is URL-qualified by default: two repositories that merely share an id but point at different URLs are tracked as
 * different origins (see
 * {@link EnhancedLocalRepositoryManagerFactory#CONFIG_PROP_TRACKING_REPOSITORY_KEY_FUNCTION}).
 *
 * @see EnhancedLocalRepositoryManagerFactory
 */
class EnhancedLocalRepositoryManager extends SimpleLocalRepositoryManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(EnhancedLocalRepositoryManager.class);

    private static final String LOCAL_REPO_ID = "";

    /**
     * Shared sentinel for "no tracking data". Mutation is forbidden: the instance is shared
     * across threads via {@link #trackingFileCache} and returned directly from {@link #readRepos}.
     */
    private static final Properties EMPTY_PROPERTIES = new Properties() {
        @Override
        public synchronized Object put(Object key, Object value) {
            throw new UnsupportedOperationException("EMPTY_PROPERTIES is read-only");
        }

        @Override
        public synchronized Object remove(Object key) {
            throw new UnsupportedOperationException("EMPTY_PROPERTIES is read-only");
        }

        @Override
        public synchronized void clear() {
            throw new UnsupportedOperationException("EMPTY_PROPERTIES is read-only");
        }
    };

    private final String trackingFilename;

    private final TrackingFileManager trackingFileManager;

    private final LocalPathPrefixComposer localPathPrefixComposer;

    /**
     * Repository key function used solely for the provenance tracking entries (the repository component of the
     * keys in the tracking file); path composition keeps using the (system-wide) key function held by the
     * superclass. URL-qualified by default, so two repositories merely sharing an id are not treated as the same
     * origin - see {@link EnhancedLocalRepositoryManagerFactory#CONFIG_PROP_TRACKING_REPOSITORY_KEY_FUNCTION}.
     * Lookups of entries written under a different key function miss and fail safe: the artifact stays tracked
     * (so the untracked inter-op fallback in {@link #checkFind} does not accept it) but unavailable, forcing a
     * checksum-validated re-fetch.
     */
    private final RepositoryKeyFunction trackingRepositoryKeyFunction;

    /**
     * Cache of tracking file contents, keyed by tracking file path. Eliminates redundant disk I/O
     * when multiple artifacts in the same directory are resolved — they all share the same
     * {@code _remote.repositories} tracking file. Invalidated on writes via {@link #addRepo}.
     * <p>
     * Cached {@link Properties} instances are shared across threads and must be treated as
     * read-only by callers of {@link #readRepos}. The cache is scoped to this manager instance
     * (one per session), so concurrent builds in separate JVMs each maintain independent caches.
     * If another process updates a tracking file after it has been cached here, a stale entry is
     * NOT always harmless: for a tracked artifact it merely causes a redundant download, but in the
     * untracked inter-op branch of {@link #checkFind} staleness would be trust-increasing (the file
     * would be accepted as locally installed although its recorded origin says otherwise). That
     * branch therefore re-reads the tracking file from disk before accepting, see
     * {@link #readReposFresh}.
     */
    private final ConcurrentHashMap<Path, Properties> trackingFileCache = new ConcurrentHashMap<>();

    /**
     * Lazily computed real (symlink-resolved) path of the local repository base directory, used by
     * {@link #hasFaithfulRealPath(Path)}. It cannot change during the lifetime of this manager.
     */
    private final Path realBasePath;

    EnhancedLocalRepositoryManager(
            Path basedir,
            LocalPathComposer localPathComposer,
            RepositoryKeyFunction repositoryKeyFunction,
            RepositoryKeyFunction trackingRepositoryKeyFunction,
            String trackingFilename,
            TrackingFileManager trackingFileManager,
            LocalPathPrefixComposer localPathPrefixComposer)
            throws IOException {
        super(basedir, "enhanced", localPathComposer, repositoryKeyFunction);
        this.trackingRepositoryKeyFunction = requireNonNull(trackingRepositoryKeyFunction);
        this.trackingFilename = requireNonNull(trackingFilename);
        this.trackingFileManager = requireNonNull(trackingFileManager);
        this.localPathPrefixComposer = requireNonNull(localPathPrefixComposer);
        this.realBasePath = getRepository().getBasePath().toRealPath();
    }

    private String concatPaths(String prefix, String artifactPath) {
        if (prefix == null || prefix.isEmpty()) {
            return artifactPath;
        }
        return prefix + '/' + artifactPath;
    }

    @Override
    public String getPathForLocalArtifact(Artifact artifact) {
        return concatPaths(
                localPathPrefixComposer.getPathPrefixForLocalArtifact(artifact),
                super.getPathForLocalArtifact(artifact));
    }

    @Override
    public String getPathForRemoteArtifact(Artifact artifact, RemoteRepository repository, String context) {
        return concatPaths(
                localPathPrefixComposer.getPathPrefixForRemoteArtifact(artifact, repository),
                super.getPathForRemoteArtifact(artifact, repository, context));
    }

    @Override
    public String getPathForLocalMetadata(Metadata metadata) {
        return concatPaths(
                localPathPrefixComposer.getPathPrefixForLocalMetadata(metadata),
                super.getPathForLocalMetadata(metadata));
    }

    @Override
    public String getPathForRemoteMetadata(Metadata metadata, RemoteRepository repository, String context) {
        return concatPaths(
                localPathPrefixComposer.getPathPrefixForRemoteMetadata(metadata, repository),
                super.getPathForRemoteMetadata(metadata, repository, context));
    }

    @Override
    public LocalArtifactResult find(RepositorySystemSession session, LocalArtifactRequest request) {
        Artifact artifact = request.getArtifact();
        LocalArtifactResult result = new LocalArtifactResult(request);

        boolean verifyRealPath = ConfigUtils.getBoolean(
                session,
                EnhancedLocalRepositoryManagerFactory.DEFAULT_VERIFY_REAL_PATH,
                EnhancedLocalRepositoryManagerFactory.CONFIG_PROP_VERIFY_REAL_PATH);

        Path filePath;

        // Local repository CANNOT have timestamped installed, they are created only during deploy
        if (Objects.equals(artifact.getVersion(), artifact.getBaseVersion())) {
            filePath = getAbsolutePathForLocalArtifact(artifact);
            checkFind(filePath, result, verifyRealPath);
        }

        if (!result.isAvailable()) {
            for (RemoteRepository repository : request.getRepositories()) {
                filePath = getAbsolutePathForRemoteArtifact(artifact, repository, request.getContext());

                checkFind(filePath, result, verifyRealPath);

                if (result.isAvailable()) {
                    break;
                }
            }
        }

        return result;
    }

    /**
     * Verifies that the real (on-disk) spelling of the given artifact path matches the requested spelling,
     * relative to the local repository base directory. On case-insensitive or normalization-preserving
     * filesystems (the macOS and Windows defaults) a cached file whose stored name differs from the requested one
     * - for example one cached for case-colliding coordinates - still passes the file-existence check, while the
     * tracking data in the tracking file is compared exactly: the aliased file would then be treated as
     * present-but-untracked and accepted with no download and no checksum verification. Such aliases are treated
     * as "not present" instead (fail closed), forcing a proper download for the requested coordinates. The
     * comparison is relative to the (symlink-resolved) base directory, so a symlinked base directory is
     * supported; symbolic links below the base directory are not - see
     * {@link EnhancedLocalRepositoryManagerFactory#CONFIG_PROP_VERIFY_REAL_PATH} to opt out.
     */
    private boolean hasFaithfulRealPath(Path path) {
        try {
            String requested = getRepository().getBasePath().relativize(path).toString();
            String real = realBasePath.relativize(path.toRealPath()).toString();
            if (!requested.equals(real)) {
                LOGGER.warn(
                        "Rejecting locally cached artifact {}: its on-disk path {} does not match the requested"
                                + " coordinates (filesystem case/normalization alias); treating it as not present",
                        path,
                        real);
                return false;
            }
            return true;
        } catch (IOException | IllegalArgumentException e) {
            // the real identity of the file cannot be established: fail closed, forcing a re-download
            return false;
        }
    }

    private void checkFind(Path path, LocalArtifactResult result, boolean verifyRealPath) {
        if (Files.isRegularFile(path) && (!verifyRealPath || hasFaithfulRealPath(path))) {
            result.setPath(path);

            Properties props = readRepos(path);

            if (!applyTracking(path, result, props) && !isTracked(props, path)) {
                /*
                 * The (possibly cached) state claims the artifact is untracked, and the untracked inter-op
                 * branch below is trust-increasing: it accepts the file as locally installed. That decision
                 * must never rest on tracking state that may be older than the file it judges (another process
                 * sharing this local repository may have recorded the true origin of the file after our cache
                 * entry was populated), so re-read the tracking file from disk before concluding untracked.
                 */
                props = readReposFresh(path);
                if (!applyTracking(path, result, props) && !isTracked(props, path)) {
                    /*
                     * NOTE: The artifact is present but not tracked at all, for inter-op with simple local repo, assume
                     * the artifact was locally installed.
                     */
                    result.setAvailable(true);
                }
            }
        }
    }

    /**
     * Applies the tracking-state based acceptance rules to given result: an artifact installed into the local
     * repository is always accepted, an artifact downloaded from a remote repository is accepted only if
     * downloaded from one of the request repositories.
     *
     * @return {@code true} if the result was made available, {@code false} otherwise.
     */
    private boolean applyTracking(Path path, LocalArtifactResult result, Properties props) {
        if (props.get(getKey(path, LOCAL_REPO_ID)) != null) {
            // artifact installed into the local repo is always accepted
            result.setAvailable(true);
            return true;
        }
        String context = result.getRequest().getContext();
        for (RemoteRepository repository : result.getRequest().getRepositories()) {
            if (props.get(getKey(path, getTrackingRepositoryKey(repository, context))) != null) {
                // artifact downloaded from remote repository is accepted only downloaded from request
                // repositories
                result.setAvailable(true);
                result.setRepository(repository);
                return true;
            }
        }
        return false;
    }

    @Override
    public void add(RepositorySystemSession session, LocalArtifactRegistration request) {
        Collection<String> repositories;
        if (request.getRepository() == null) {
            repositories = Collections.singleton(LOCAL_REPO_ID);
        } else {
            repositories = getRepositoryKeys(request.getRepository(), request.getContexts());
        }
        if (request.getRepository() == null) {
            addArtifact(request.getArtifact(), repositories, null, null);
        } else {
            for (String context : request.getContexts()) {
                addArtifact(request.getArtifact(), repositories, request.getRepository(), context);
            }
        }
    }

    private Collection<String> getRepositoryKeys(RemoteRepository repository, Collection<String> contexts) {
        Collection<String> keys = new HashSet<>();

        if (contexts != null) {
            for (String context : contexts) {
                keys.add(getTrackingRepositoryKey(repository, context));
            }
        }

        return keys;
    }

    private void addArtifact(
            Artifact artifact, Collection<String> repositories, RemoteRepository repository, String context) {
        requireNonNull(artifact, "artifact cannot be null");
        Path file = repository == null
                ? getAbsolutePathForLocalArtifact(artifact)
                : getAbsolutePathForRemoteArtifact(artifact, repository, context);
        addRepo(file, repositories);
    }

    private Properties readRepos(Path artifactPath) {
        Path trackingFile = getTrackingFile(artifactPath);
        return trackingFileCache.computeIfAbsent(trackingFile, tf -> {
            Properties props = trackingFileManager.read(tf);
            return (props != null) ? props : EMPTY_PROPERTIES;
        });
    }

    /**
     * Reads the tracking file for given artifact directly from disk, bypassing (and deliberately not populating)
     * {@link #trackingFileCache}: callers use the result for a trust-increasing decision, which must not be taken
     * on — nor allowed to re-cache — possibly stale state.
     */
    private Properties readReposFresh(Path artifactPath) {
        Properties props = trackingFileManager.read(getTrackingFile(artifactPath));
        return (props != null) ? props : EMPTY_PROPERTIES;
    }

    private void addRepo(Path artifactPath, Collection<String> repositories) {
        Map<String, String> updates = new HashMap<>();
        for (String repository : repositories) {
            updates.put(getKey(artifactPath, repository), "");
        }

        Path trackingPath = getTrackingFile(artifactPath);

        // Invalidate cache before AND after the write. Before: using put() with the returned Properties
        // would be racy — two concurrent addRepo() calls could reorder their puts, leaving stale data.
        // After: a reader may re-populate the cache with the pre-write contents between the first
        // invalidation and the write completing; without a post-write invalidation that stale entry —
        // which is trust-affecting, see checkFind — would survive for the whole session.
        trackingFileCache.remove(trackingPath);
        try {
            trackingFileManager.update(trackingPath, updates);
        } finally {
            trackingFileCache.remove(trackingPath);
        }
    }

    private Path getTrackingFile(Path artifactPath) {
        return artifactPath.getParent().resolve(trackingFilename);
    }

    private String getKey(Path path, String repository) {
        return path.getFileName() + ">" + repository;
    }

    /**
     * Returns the tracking key of given repository, derived with the tracking-scoped key function (URL-qualified
     * by default). Deliberately distinct from {@link #getRepositoryKey(RemoteRepository, String)}, which follows
     * the system-wide key function and is used for path composition: tracking must bind an artifact to the full
     * identity of its origin, while on-disk layout and repository aggregation identity stay unchanged.
     */
    private String getTrackingRepositoryKey(RemoteRepository repository, String context) {
        return trackingRepositoryKeyFunction.apply(repository, context);
    }

    private boolean isTracked(Properties props, Path path) {
        if (props != null) {
            String keyPrefix = path.getFileName() + ">";
            for (Object key : props.keySet()) {
                if (key.toString().startsWith(keyPrefix)) {
                    return true;
                }
            }
        }
        return false;
    }
}
