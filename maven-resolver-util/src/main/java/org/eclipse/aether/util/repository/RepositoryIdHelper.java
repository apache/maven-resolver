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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.repository.RemoteRepository;

import static java.util.Objects.requireNonNull;

/**
 * Helper class for {@link ArtifactRepository#getId()} handling. This class provides helper function (cached or uncached)
 * to get {@link RemoteRepository#getId()} as it was originally envisioned: as path safe. While POMs are validated
 * by Maven, there are POMs out there that somehow define repositories with unsafe characters in their id.
 *
 * @since 2.0.11
 */
public final class RepositoryIdHelper {
    private RepositoryIdHelper() {}

    private static final List<String> ILLEGAL_REPO_ID_CHARS = Collections.unmodifiableList(
            Arrays.asList("\\", "/", ":", "\"", "<", ">", "|", "?", "*")); // copied from Maven
    private static final List<String> REPLACEMENT_REPO_ID_CHARS =
            Collections.unmodifiableList(Arrays.asList("X", "X", "X", "X", "X", "X", "X", "X", "X"));

    /**
     * Provides cached (or uncached, if session has no cache set) for {@link #idToPathSegment(RemoteRepository)} function.
     */
    @SuppressWarnings("unchecked")
    public static Function<RemoteRepository, String> cachedIdToPathSegment(RepositorySystemSession session) {
        requireNonNull(session, "session");
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
        for (int illegalIndex = 0; illegalIndex < ILLEGAL_REPO_ID_CHARS.size(); illegalIndex++) {
            String illegal = ILLEGAL_REPO_ID_CHARS.get(illegalIndex);
            int pos = result.indexOf(illegal);
            while (pos >= 0) {
                result.replace(pos, pos + illegal.length(), REPLACEMENT_REPO_ID_CHARS.get(illegalIndex));
                pos = result.indexOf(illegal);
            }
        }
        return result.toString();
    }
}
