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

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A simple artifact. <em>Note:</em> Instances of this class are immutable and the exposed mutators return new objects
 * rather than changing the current instance.
 */
public final class DefaultArtifact extends AbstractArtifact {
    private static final Pattern COORDINATE_PATTERN =
            Pattern.compile("([^: ]+):([^: ]+)(:([^: ]*)(:([^: ]+))?)?:([^: ]+)");

    private final String groupId;

    private final String artifactId;

    private final String version;

    private final String classifier;

    private final String extension;

    private final Path path;

    private final Map<String, String> properties;

    /**
     * Creates a new artifact with the specified coordinates. If not specified in the artifact coordinates, the
     * artifact's extension defaults to {@code jar} and classifier to an empty string.
     *
     * @param coords The artifact coordinates in the format
     *            {@code <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>}, must not be {@code null}.
     *
     * @throws IllegalArgumentException If the artifact coordinates found in {@code coords} do not match the expected
     * format.
     */
    public DefaultArtifact(String coords) {
        this(coords, null, null);
    }

    /**
     * Creates a new artifact with the specified coordinates and properties. If not specified in the artifact
     * coordinates, the artifact's extension defaults to {@code jar} and classifier to an empty string.
     *
     * @param coords The artifact coordinates in the format
     *            {@code <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>}, must not be {@code null}.
     * @param properties The artifact properties, may be {@code null}.
     *
     * @throws IllegalArgumentException If the artifact coordinates found in {@code coords} do not match the expected
     * format.
     */
    public DefaultArtifact(String coords, Map<String, String> properties) {
        this(coords, properties, null);
    }

    /**
     * Creates a new artifact with the specified coordinates and type. If not specified in the artifact coordinates,
     * the artifact's extension defaults to type extension (or "jar" if type is {@code null}) and
     * classifier to type extension (or "" if type is {@code null}).
     *
     * @param coords The artifact coordinates in the format
     *            {@code <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>}, must not be {@code null}.
     * @param type The artifact type, may be {@code null}.
     *
     * @throws IllegalArgumentException If the artifact coordinates found in {@code coords} do not match the expected
     * format.
     */
    public DefaultArtifact(String coords, ArtifactType type) {
        this(coords, null, type);
    }

    /**
     * Creates a new artifact with the specified coordinates, properties and type. If not specified in the artifact
     * coordinates, the artifact's extension defaults to type extension (or "jar" if type is {@code null}) and
     * classifier to type extension (or "" if type is {@code null}).
     *
     * @param coords The artifact coordinates in the format
     *            {@code <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>}, must not be {@code null}.
     * @param properties The artifact properties, may be {@code null}.
     * @param type The artifact type, may be {@code null}.
     *
     * @throws IllegalArgumentException If the artifact coordinates found in {@code coords} do not match the expected
     * format.
     */
    public DefaultArtifact(String coords, Map<String, String> properties, ArtifactType type) {
        Matcher m = COORDINATE_PATTERN.matcher(coords);
        if (!m.matches()) {
            throw new IllegalArgumentException("Bad artifact coordinates " + coords
                    + ", expected format is <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>");
        }
        groupId = m.group(1);
        artifactId = m.group(2);
        extension = get(m.group(4), type == null ? "jar" : type.getExtension());
        classifier = get(m.group(6), type == null ? "" : type.getClassifier());
        this.version = emptify(m.group(7));
        this.path = null;
        this.properties = mergeArtifactProperties(properties, (type != null) ? type.getProperties() : null);
    }

    private static String get(String value, String defaultValue) {
        return (value == null || value.isEmpty()) ? defaultValue : value;
    }

    /**
     * Creates a new artifact with the specified coordinates and no classifier. Passing {@code null} for any of the
     * coordinates is equivalent to specifying an empty string.
     *
     * @param groupId The group identifier of the artifact, may be {@code null}.
     * @param artifactId The artifact identifier of the artifact, may be {@code null}.
     * @param extension The file extension of the artifact, may be {@code null}.
     * @param version The version (may also be a meta version or version range) of the artifact, may be {@code null}.
     */
    public DefaultArtifact(String groupId, String artifactId, String extension, String version) {
        this(groupId, artifactId, "", extension, version);
    }

    /**
     * Creates a new artifact with the specified coordinates. Passing {@code null} for any of the coordinates is
     * equivalent to specifying an empty string.
     *
     * @param groupId The group identifier of the artifact, may be {@code null}.
     * @param artifactId The artifact identifier of the artifact, may be {@code null}.
     * @param classifier The classifier of the artifact, may be {@code null}.
     * @param extension The file extension of the artifact, may be {@code null}.
     * @param version The version (may also be a meta version or version range) of the artifact, may be {@code null}.
     */
    public DefaultArtifact(String groupId, String artifactId, String classifier, String extension, String version) {
        this(groupId, artifactId, classifier, extension, version, null, (File) null);
    }

    /**
     * Creates a new artifact with the specified coordinates. Passing {@code null} for any of the coordinates is
     * equivalent to specifying an empty string. The optional artifact type provided to this constructor will be used to
     * determine the artifact's classifier and file extension if the corresponding arguments for this constructor are
     * {@code null}.
     *
     * @param groupId The group identifier of the artifact, may be {@code null}.
     * @param artifactId The artifact identifier of the artifact, may be {@code null}.
     * @param classifier The classifier of the artifact, may be {@code null}.
     * @param extension The file extension of the artifact, may be {@code null}.
     * @param version The version (may also be a meta version or version range) of the artifact, may be {@code null}.
     * @param type The artifact type from which to query classifier, file extension and properties, may be {@code null}.
     */
    public DefaultArtifact(
            String groupId, String artifactId, String classifier, String extension, String version, ArtifactType type) {
        this(groupId, artifactId, classifier, extension, version, null, type);
    }

