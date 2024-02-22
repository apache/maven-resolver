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
package org.eclipse.aether.internal.impl.synccontext.named;

import java.util.Collection;
import java.util.stream.Collectors;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.named.NamedLockKey;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.StringDigestUtil;

import static java.util.Objects.requireNonNull;

/**
 * Wrapping {@link NameMapper}, that wraps another {@link NameMapper} and hashes resulting strings. It makes use of
 * fact that (proper) Hash will create unique fixed length string for each different input string (so injection still
 * stands). This mapper produces file system friendly names. Supports different "depths" (0-4 inclusive) where the
 * name will contain 0 to 4 level deep directories.
 * <p>
 * This mapper is usable in any scenario, but intent was to produce more "compact" name mapper for file locking.
 *
 * @since 1.9.0
 */
public class HashingNameMapper implements NameMapper {
    /**
     * The depth how many levels should adapter create. Acceptable values are 0-4 (inclusive).
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Integer}
     * @configurationDefaultValue {@link #DEFAULT_DEPTH}
     */
    public static final String CONFIG_PROP_DEPTH = NamedLockFactoryAdapter.CONFIG_PROPS_PREFIX + "hashing.depth";

    public static final int DEFAULT_DEPTH = 2;

    private final NameMapper delegate;

    public HashingNameMapper(final NameMapper delegate) {
        this.delegate = requireNonNull(delegate);
    }

    @Override
    public boolean isFileSystemFriendly() {
        return true; // hashes delegated strings, so whatever it wrapped, it does not come through
    }

    @Override
    public Collection<NamedLockKey> nameLocks(
            RepositorySystemSession session,
            Collection<? extends Artifact> artifacts,
            Collection<? extends Metadata> metadatas) {
        final int depth = ConfigUtils.getInteger(session, DEFAULT_DEPTH, CONFIG_PROP_DEPTH);
        if (depth < 0 || depth > 4) {
            throw new IllegalArgumentException("allowed depth value is between 0 and 4 (inclusive)");
        }
        return delegate.nameLocks(session, artifacts, metadatas).stream()
                .map(k -> NamedLockKey.of(hashName(k.name(), depth), k.resources()))
                .collect(Collectors.toList());
    }

    private String hashName(final String name, final int depth) {
        String hashedName = StringDigestUtil.sha1(name);
        if (depth == 0) {
            return hashedName;
        }
        StringBuilder prefix = new StringBuilder();
        int i = 0;
        while (i < hashedName.length() && i / 2 < depth) {
            prefix.append(hashedName, i, i + 2).append("/");
            i += 2;
        }
        return prefix.append(hashedName).toString();
    }
}
