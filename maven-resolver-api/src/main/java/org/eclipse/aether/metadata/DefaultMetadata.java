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
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * A basic metadata instance. <em>Note:</em> Instances of this class are immutable and the exposed mutators return new
 * objects rather than changing the current instance.
 */
public final class DefaultMetadata extends AbstractMetadata {

    private final String groupId;

    private final String artifactId;

    private final String version;

    private final String type;

    private final Nature nature;

    private final File file;

    private final Map<String, String> properties;

    /**
     * Creates a new metadata for the repository root with the specific type and nature.
     *
     * @param type The type of the metadata, e.g. "maven-metadata.xml", may be {@code null}.
     * @param nature The nature of the metadata, must not be {@code null}.
     */
    public DefaultMetadata(String type, Nature nature) {
        this("", "", "", type, nature, null, (File) null);
    }

    /**
     * Creates a new metadata for the groupId level with the specific type and nature.
     *
     * @param groupId The group identifier to which this metadata applies, may be {@code null}.
     * @param type The type of the metadata, e.g. "maven-metadata.xml", may be {@code null}.
     * @param nature The nature of the metadata, must not be {@code null}.
     */
    public DefaultMetadata(String groupId, String type, Nature nature) {
        this(groupId, "", "", type, nature, null, (File) null);
    }

    /**
     * Creates a new metadata for the groupId:artifactId level with the specific type and nature.
     *
     * @param groupId The group identifier to which this metadata applies, may be {@code null}.
     * @param artifactId The artifact identifier to which this metadata applies, may be {@code null}.
     * @param type The type of the metadata, e.g. "maven-metadata.xml", may be {@code null}.
     * @param nature The nature of the metadata, must not be {@code null}.
     */
    public DefaultMetadata(String groupId, String artifactId, String type, Nature nature) {
        this(groupId, artifactId, "", type, nature, null, (File) null);
    }

    /**
     * Creates a new metadata for the groupId:artifactId:version level with the specific type and nature.
     *
     * @param groupId The group identifier to which this metadata applies, may be {@code null}.
     * @param artifactId The artifact identifier to which this metadata applies, may be {@code null}.
     * @param version The version to which this metadata applies, may be {@code null}.
     * @param type The type of the metadata, e.g. "maven-metadata.xml", may be {@code null}.
     * @param nature The nature of the metadata, must not be {@code null}.
     */
    public DefaultMetadata(String groupId, String artifactId, String version, String type, Nature nature) {
        this(groupId, artifactId, version, type, nature, null, (File) null);
    }

    /**
     * Creates a new metadata for the groupId:artifactId:version level with the specific type and nature.
     *
     * @param groupId The group identifier to which this metadata applies, may be {@code null}.
     * @param artifactId The artifact identifier to which this metadata applies, may be {@code null}.
     * @param version The version to which this metadata applies, may be {@code null}.
     * @param type The type of the metadata, e.g. "maven-metadata.xml", may be {@code null}.
     * @param nature The nature of the metadata, must not be {@code null}.
     * @param file The resolved file of the metadata, may be {@code null}.
     */
    public DefaultMetadata(String groupId, String artifactId, String version, String type, Nature nature, File file) {
        this(groupId, artifactId, version, type, nature, null, file);
    }

    /**
     * Creates a new metadata for the groupId:artifactId:version level with the specific type and nature.
     *
     * @param groupId The group identifier to which this metadata applies, may be {@code null}.
     * @param artifactId The artifact identifier to which this metadata applies, may be {@code null}.
     * @param version The version to which this metadata applies, may be {@code null}.
     * @param type The type of the metadata, e.g. "maven-metadata.xml", may be {@code null}.
     * @param nature The nature of the metadata, must not be {@code null}.
     * @param properties The properties of the metadata, may be {@code null} if none.
     * @param file The resolved file of the metadata, may be {@code null}.
     */
    public DefaultMetadata(
            String groupId,
            String artifactId,
            String version,
            String type,
            Nature nature,
            Map<String, String> properties,
            File file) {
        this.groupId = emptify(groupId);
        this.artifactId = emptify(artifactId);
        this.version = emptify(version);
        this.type = emptify(type);
        this.nature = requireNonNull(nature, "metadata nature cannot be null");
        this.file = file;
        this.properties = copyProperties(properties);
    }

    DefaultMetadata(
            String groupId,
            String artifactId,
            String version,
            String type,
            Nature nature,
            File file,
            Map<String, String> properties) {
        // NOTE: This constructor assumes immutability of the provided properties, for internal use only
        this.groupId = emptify(groupId);
        this.artifactId = emptify(artifactId);
        this.version = emptify(version);
        this.type = emptify(type);
        this.nature = nature;
        this.file = file;
        this.properties = properties;
    }

    private static String emptify(String str) {
        return (str == null) ? "" : str;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }

    public String getType() {
        return type;
    }

    public Nature getNature() {
        return nature;
    }

    public File getFile() {
        return file;
    }

    public Map<String, String> getProperties() {
        return properties;
    }
}
