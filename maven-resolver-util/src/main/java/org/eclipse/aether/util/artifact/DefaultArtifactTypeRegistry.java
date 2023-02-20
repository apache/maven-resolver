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
package org.eclipse.aether.util.artifact;

import org.eclipse.aether.artifact.ArtifactType;

/**
 * A simple artifact type registry.
 */
public final class DefaultArtifactTypeRegistry extends SimpleArtifactTypeRegistry {

    /**
     * Creates a new artifact type registry with initally no registered artifact types. Use {@link #add(ArtifactType)}
     * to populate the registry.
     */
    public DefaultArtifactTypeRegistry() {}

    /**
     * Adds the specified artifact type to the registry.
     *
     * @param type The artifact type to add, must not be {@code null}.
     * @return This registry for chaining, never {@code null}.
     */
    public DefaultArtifactTypeRegistry add(ArtifactType type) {
        super.add(type);
        return this;
    }
}
