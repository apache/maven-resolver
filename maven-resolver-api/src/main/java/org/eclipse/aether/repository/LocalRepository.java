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
package org.eclipse.aether.repository;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * A repository on the local file system used to cache contents of remote repositories and to store locally installed
 * artifacts. Note that this class merely describes such a repository, actual access to the contained artifacts is
 * handled by a {@link LocalRepositoryManager} which is usually determined from the {@link #getContentType() type} of
 * the repository.
 */
public final class LocalRepository implements ArtifactRepository {

    private final Path basePath;

    private final String type;

    /**
     * Creates a new local repository with the specified base directory and unknown type.
     *
     * @param basedir The base directory of the repository, may be {@code null}.
     */
    public LocalRepository(String basedir) {
        this.basePath = Paths.get(RepositoryUriUtils.toUri(basedir)).toAbsolutePath();
        this.type = "";
    }

    /**
     * Creates a new local repository with the specified base directory and unknown type.
     *
     * @param basedir The base directory of the repository, may be {@code null}.
     * @since 2.0.0
     */
    public LocalRepository(URI basedir) {
        this((basedir != null) ? Paths.get(basedir) : null, "");
    }

    /**
     * Creates a new local repository with the specified base directory and unknown type.
     *
     * @param basedir The base directory of the repository, may be {@code null}.
     * @deprecated Use {@link #LocalRepository(Path)} instead.
     */
    @Deprecated
    public LocalRepository(File basedir) {
        this(basedir, "");
    }

    /**
     * Creates a new local repository with the specified base directory and unknown type.
     *
     * @param basePath The base directory of the repository, may be {@code null}.
     * @since 2.0.0
     */
    public LocalRepository(Path basePath) {
        this(basePath, "");
    }

    /**
     * Creates a new local repository with the specified properties.
     *
     * @param basedir The base directory of the repository, may be {@code null}.
     * @param type The type of the repository, may be {@code null}.
     * @deprecated Use {@link #LocalRepository(Path, String)} instead.
     */
    @Deprecated
    public LocalRepository(File basedir, String type) {
        this(basedir != null ? basedir.toPath() : null, type);
    }

    /**
     * Creates a new local repository with the specified properties.
     *
     * @param basePath The base directory of the repository, may be {@code null}.
     * @param type The type of the repository, may be {@code null}.
     * @since 2.0.0
     */
    public LocalRepository(Path basePath, String type) {
        this.basePath = basePath;
        this.type = (type != null) ? type : "";
    }

    @Override
    public String getContentType() {
        return type;
    }

    @Override
    public String getId() {
        return "local";
    }

    /**
     * Gets the base directory of the repository.
     *
     * @return The base directory or {@code null} if none.
     * @deprecated Use {@link #getBasePath()} instead.
     */
    @Deprecated
    public File getBasedir() {
        return basePath != null ? basePath.toFile() : null;
    }

    /**
     * Gets the base directory of the repository.
     *
     * @return The base directory or {@code null} if none.
     * @since 2.0.0
     */
    public Path getBasePath() {
        return basePath;
    }

    @Override
    public String toString() {
        return getBasePath() + " (" + getContentType() + ")";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || !getClass().equals(obj.getClass())) {
            return false;
        }

        LocalRepository that = (LocalRepository) obj;

        return Objects.equals(basePath, that.basePath) && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        int hash = 17;
        hash = hash * 31 + hash(basePath);
        hash = hash * 31 + hash(type);
        return hash;
    }

    private static int hash(Object obj) {
        return obj != null ? obj.hashCode() : 0;
    }
}
