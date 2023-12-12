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
package org.eclipse.aether.spi.relocation;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;

/**
 * Artifact Relocation Source component.
 *
 * @since 2.0.0
 */
public interface ArtifactRelocationSource {
    /**
     * The priority of this source. When multiple sources exist, sources are consulted in order from those with higher
     * priority to those with lower priority.
     *
     * @return The priority of this source.
     */
    float getPriority();

    /**
     * Receives {@link Artifact} just before it is about to have processed for dependency collection and may return
     * alternative {@link Artifact} instance causing "relocation" or same instance, in which case nothing happens.
     *
     * @param session  The session, never {@code null}.
     * @param artifact The artifact that is being collected, never {@code null}.
     * @return artifact to relocate, or {@code null}.
     */
    Artifact relocatedTarget(RepositorySystemSession session, Artifact artifact);
}
