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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
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
     * Supported {@code repositoryKey} types.
     *
     * @since 2.0.14
     */
    public enum RepositoryKeyType {
        /**
         * The "simple" repository key, was default in Maven 3.
         */
        SIMPLE,
        /**
         * Crafts repository key using normalized {@link RemoteRepository#getId()}.
         */
        NID,
        /**
         * Crafts repository key using hashed {@link RemoteRepository#getUrl()}.
         */
        HURL,
        /**
         * Crafts unique repository key using normalized {@link RemoteRepository#getId()} and hashed {@link RemoteRepository#getUrl()}.
         */
        NID_HURL,
        /**
         * Crafts normalized unique repository key using {@link RemoteRepository#getId()} and all the remaining properties of
         * {@link RemoteRepository} ignoring actual list of mirrors, if any (but mirrors are split).
         */
        NGURK,
        /**
         * Crafts unique repository key using {@link RemoteRepository#getId()} and all the remaining properties of
         * {@link RemoteRepository}.
         */
        GURK
    }

    /**
     * Selector method for {@link RepositoryKeyFunction} based on string representation of {@link RepositoryKeyType}
     * enum.
     */
    public static RepositoryKeyFunction getRepositoryKeyFunction(String keyTypeString) {
        RepositoryKeyType keyType = RepositoryKeyType.valueOf(keyTypeString.toUpperCase(Locale.ENGLISH));
        switch (keyType) {
            case SIMPLE:
                return RepositoryIdHelper::simpleRepositoryKey;
            case NID:
                return RepositoryIdHelper::nidRepositoryKey;
            case HURL:
                return RepositoryIdHelper::hurlRepositoryKey;
            case NID_HURL:
                return RepositoryIdHelper::nidAndHurlRepositoryKey;
            case NGURK:
                return RepositoryIdHelper::normalizedGloballyUniqueRepositoryKey;
            case GURK:
                return RepositoryIdHelper::globallyUniqueRepositoryKey;
            default:
                throw new IllegalArgumentException("Unknown repository key type: " + keyType.name());
        }
    }

    /**
     * Simple {@code repositoryKey} function (classic). Returns {@link RemoteRepository#getId()}, unless
     * {@link RemoteRepository#isRepositoryManager()} returns {@code true}, in which case this method creates
     * unique identifier based on ID and current configuration of the remote repository and context.
     * <p>
     * This was the default {@code repositoryKey} method in Maven 3. Is exposed (others key methods are private) as
     * it is directly used by "simple" LRM.
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
     * The ID {@code repositoryKey} function that uses only the {@link RemoteRepository#getId()} value to derive a key.
     *
     * @since 2.0.14
     **/
    private static String nidRepositoryKey(RemoteRepository repository, String context) {
        String seed = null;
        if (repository.isRepositoryManager() && context != null && !context.isEmpty()) {
            seed += context;
        }
        return idToPathSegment(repository) + (seed == null ? "" : "-" + StringDigestUtil.sha1(seed));
    }

    /**
     * The URL {@code repositoryKey} function that uses only the {@link RemoteRepository#getUrl()} hash to derive a key.
     *
     * @since 2.0.14
     **/
    private static String hurlRepositoryKey(RemoteRepository repository, String context) {
        String seed = null;
        if (repository.isRepositoryManager() && context != null && !context.isEmpty()) {
            seed += context;
        }
        return StringDigestUtil.sha1(repository.getUrl()) + (seed == null ? "" : "-" + StringDigestUtil.sha1(seed));
    }

    /**
     * The ID and URL {@code repositoryKey} function. This method creates unique identifier based on ID and URL
     * of the remote repository.
     *
     * @since 2.0.14
     **/
    private static String nidAndHurlRepositoryKey(RemoteRepository repository, String context) {
        String seed = repository.getUrl();
        if (repository.isRepositoryManager() && context != null && !context.isEmpty()) {
            seed += context;
        }
        return idToPathSegment(repository) + "-" + StringDigestUtil.sha1(seed);
    }

    /**
     * Normalized globally unique {@code repositoryKey} function. This method creates unique identifier based on ID and current
     * configuration of the remote repository ignoring mirrors (it records the fact repository is a mirror, but ignores
     * mirrored repositories). If {@link RemoteRepository#isRepositoryManager()} returns {@code true}, the passed in
     * {@code context} string is factored in as well.
     *
     * @since 2.0.14
     **/
    private static String normalizedGloballyUniqueRepositoryKey(RemoteRepository repository, String context) {
        String seed = remoteRepositoryDescription(repository, false);
        if (repository.isRepositoryManager() && context != null && !context.isEmpty()) {
            seed += context;
        }
        return idToPathSegment(repository) + "-" + StringDigestUtil.sha1(seed);
    }

    /**
     * Globally unique {@code repositoryKey} function. This method creates unique identifier based on ID and current
     * configuration of the remote repository. If {@link RemoteRepository#isRepositoryManager()} returns {@code true},
     * the passed in {@code context} string is factored in as well.
     * <p>
     * <em>Important:</em> this repository key can be considered "stable" for normal remote repositories (where only
     * ID and URL matters). But, for mirror repositories, the key will change if mirror members change.
     *
     * @since 2.0.14
     **/
    private static String globallyUniqueRepositoryKey(RemoteRepository repository, String context) {
        String seed = remoteRepositoryDescription(repository, true);
        if (repository.isRepositoryManager() && context != null && !context.isEmpty()) {
            seed += context;
        }
        return idToPathSegment(repository) + "-" + StringDigestUtil.sha1(seed);
    }

    /**
     * This method returns the passed in {@link ArtifactRepository#getId()} value, modifying it if needed, making sure that
     * returned repository ID is "path segment" safe. Ideally, this method should never modify repository ID, as
     * Maven validation prevents use of illegal FS characters in them, but we found in Maven Central several POMs that
     * define remote repositories with illegal FS characters in their ID.
     */
    private static String idToPathSegment(ArtifactRepository repository) {
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
    private static String remoteRepositoryDescription(RemoteRepository repository, boolean mirrorDetails) {
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
            if (mirrorDetails) {
                // sort them to make it stable ordering
                ArrayList<RemoteRepository> mirroredRepositories =
                        new ArrayList<>(repository.getMirroredRepositories());
                mirroredRepositories.sort(Comparator.comparing(RemoteRepository::getId));
                buffer.append(", mirrorOf(");
                for (RemoteRepository mirroredRepo : mirroredRepositories) {
                    buffer.append(remoteRepositoryDescription(mirroredRepo, true));
                }
                buffer.append(")");
            } else {
                buffer.append(", isMirror");
            }
        }
        if (repository.isBlocked()) {
            buffer.append(", blocked");
        }
        buffer.append(")");
        return buffer.toString();
    }
}
