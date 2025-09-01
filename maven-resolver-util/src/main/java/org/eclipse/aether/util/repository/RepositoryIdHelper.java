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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.repository.RemoteRepository;

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
        HashMap<String, String> illegalReposIdReplacements = new HashMap<>();
        illegalReposIdReplacements.put("\\", "BACKSLASH");
        illegalReposIdReplacements.put("/", "SLASH");
        illegalReposIdReplacements.put(":", "COLON");
        illegalReposIdReplacements.put("\"", "QUOTE");
        illegalReposIdReplacements.put("<", "LT");
        illegalReposIdReplacements.put(">", "GT");
        illegalReposIdReplacements.put("|", "PIPE");
        illegalReposIdReplacements.put("?", "QMARK");
        illegalReposIdReplacements.put("*", "ASTERISK");
        ILLEGAL_REPO_ID_REPLACEMENTS = Collections.unmodifiableMap(illegalReposIdReplacements);
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
