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

import java.util.SortedSet;
import java.util.TreeSet;

import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.PathUtils;
import org.eclipse.aether.util.StringDigestUtil;

/**
 * Helper class for {@link ArtifactRepository#getId()} handling. This class provides  helper methods
 * to get id of repository as it was originally envisioned: as path safe, unique, etc. While POMs are validated by Maven,
 * there are POMs out there that somehow define repositories with unsafe characters in their id. The problem affects mostly
 * {@link RemoteRepository} instances, as all other implementations have fixed ids that are path safe.
 * <p>
 * <em>Important:</em> multiple of these provided methods are not trivial processing-wise, and some sort of
 * caching is warmly recommended.
 *
 * @see PathUtils
 * @since 2.0.11
 */
public final class RepositoryIdHelper {
    private RepositoryIdHelper() {}

    /**
     * Simple {@code repositoryKey} function (classic). Returns {@link RemoteRepository#getId()}, unless
     * {@link RemoteRepository#isRepositoryManager()} returns {@code true}, in which case this method creates
     * unique identifier based on ID and current configuration of the remote repository and context.
     * <p>
     * This was the default {@code repositoryKey} method in Maven 3.
     *
     * @since 2.0.14
     **/
    public static String simpleRepositoryKey(RemoteRepository repository, String context) {
        if (repository.isRepositoryManager()) {
            StringBuilder buffer = new StringBuilder(128);
            buffer.append(idToPathSegment(repository));
            buffer.append('-');
            SortedSet<String> subKeys = new TreeSet<>();
            for (RemoteRepository mirroredRepo : repository.getMirroredRepositories()) {
                subKeys.add(mirroredRepo.getId());
            }
            StringDigestUtil sha1 = StringDigestUtil.sha1();
            sha1.update(context);
            for (String subKey : subKeys) {
                sha1.update(subKey);
            }
            buffer.append(sha1.digest());
            return buffer.toString();
        } else {
            return idToPathSegment(repository);
        }
    }

    /**
     * Globally unique {@code repositoryKey} function. This method creates unique identifier based on ID and current
     * configuration of the remote repository. If {@link RemoteRepository#isRepositoryManager()} returns {@code true},
     * the passed in {@code context} string is factored in as well. This repository key method returns same results as
     * {@link #remoteRepositoryUniqueId(RemoteRepository)} if parameter context is {@code null} or empty string.
     * <p>
     * <em>Important:</em> this repository key can be considered "stable" for normal remote repositories (where only
     * ID and URL matters). But, for mirror repositories, the key will change if mirror members change.
     * TODO: reconsider this?
     *
     * @see #remoteRepositoryUniqueId(RemoteRepository)
     * @since 2.0.14
     **/
    public static String globallyUniqueRepositoryKey(RemoteRepository repository, String context) {
        String description = remoteRepositoryDescription(repository);
        if (repository.isRepositoryManager() && context != null && !context.isEmpty()) {
            description += context;
        }
        return idToPathSegment(repository) + "-" + StringDigestUtil.sha1(description);
    }

    /**
     * Creates unique repository id for given {@link RemoteRepository}.
     * For any remote repository it will return string created as {@code $(repository.id)-sha1(repository-aspects)}.
     * The key material contains all relevant aspects of remote repository, so repository with same ID even if just
     * policy changes (enabled/disabled), will map to different string id. The checksum and update policies are not
     * participating in key creation.
     * <p>
     * This method is costly, so should be invoked sparingly, or cache results if needed.
     * <p>
     * <em>Important:</em>Do not use this method, or at least <em>do consider when do you want to use it</em>, as it
     * totally disconnects repositories used in session. This method may be used under some special circumstances
     * (ie reporting), but <em>must not be used within Resolver (and Maven) session for "usual" resolution and
     * deployment use cases</em>.
     */
    public static String remoteRepositoryUniqueId(RemoteRepository repository) {
        return idToPathSegment(repository) + "-" + StringDigestUtil.sha1(remoteRepositoryDescription(repository));
    }

    /**
     * This method returns the passed in {@link ArtifactRepository#getId()} value, modifying it if needed, making sure that
     * returned repository ID is "path segment" safe. Ideally, this method should never modify repository ID, as
     * Maven validation prevents use of illegal FS characters in them, but we found in Maven Central several POMs that
     * define remote repositories with illegal FS characters in their ID.
     */
    public static String idToPathSegment(ArtifactRepository repository) {
        if (repository instanceof RemoteRepository) {
            return PathUtils.stringToPathSegment(repository.getId());
        } else {
            return repository.getId();
        }
    }

    /**
     * Creates unique string for given {@link RemoteRepository}. Ignores following properties:
     * <ul>
     *     <li>{@link RemoteRepository#getAuthentication()}</li>
     *     <li>{@link RemoteRepository#getProxy()}</li>
     *     <li>{@link RemoteRepository#getIntent()}</li>
     * </ul>
     */
    public static String remoteRepositoryDescription(RemoteRepository repository) {
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
            buffer.append(", managed");
        }
        if (!repository.getMirroredRepositories().isEmpty()) {
            buffer.append(", mirrorOf(");
            for (RemoteRepository mirroredRepo : repository.getMirroredRepositories()) {
                buffer.append(remoteRepositoryDescription(mirroredRepo));
            }
            buffer.append(")");
        }
        if (repository.isBlocked()) {
            buffer.append(", blocked");
        }
        buffer.append(")");
        return buffer.toString();
    }
}
