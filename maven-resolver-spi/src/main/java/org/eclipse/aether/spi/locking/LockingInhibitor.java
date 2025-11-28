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
package org.eclipse.aether.spi.locking;

import java.util.Optional;
import java.util.function.Predicate;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;

/**
 * Locking inhibitor may prevent Resolver locking to happen on certain resources.
 *
 * @since 2.0.14
 */
public interface LockingInhibitor {
    /**
     * May provide a predicate for artifacts that needs lock inhibition. <em>Warning: you do not want to override
     * this method, or if you do, think twice.</em>
     */
    default Optional<Predicate<Artifact>> inhibitArtifactLocking() {
        return Optional.empty();
    }

    /**
     * May provide a predicate for metadata that needs lock inhibition.
     */
    default Optional<Predicate<Metadata>> inhibitMetadataLocking() {
        return Optional.empty();
    }
}
