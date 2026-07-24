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
package org.eclipse.aether.spi.validator;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * A repository system main input validator; this validator is used in repository system "main entry methods".
 *
 * @since 2.0.8
 */
public interface Validator {
    /**
     * Validates artifact.
     *
     * @param artifact the artifact to validate, never {@code null}.
     * @throws IllegalArgumentException if artifact is invalid.
     */
    default void validateArtifact(Artifact artifact) throws IllegalArgumentException {}

    /**
     * Validates metadata.
     *
     * @param metadata the metadata to validate, never {@code null}.
     * @throws IllegalArgumentException if metadata is invalid.
     */
    default void validateMetadata(Metadata metadata) throws IllegalArgumentException {}

    /**
     * Validates dependency.
     *
     * @param dependency the dependency to validate, never {@code null}.
     * @throws IllegalArgumentException if dependency is invalid.
     */
    default void validateDependency(Dependency dependency) throws IllegalArgumentException {}

    /**
     * Validates managed dependency.
     * <em>Important:</em> They are declarative constraints (version/scope/exclusion overrides) that only take effect
     * when a matching dependency is encountered during collection. Validating them eagerly may reject valid builds
     * where a BOM imports managed dependencies with uninterpolated property expressions (e.g. {@code ${osgi.version}})
     * that are never actually used. If a managed dependency IS matched and its coordinates are invalid, the error
     * will surface naturally during version resolution or artifact resolution.
     *
     * @param managedDependency the managed dependency to validate, never {@code null}.
     * @throws IllegalArgumentException if dependency is invalid.
     * @since 2.0.22
     */
    default void validateManagedDependency(Dependency managedDependency) throws IllegalArgumentException {}

    /**
     * Validates local repository.
     *
     * @param localRepository the local repository to validate, never {@code null}.
     * @throws IllegalArgumentException if local repository is invalid.
     */
    default void validateLocalRepository(LocalRepository localRepository) throws IllegalArgumentException {}

    /**
     * Validates remote repository.
     *
     * @param remoteRepository the remote repository to validate, never {@code null}.
     * @throws IllegalArgumentException if remote repository is invalid.
     */
    default void validateRemoteRepository(RemoteRepository remoteRepository) throws IllegalArgumentException {}
}
