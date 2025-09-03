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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.StringDigestUtil;

import static java.util.Objects.requireNonNull;

/**
 * Helper class for {@link ArtifactRepository#getId()} handling. This class provides  helper function (cached or uncached)
 * to get id of repository as it was originally envisioned: as path safe. While POMs are validated by Maven, there are
 * POMs out there that somehow define repositories with unsafe characters in their id. The problem affects mostly
 * {@link RemoteRepository} instances, as all other implementations have fixed ids that are path safe.
 *
 * @since 2.0.11
 */
public final class RepositoryIdHelper {
    private RepositoryIdHelper() {}

    private static final Map<String, String> ILLEGAL_REPO_ID_REPLACEMENTS;

    static {
        HashMap<String, String> illegalRepoIdReplacements = new HashMap<>();
        illegalRepoIdReplacements.put("\\", "-BACKSLASH-");
        illegalRepoIdReplacements.put("/", "-SLASH-");
        illegalRepoIdReplacements.put(":", "-COLON-");
        illegalRepoIdReplacements.put("\"", "-QUOTE-");
        illegalRepoIdReplacements.put("<", "-LT-");
        illegalRepoIdReplacements.put(">", "-GT-");
        illegalRepoIdReplacements.put("|", "-PIPE-");
        illegalRepoIdReplacements.put("?", "-QMARK-");
        illegalRepoIdReplacements.put("*", "-ASTERISK-");
        ILLEGAL_REPO_ID_REPLACEMENTS = Collections.unmodifiableMap(illegalRepoIdReplacements);
    }

    private static final String CENTRAL_REPOSITORY_ID = "central";
    private static final Collection<String> CENTRAL_URLS = Collections.unmodifiableList(Arrays.asList(
            "https://repo.maven.apache.org/maven2",
            "https://repo1.maven.org/maven2",
            "https://maven-central.storage-download.googleapis.com/maven2"));
    private static final Predicate<RemoteRepository> CENTRAL_DIRECT_ONLY =
            remoteRepository -> CENTRAL_REPOSITORY_ID.equals(remoteRepository.getId())
                    && "https".equals(remoteRepository.getProtocol().toLowerCase(Locale.ENGLISH))
                    && CENTRAL_URLS.stream().anyMatch(remoteUrl -> {
                        String rurl = remoteRepository.getUrl().toLowerCase(Locale.ENGLISH);
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
     * Creates unique repository id for given {@link RemoteRepository}. For Maven Central this method will return
     * string "central", while for any other remote repository it will return string created as
     * {@code $(repository.id)-sha1(repository-aspects)}. The key material contains all relevant aspects
     * of remote repository, so repository with same ID even if just policy changes (enabled/disabled), will map to
     * different string id. The checksum and update policies are not participating in key creation.
     * <p>
     * This method is costly, so should be invoked sparingly, or cache results if needed.
     */
    public static String remoteRepositoryUniqueId(RemoteRepository repository) {
        if (CENTRAL_DIRECT_ONLY.test(repository)) {
            return CENTRAL_REPOSITORY_ID;
        } else {
            StringBuilder buffer = new StringBuilder(256);
            buffer.append(repository.getId());
            buffer.append(" (").append(repository.getUrl());
            buffer.append(", ").append(repository.getContentType());
            boolean r = repository.getPolicy(false).isEnabled(),
                    s = repository.getPolicy(true).isEnabled();
            if (r && s) {
                buffer.append(", releases+snapshots");
            } else if (r) {
                buffer.append(", releases");
            } else if (s) {
                buffer.append(", snapshots");
            } else {
                buffer.append(", disabled");
            }
            if (repository.isRepositoryManager()) {
                buffer.append(", managed(");
                for (RemoteRepository mirroredRepo : repository.getMirroredRepositories()) {
                    buffer.append(remoteRepositoryUniqueId(mirroredRepo));
                }
                buffer.append(")");
            }
            if (repository.isBlocked()) {
                buffer.append(", blocked");
            }
            buffer.append(")");
            return idToPathSegment(repository) + "-" + StringDigestUtil.sha1(buffer.toString());
        }
    }

    /**
     * Returns same instance of (session cached) function for session.
     */
    @SuppressWarnings("unchecked")
    public static Function<ArtifactRepository, String> cachedIdToPathSegment(RepositorySystemSession session) {
        requireNonNull(session, "session");
        return (Function<ArtifactRepository, String>) session.getData()
                .computeIfAbsent(
                        RepositoryIdHelper.class.getSimpleName() + "-idToPathSegmentFunction",
                        () -> cachedIdToPathSegmentFunction(session));
    }

    /**
     * Returns new instance of function backed by cached or uncached (if session has no cache set)
     * {@link #idToPathSegment(ArtifactRepository)} method call.
     */
    @SuppressWarnings("unchecked")
    private static Function<ArtifactRepository, String> cachedIdToPathSegmentFunction(RepositorySystemSession session) {
        if (session.getCache() != null) {
            return repository -> ((ConcurrentHashMap<String, String>) session.getCache()
                            .computeIfAbsent(
                                    session,
                                    RepositoryIdHelper.class.getSimpleName() + "-idToPathSegmentCache",
                                    ConcurrentHashMap::new))
                    .computeIfAbsent(repository.getId(), id -> idToPathSegment(repository));
        } else {
            return RepositoryIdHelper::idToPathSegment; // uncached
        }
    }

    /**
     * This method returns the passed in {@link ArtifactRepository#getId()} value, modifying it if needed, making sure that
     * returned repository ID is "path segment" safe. Ideally, this method should never modify repository ID, as
     * Maven validation prevents use of illegal FS characters in them, but we found in Maven Central several POMs that
     * define remote repositories with illegal FS characters in their ID.
     * <p>
     * This method is simplistic on purpose, and if frequently used, best if results are cached (per session),
     * see {@link #cachedIdToPathSegment(RepositorySystemSession)} method.
     * <p>
     * This method is visible for testing only, should not be used in any other scenarios.
     *
     * @see #cachedIdToPathSegment(RepositorySystemSession)
     */
    static String idToPathSegment(ArtifactRepository repository) {
        if (repository instanceof RemoteRepository) {
            StringBuilder result = new StringBuilder(repository.getId());
            for (Map.Entry<String, String> entry : ILLEGAL_REPO_ID_REPLACEMENTS.entrySet()) {
                String illegal = entry.getKey();
                int pos = result.indexOf(illegal);
                while (pos >= 0) {
                    result.replace(pos, pos + illegal.length(), entry.getValue());
                    pos = result.indexOf(illegal);
                }
            }
            return result.toString();
        } else {
            return repository.getId();
        }
    }
}
