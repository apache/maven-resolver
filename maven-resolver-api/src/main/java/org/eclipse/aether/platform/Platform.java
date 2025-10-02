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
package org.eclipse.aether.platform;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.DependencyManagementKey;
import org.eclipse.aether.graph.Exclusion;

/**
 * A platform definition.
 * <p>
 * It may manage several aspects of dependencies, except the "system scope".
 */
public interface Platform {
    /**
     * Platform mode of operation that defines how management rules are applied but also what happens in case of
     * "platform conflicts".
     *
     * @see #managementKey()
     */
    enum Mode {
        /**
         * In this mode platform "only" makes sure that platform artifacts are aligned to one single selected platform.
         * There may be several advising platform present for same management key, and they will be subject to platform
         * mediation, and only one will "win". In this mode:
         * <ul>
         *     <li>dependency properties are applied as "not enforced"</li>
         *     <li>provided checksums are let to be processed according to session checksum policy</li>
         * </ul>
         */
        ALIGNING,
        /**
         * In this mode platform aside of "aligning", also makes management strict, enforcing that any managed property
         * do not fall outside of platform defined versions. There can be only one enforced platform present for one
         * management key. In case of two enforced but conflicting platforms collection will fail, in other cases
         * otherwise always the enforced one is selected. In this mode:
         * <ul>
         *     <li>dependency properties are applied as "enforced"</li>
         *     <li>provided checksums are processed strictly (fail the build if not matching)</li>
         * </ul>
         */
        ENFORCED
    }

    /**
     * The platform representing (existing or not-existing/virtual) artifact.
     */
    Artifact artifact();

    /**
     * The platform management key. If multiple platform share same key, they are "conflicting".
     */
    DependencyManagementKey managementKey();

    /**
     * The platform name (for human consumption, like logging purposes).
     */
    String getName();

    /**
     * The operating mode of platform.
     */
    Mode getMode();

    /**
     * Returns {@code true} if this platform manages given artifact.
     */
    boolean contains(Artifact artifact);

    /**
     * Platform management for version.
     */
    default Optional<String> getManagedVersion(Artifact artifact) {
        return Optional.empty();
    }

    /**
     * Platform management for scope.
     */
    default Optional<String> getManagedScope(Artifact artifact) {
        return Optional.empty();
    }

    /**
     * Platform management for optional.
     */
    default Optional<Boolean> getManagedOptional(Artifact artifact) {
        return Optional.empty();
    }

    /**
     * Platform management for exclusions.
     */
    default Optional<Collection<Exclusion>> getManagedExclusions(Artifact artifact) {
        return Optional.empty();
    }

    /**
     * Platform management for checksums.
     */
    default Optional<Map<String, String>> getManagedChecksums(Artifact artifact) {
        return Optional.empty();
    }

    /**
     * Platform management for relocations.
     */
    default Optional<Artifact> getManagedRelocation(Artifact artifact) {
        return Optional.empty();
    }

    // repositories?
}
