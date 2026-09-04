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

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.LocalArtifactRegistration;
import org.eclipse.aether.repository.LocalArtifactRequest;
import org.eclipse.aether.repository.LocalArtifactResult;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.PathUtils;
import org.eclipse.aether.util.StringDigestUtil;

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
 * The repository component of a tracking key is produced by the tracking repository key function, which is
 * URL-qualified by default: two repositories that merely share an id but point at different URLs are tracked as
 * different origins (see {@link EnhancedLocalRepositoryManagerFactory}).
 *
 * @see EnhancedLocalRepositoryManagerFactory
 */
class EnhancedLocalRepositoryManager extends SimpleLocalRepositoryManager {

    private static final String LOCAL_REPO_ID = "";

    private final String trackingFilename;

    private final TrackingFileManager trackingFileManager;

    private final LocalPathPrefixComposer localPathPrefixComposer;

    /**
     * The functions producing the repository component of a tracking entry.
     */
    enum TrackingKeyFunction {
        /**
         * The repository id alone, as written by earlier releases.
         */
        NID,
        /**
         * The repository id qualified with a hash of the repository URL, see
         * {@link #urlQualifiedRepositoryKey(RemoteRepository, String)}.
         */
        NID_HURL
    }

    /**
     * Key function used solely for the tracking entries (the repository component of the keys in the tracking
     * file); path composition keeps using {@link #getRepositoryKey(RemoteRepository, String)}. Entries written
     * under a different key function never match a lookup: the artifact stays tracked, so the untracked inter-op
     * branch in {@link #checkFind} does not accept it, but unavailable, and is fetched again.
     */
    private final TrackingKeyFunction trackingKeyFunction;

    EnhancedLocalRepositoryManager(
            File basedir,
            LocalPathComposer localPathComposer,
            String trackingFilename,
            TrackingFileManager trackingFileManager,
            LocalPathPrefixComposer localPathPrefixComposer) {
        this(
                basedir,
                localPathComposer,
                trackingFilename,
                trackingFileManager,
                localPathPrefixComposer,
                TrackingKeyFunction.NID_HURL);
    }

    EnhancedLocalRepositoryManager(
            File basedir,
            LocalPathComposer localPathComposer,
            String trackingFilename,
            TrackingFileManager trackingFileManager,
            LocalPathPrefixComposer localPathPrefixComposer,
            TrackingKeyFunction trackingKeyFunction) {
        super(basedir, "enhanced", localPathComposer);
        this.trackingFilename = requireNonNull(trackingFilename);
        this.trackingFileManager = requireNonNull(trackingFileManager);
        this.localPathPrefixComposer = requireNonNull(localPathPrefixComposer);
        this.trackingKeyFunction = requireNonNull(trackingKeyFunction);
    }

    /**
     * The URL-qualified tracking key: the repository id as a path segment, a dash, and the SHA-1 of the repository
     * URL (with the context appended for repository managers). Same shape as the {@code nid_hurl} repository key
     * function of the 2.x line, so tracking files are shared between the lines.
     */
    static String urlQualifiedRepositoryKey(RemoteRepository repository, String context) {
        String seed = repository.getUrl();
        if (repository.isRepositoryManager() && context != null && !context.isEmpty()) {
            seed += context;
        }
        return PathUtils.stringToPathSegment(repository.getId()) + "-" + StringDigestUtil.sha1(seed);
    }

    /**
     * Returns the tracking key of given repository. Deliberately distinct from
     * {@link #getRepositoryKey(RemoteRepository, String)}, which is used for path composition: tracking binds an
     * artifact to the full identity of its origin, while the on-disk layout stays unchanged.
     */
    private String getTrackingRepositoryKey(RemoteRepository repository, String context) {
        if (trackingKeyFunction == TrackingKeyFunction.NID_HURL) {
            return urlQualifiedRepositoryKey(repository, context);
        }
        return getRepositoryKey(repository, context);
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

        String path;
        File file;

        // Local repository CANNOT have timestamped installed, they are created only during deploy
        if (Objects.equals(artifact.getVersion(), artifact.getBaseVersion())) {
            path = getPathForLocalArtifact(artifact);
            file = new File(getRepository().getBasedir(), path);
            checkFind(file, result);
        }

        if (!result.isAvailable()) {
            for (RemoteRepository repository : request.getRepositories()) {
                path = getPathForRemoteArtifact(artifact, repository, request.getContext());
                file = new File(getRepository().getBasedir(), path);

                checkFind(file, result);

                if (result.isAvailable()) {
                    break;
                }
            }
        }

        return result;
    }

    private void checkFind(File file, LocalArtifactResult result) {
        if (file.isFile()) {
            result.setFile(file);

            Properties props = readRepos(file);

            if (props.get(getKey(file, LOCAL_REPO_ID)) != null) {
                // artifact installed into the local repo is always accepted
                result.setAvailable(true);
            } else {
                String context = result.getRequest().getContext();
                for (RemoteRepository repository : result.getRequest().getRepositories()) {
                    if (props.get(getKey(file, getTrackingRepositoryKey(repository, context))) != null) {
                        // artifact downloaded from remote repository is accepted only downloaded from request
                        // repositories
                        result.setAvailable(true);
                        result.setRepository(repository);
                        break;
                    }
                }
                if (!result.isAvailable() && !isTracked(props, file)) {
                    /*
                     * NOTE: The artifact is present but not tracked at all, for inter-op with simple local repo, assume
                     * the artifact was locally installed.
                     */
                    result.setAvailable(true);
                }
            }
        }
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
        String path = repository == null
                ? getPathForLocalArtifact(artifact)
                : getPathForRemoteArtifact(artifact, repository, context);
        File file = new File(getRepository().getBasedir(), path);
        addRepo(file, repositories);
    }

    private Properties readRepos(File artifactFile) {
        File trackingFile = getTrackingFile(artifactFile);

        Properties props = trackingFileManager.read(trackingFile);

        return (props != null) ? props : new Properties();
    }

    private void addRepo(File artifactFile, Collection<String> repositories) {
        Map<String, String> updates = new HashMap<>();
        for (String repository : repositories) {
            updates.put(getKey(artifactFile, repository), "");
        }

        File trackingFile = getTrackingFile(artifactFile);

        trackingFileManager.update(trackingFile, updates);
    }

    private File getTrackingFile(File artifactFile) {
        return new File(artifactFile.getParentFile(), trackingFilename);
    }

    private String getKey(File file, String repository) {
        return file.getName() + '>' + repository;
    }

    private boolean isTracked(Properties props, File file) {
        if (props != null) {
            String keyPrefix = file.getName() + '>';
            for (Object key : props.keySet()) {
                if (key.toString().startsWith(keyPrefix)) {
                    return true;
                }
            }
        }
        return false;
    }
}
