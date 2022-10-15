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
import org.eclipse.aether.artifact.ArtifactTypeRegistry;

/**
 * An artifact type registry which first consults its own mappings and in case of an unknown type falls back to another
 * type registry.
 */
public final class OverlayArtifactTypeRegistry extends SimpleArtifactTypeRegistry {

    private final ArtifactTypeRegistry delegate;

    /**
     * Creates a new artifact type registry with initially no registered artifact types and the specified fallback
     * registry. Use {@link #add(ArtifactType)} to populate the registry.
     *
     * @param delegate The artifact type registry to fall back to, may be {@code null}.
     */
    public OverlayArtifactTypeRegistry(ArtifactTypeRegistry delegate) {
        this.delegate = delegate;
    }

    /**
     * Adds the specified artifact type to the registry.
     *
     * @param type The artifact type to add, must not be {@code null}.
     * @return This registry for chaining, never {@code null}.
     */
    public OverlayArtifactTypeRegistry add(ArtifactType type) {
        super.add(type);
        return this;
    }

    public ArtifactType get(String typeId) {
        ArtifactType type = super.get(typeId);

        if (type == null && delegate != null) {
            type = delegate.get(typeId);
        }

        return type;
    }
}
