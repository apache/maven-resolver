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
package org.eclipse.aether.util.repository;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.WorkspaceRepository;
import org.eclipse.aether.util.StringDigestUtil;

import static java.util.Objects.requireNonNull;

/**
 * Helper class for {@link ArtifactRepository#getId()} handling.
 *
 * @since 2.0.11
 */
public final class RepositoryIdHelper {
    private RepositoryIdHelper() {}

    private static final String ILLEGAL_REPO_ID_CHARS = "\\/:\"<>|?*";
    private static final String REPLACEMENT_REPO_ID_CHAR = "X";
    private static final String CENTRAL_REPOSITORY_ID = "central";
    private static final Collection<String> CENTRAL_URLS = Collections.unmodifiableList(Arrays.asList(
            "https://repo.maven.apache.org/maven2",
            "https://repo1.maven.org/maven2",
            "https://maven-central.storage-download.googleapis.com/maven2"));
    private static final Predicate<RemoteRepository> CENTRAL_DIRECT_ONLY =
            remoteRepository -> CENTRAL_REPOSITORY_ID.equals(remoteRepository.getId())
                    && "https".equals(remoteRepository.getProtocol().toLowerCase(Locale.ROOT))
                    && CENTRAL_URLS.stream().anyMatch(remoteUrl -> {
                        String rurl = remoteRepository.getUrl().toLowerCase(Locale.ROOT);
                        if (rurl.endsWith("/")) {
                            rurl = rurl.substring(0, rurl.length() - 1);
                        }
                        return rurl.equals(remoteUrl);
                    })
                    && remoteRepository.getPolicy(false).isEnabled()
                    && !remoteRepository.getPolicy(true).isEnabled()
                    && remoteRepository.getMirroredRepositories().isEmpty()
                    && !remoteRepository.isRepositoryManager()
                    && !remoteRepository.isBlocked();

    /**
     * Provides cached (or uncached, if session has no cache set) for {@link #idToSafePathSegment(RemoteRepository)} function.
     */
    @SuppressWarnings("unchecked")
    public static Function<? extends ArtifactRepository, String> cachedIdToSafePathSegment(
            RepositorySystemSession session) {
        if (session.getCache() != null) {
            return repository -> ((ConcurrentHashMap<ArtifactRepository, String>) session.getCache()
                            .computeIfAbsent(
                                    session,
                                    RepositoryIdHelper.class.getSimpleName() + "-idToSafePathSegment",
                                    ConcurrentHashMap::new))
                    .computeIfAbsent(repository, RepositoryIdHelper::idToSafePathSegment);
        } else {
            return RepositoryIdHelper::idToSafePathSegment; // uncached
        }
    }

    /**
     * Provides cached (or uncached, if session has no cache set) for {@link #idToPathSegment(RemoteRepository)} function.
     */
    @SuppressWarnings("unchecked")
    public static Function<RemoteRepository, String> cachedIdToPathSegment(RepositorySystemSession session) {
        if (session.getCache() != null) {
            return repository -> ((ConcurrentHashMap<RemoteRepository, String>) session.getCache()
                            .computeIfAbsent(
                                    session,
                                    RepositoryIdHelper.class.getSimpleName() + "-idToPathSegment",
                                    ConcurrentHashMap::new))
                    .computeIfAbsent(repository, RepositoryIdHelper::idToPathSegment);
        } else {
            return RepositoryIdHelper::idToPathSegment; // uncached
        }
    }

    /**
     * This method returns the passed in {@link ArtifactRepository#getId()} value, but it makes them unique to prevent
     * ID overlap in unrelated builds. Only Maven Central will have return ID "central", while all the other
     * remote repository will have returned string in form of {@code repo.ID-sha1(repo.url)}.
     * <p>
     * This method should be used when code operates with {@link ArtifactRepository}s and uses repository ID as identifier
     * (like enhanced local repository is, or split repository). Use of ID solely can result in clash and overlaps,
     * as for example repository with ID "releases" may be defined in multiple builds, but pointing to different
     * URLs.
     * <p>
     * This method is simplistic on purpose, and if frequently used, best if results are cached (per session).
     */
    public static String idToSafePathSegment(ArtifactRepository repository) {
        requireNonNull(repository, "repository");
        if (repository instanceof LocalRepository) {
            return repository.getId(); // "local"
        } else if (repository instanceof WorkspaceRepository) {
            return repository.getId(); // "workspace"
        } else if (repository instanceof RemoteRepository) {
            return idToSafePathSegment((RemoteRepository) repository);
        } else {
            throw new IllegalArgumentException("Unknown repository type: " + repository);
        }
    }

    /**
     * This method returns the passed in {@link RemoteRepository#getId()} value, but it makes them unique to prevent
     * ID overlap in unrelated builds. Only Maven Central will have return ID "central", while all the other
     * remote repository will have returned string in form of {@code repo.ID-sha1(repo.url)}.
     * <p>
     * This method should be used when code operates with {@link RemoteRepository}s and uses repository ID as identifier
     * (like enhanced local repository is, or split repository). Use of ID solely can result in clash and overlaps,
     * as for example repository with ID "releases" may be defined in multiple builds, but pointing to different
     * URLs.
     * <p>
     * This method is simplistic on purpose, and if frequently used, best if results are cached (per session).
     */
    public static String idToSafePathSegment(RemoteRepository repository) {
        if (CENTRAL_DIRECT_ONLY.test(repository)) {
            return repository.getId();
        } else {
            return idToPathSegment(repository) + "-" + StringDigestUtil.sha1(repository.getUrl());
        }
    }

    /**
     * This method returns the passed in {@link RemoteRepository#getId()} value, modifying it if needed, making sure that
     * returned repository ID is "path segment" safe. Ideally, this method should never modify repository ID, as
     * Maven validation prevents use of illegal FS characters in them, but we found in Maven Central several POMs that
     * define remote repositories with illegal FS characters in their ID.
     * <p>
     * This method is simplistic on purpose, and if frequently used, best if results are cached (per session),
     * see {@link #cachedIdToPathSegment(RepositorySystemSession)} method.
     *
     * @see #cachedIdToPathSegment(RepositorySystemSession)
     */
    public static String idToPathSegment(RemoteRepository repository) {
        StringBuilder result = new StringBuilder(repository.getId());
        for (int illegalIndex = 0; illegalIndex < ILLEGAL_REPO_ID_CHARS.length(); illegalIndex++) {
            String illegal = ILLEGAL_REPO_ID_CHARS.substring(illegalIndex, illegalIndex + 1);
            int pos = result.indexOf(illegal);
            while (pos >= 0) {
                result.replace(pos, pos + 1, REPLACEMENT_REPO_ID_CHAR);
                pos = result.indexOf(illegal);
            }
        }
        return result.toString();
    }
}
