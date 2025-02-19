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
package org.eclipse.aether.metadata;

import java.io.File;
import java.nio.file.Path;

import org.eclipse.aether.RepositoryException;

/**
 * A piece of metadata that needs to be merged with any current metadata before installation/deployment.
 */
public interface MergeableMetadata extends Metadata {

    /**
     * Merges this metadata into the current metadata (if any). Note that this method will be invoked regardless whether
     * metadata currently exists or not.
     *
     * @param current The path to the current metadata file, may not exist but must not be {@code null}.
     * @param result The path to the result file where the merged metadata should be stored, must not be {@code null}.
     * @throws RepositoryException If the metadata could not be merged.
     * @deprecated Use {@link #merge(Path, Path)} instead.
     */
    @Deprecated
    void merge(File current, File result) throws RepositoryException;

    /**
     * Merges this metadata into the current metadata (if any). Note that this method will be invoked regardless whether
     * metadata currently exists or not.
     *
     * @param current The path to the current metadata file, may not exist but must not be {@code null}.
     * @param result The path to the result file where the merged metadata should be stored, must not be {@code null}.
     * @throws RepositoryException If the metadata could not be merged.
     * @since 2.0.0
     */
    default void merge(Path current, Path result) throws RepositoryException {
        merge(current.toFile(), result.toFile());
    }

    /**
     * Indicates whether this metadata has been merged.
     *
     * @return {@code true} if the metadata has been merged, {@code false} otherwise.
     */
    boolean isMerged();
}