    /**
     * Creates a new artifact with the specified coordinates and properties. Passing {@code null} for any of the
     * coordinates is equivalent to specifying an empty string. The optional artifact type provided to this constructor
     * will be used to determine the artifact's classifier and file extension if the corresponding arguments for this
     * constructor are {@code null}. If the artifact type specifies properties, those will get merged with the
     * properties passed directly into the constructor, with the latter properties taking precedence.
     *
     * @param groupId The group identifier of the artifact, may be {@code null}.
     * @param artifactId The artifact identifier of the artifact, may be {@code null}.
     * @param classifier The classifier of the artifact, may be {@code null}.
     * @param extension The file extension of the artifact, may be {@code null}.
     * @param version The version (may also be a meta version or version range) of the artifact, may be {@code null}.
     * @param properties The properties of the artifact, may be {@code null} if none.
     * @param type The artifact type from which to query classifier, file extension and properties, may be {@code null}.
     */
    public DefaultArtifact(
            String groupId,
            String artifactId,
            String classifier,
            String extension,
            String version,
            Map<String, String> properties,
            ArtifactType type) {
        this.groupId = emptify(groupId);
        this.artifactId = emptify(artifactId);
        if (classifier != null || type == null) {
            this.classifier = emptify(classifier);
        } else {
            this.classifier = emptify(type.getClassifier());
        }
        if (extension != null || type == null) {
            this.extension = emptify(extension);
        } else {
            this.extension = emptify(type.getExtension());
        }
        this.version = emptify(version);
        this.path = null;
        this.properties = mergeArtifactProperties(properties, (type != null) ? type.getProperties() : null);
    }

    private static Map<String, String> mergeArtifactProperties(
            Map<String, String> artifactProperties, Map<String, String> typeDefaultProperties) {
        Map<String, String> properties;

        if (artifactProperties == null || artifactProperties.isEmpty()) {
            if (typeDefaultProperties == null || typeDefaultProperties.isEmpty()) {
                properties = Collections.emptyMap();
            } else {
                // type default properties are already unmodifiable
                return typeDefaultProperties;
            }
        } else {
            properties = new HashMap<>();
            if (typeDefaultProperties != null) {
                properties.putAll(typeDefaultProperties);
            }
            if (artifactProperties != null) {
                properties.putAll(artifactProperties);
            }
            properties = Collections.unmodifiableMap(properties);
        }

        return properties;
    }

    /**
     * Creates a new artifact with the specified coordinates, properties and file. Passing {@code null} for any of the
     * coordinates is equivalent to specifying an empty string.
     *
     * @param groupId The group identifier of the artifact, may be {@code null}.
     * @param artifactId The artifact identifier of the artifact, may be {@code null}.
     * @param classifier The classifier of the artifact, may be {@code null}.
     * @param extension The file extension of the artifact, may be {@code null}.
     * @param version The version of the artifact, may be {@code null}.
     * @param properties The properties of the artifact, may be {@code null} if none.
     * @param file The resolved file of the artifact, may be {@code null}.
     */
    public DefaultArtifact(
            String groupId,
            String artifactId,
            String classifier,
            String extension,
            String version,
            Map<String, String> properties,
            File file) {
        this.groupId = emptify(groupId);
        this.artifactId = emptify(artifactId);
        this.classifier = emptify(classifier);
        this.extension = emptify(extension);
        this.version = emptify(version);
        this.path = file != null ? file.toPath() : null;
        this.properties = copyProperties(properties);
    }

    /**
     * Creates a new artifact with the specified coordinates, properties and file. Passing {@code null} for any of the
     * coordinates is equivalent to specifying an empty string.
     *
     * @param groupId The group identifier of the artifact, may be {@code null}.
     * @param artifactId The artifact identifier of the artifact, may be {@code null}.
     * @param classifier The classifier of the artifact, may be {@code null}.
     * @param extension The file extension of the artifact, may be {@code null}.
     * @param version The version of the artifact, may be {@code null}.
     * @param properties The properties of the artifact, may be {@code null} if none.
     * @param path The resolved file of the artifact, may be {@code null}.
     */
    public DefaultArtifact(
            String groupId,
            String artifactId,
            String classifier,
            String extension,
            String version,
            Map<String, String> properties,
            Path path) {
        this.groupId = emptify(groupId);
        this.artifactId = emptify(artifactId);
        this.classifier = emptify(classifier);
        this.extension = emptify(extension);
        this.version = emptify(version);
        this.path = path;
        this.properties = copyProperties(properties);
    }

    DefaultArtifact(
            String groupId,
            String artifactId,
            String classifier,
            String extension,
            String version,
            Path path,
            Map<String, String> properties) {
        // NOTE: This constructor assumes immutability of the provided properties, for internal use only
        this.groupId = emptify(groupId);
        this.artifactId = emptify(artifactId);
        this.classifier = emptify(classifier);
        this.extension = emptify(extension);
        this.version = emptify(version);
        this.path = path;
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

    public String getClassifier() {
        return classifier;
    }

    public String getExtension() {
        return extension;
    }

    @Deprecated
    public File getFile() {
        return path != null ? path.toFile() : null;
    }

    public Path getPath() {
        return path;
    }

    public Map<String, String> getProperties() {
        return properties;
    }
}
