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
package org.eclipse.aether.artifact;

import java.util.Map;

/**
 * An artifact type describing artifact characteristics/properties that are common for certain artifacts. Artifact types
 * are a means to simplify the description of an artifact by referring to an artifact type instead of specifying the
 * various properties individually.
 *
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 * @see ArtifactTypeRegistry
 * @see DefaultArtifact#DefaultArtifact(String, String, String, String, String, ArtifactType)
 */
public interface ArtifactType {

    /**
     * Gets the identifier of this type, e.g. "maven-plugin" or "test-jar".
     *
     * @return The identifier of this type, never {@code null}.
     * @see ArtifactProperties#TYPE
     */
    String getId();

    /**
     * Gets the file extension to use for artifacts of this type (unless explicitly overridden by the artifact).
     *
     * @return The usual file extension, never {@code null}.
     */
    String getExtension();

    /**
     * Gets the classifier to use for artifacts of this type (unless explicitly overridden by the artifact).
     *
     * @return The usual classifier or an empty string if none, never {@code null}.
     */
    String getClassifier();

    /**
     * Gets the properties to use for artifacts of this type (unless explicitly overridden by the artifact).
     *
     * @return The (read-only) properties, never {@code null}.
     * @see ArtifactProperties
     */
    Map<String, String> getProperties();
}
