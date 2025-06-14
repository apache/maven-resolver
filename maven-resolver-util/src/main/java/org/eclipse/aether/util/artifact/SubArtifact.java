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

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

import org.eclipse.aether.artifact.AbstractArtifact;
import org.eclipse.aether.artifact.Artifact;

import static java.util.Objects.requireNonNull;

/**
 * An artifact whose identity is derived from another artifact. <em>Note:</em> Instances of this class are immutable and
 * the exposed mutators return new objects rather than changing the current instance.
 */
public final class SubArtifact extends AbstractArtifact {

    private final Artifact mainArtifact;

    private final String classifier;

    private final String extension;

    private final Path path;

    private final Map<String, String> properties;

    /**
     * Creates a new sub artifact. The classifier and extension specified for this artifact may use the asterisk
     * character "*" to refer to the corresponding property of the main artifact. For instance, the classifier
     * "*-sources" can be used to refer to the source attachment of an artifact. Likewise, the extension "*.asc" can be
     * used to refer to the GPG signature of an artifact.
     *
     * @param mainArtifact The artifact from which to derive the identity, must not be {@code null}.
     * @param classifier The classifier for this artifact, may be {@code null} if none.
     * @param extension The extension for this artifact, may be {@code null} if none.
     */
    public SubArtifact(Artifact mainArtifact, String classifier, String extension) {
        this(mainArtifact, classifier, extension, (File) null);
    }

    /**
     * Creates a new sub artifact. The classifier and extension specified for this artifact may use the asterisk
     * character "*" to refer to the corresponding property of the main artifact. For instance, the classifier
     * "*-sources" can be used to refer to the source attachment of an artifact. Likewise, the extension "*.asc" can be
     * used to refer to the GPG signature of an artifact.
     *
     * @param mainArtifact The artifact from which to derive the identity, must not be {@code null}.
     * @param classifier The classifier for this artifact, may be {@code null} if none.
     * @param extension The extension for this artifact, may be {@code null} if none.
     * @param file The file for this artifact, may be {@code null} if unresolved.
     */
    public SubArtifact(Artifact mainArtifact, String classifier, String extension, File file) {
        this(mainArtifact, classifier, extension, null, file);
    }

    /**
     * Creates a new sub artifact. The classifier and extension specified for this artifact may use the asterisk
     * character "*" to refer to the corresponding property of the main artifact. For instance, the classifier
     * "*-sources" can be used to refer to the source attachment of an artifact. Likewise, the extension "*.asc" can be
     * used to refer to the GPG signature of an artifact.
     *
     * @param mainArtifact The artifact from which to derive the identity, must not be {@code null}.
     * @param classifier The classifier for this artifact, may be {@code null} if none.
     * @param extension The extension for this artifact, may be {@code null} if none.
     * @param path The file for this artifact, may be {@code null} if unresolved.
     * @since 2.0.0
     */
    public SubArtifact(Artifact mainArtifact, String classifier, String extension, Path path) {
        this(mainArtifact, classifier, extension, null, path);
    }

    /**
     * Creates a new sub artifact. The classifier and extension specified for this artifact may use the asterisk
     * character "*" to refer to the corresponding property of the main artifact. For instance, the classifier
     * "*-sources" can be used to refer to the source attachment of an artifact. Likewise, the extension "*.asc" can be
     * used to refer to the GPG signature of an artifact.
     *
     * @param mainArtifact The artifact from which to derive the identity, must not be {@code null}.
     * @param classifier The classifier for this artifact, may be {@code null} if none.
     * @param extension The extension for this artifact, may be {@code null} if none.
     * @param properties The properties of the artifact, may be {@code null}.
     */
    public SubArtifact(Artifact mainArtifact, String classifier, String extension, Map<String, String> properties) {
        this(mainArtifact, classifier, extension, properties, (Path) null);
    }

    /**
     * Creates a new sub artifact. The classifier and extension specified for this artifact may use the asterisk
     * character "*" to refer to the corresponding property of the main artifact. For instance, the classifier
     * "*-sources" can be used to refer to the source attachment of an artifact. Likewise, the extension "*.asc" can be
     * used to refer to the GPG signature of an artifact.
     *
     * @param mainArtifact The artifact from which to derive the identity, must not be {@code null}.
     * @param classifier The classifier for this artifact, may be {@code null} if none.
     * @param extension The extension for this artifact, may be {@code null} if none.
     * @param properties The properties of the artifact, may be {@code null}.
     * @param file The file for this artifact, may be {@code null} if unresolved.
     */
    public SubArtifact(
            Artifact mainArtifact, String classifier, String extension, Map<String, String> properties, File file) {
        this(mainArtifact, classifier, extension, properties, file != null ? file.toPath() : null);
    }

    /**
     * Creates a new sub artifact. The classifier and extension specified for this artifact may use the asterisk
     * character "*" to refer to the corresponding property of the main artifact. For instance, the classifier
     * "*-sources" can be used to refer to the source attachment of an artifact. Likewise, the extension "*.asc" can be
     * used to refer to the GPG signature of an artifact.
     *
     * @param mainArtifact The artifact from which to derive the identity, must not be {@code null}.
     * @param classifier The classifier for this artifact, may be {@code null} if none.
     * @param extension The extension for this artifact, may be {@code null} if none.
     * @param properties The properties of the artifact, may be {@code null}.
     * @param path The file for this artifact, may be {@code null} if unresolved.
     * @since 2.0.0
     */
    public SubArtifact(
            Artifact mainArtifact, String classifier, String extension, Map<String, String> properties, Path path) {
        this.mainArtifact = requireNonNull(mainArtifact, "main artifact cannot be null");
        this.classifier = classifier == null ? null : classifier.intern();
        this.extension = extension == null ? null : extension.intern();
        this.path = path;
        this.properties = copyProperties(properties);
    }

    private SubArtifact(
            Artifact mainArtifact, String classifier, String extension, Path path, Map<String, String> properties) {
        // NOTE: This constructor assumes immutability of the provided properties, for internal use only
        this.mainArtifact = mainArtifact;
        this.classifier = classifier;
        this.extension = extension;
        this.path = path;
        this.properties = properties;
    }

    @Override
    public String getGroupId() {
        return mainArtifact.getGroupId();
    }

    @Override
    public String getArtifactId() {
        return mainArtifact.getArtifactId();
    }

    @Override
    public String getVersion() {
        return mainArtifact.getVersion();
    }

    @Override
    public String getBaseVersion() {
        return mainArtifact.getBaseVersion();
    }

    @Override
    public boolean isSnapshot() {
        return mainArtifact.isSnapshot();
    }

    @Override
    public String getClassifier() {
        return expand(classifier, mainArtifact.getClassifier());
    }

    @Override
    public String getExtension() {
        return expand(extension, mainArtifact.getExtension());
    }

    @Deprecated
    @Override
    public File getFile() {
        return path != null ? path.toFile() : null;
    }

    @Override
    public Path getPath() {
        return path;
    }

    @Deprecated
    @Override
    public Artifact setFile(File file) {
        return setPath(file != null ? file.toPath() : null);
    }

    @Override
    public Artifact setPath(Path path) {
        if (Objects.equals(this.path, path)) {
            return this;
        }
        return new SubArtifact(mainArtifact, classifier, extension, path, properties);
    }

    @Override
    public Map<String, String> getProperties() {
        return properties;
    }

    @Override
    public Artifact setProperties(Map<String, String> properties) {
        if (this.properties.equals(properties) || (properties == null && this.properties.isEmpty())) {
            return this;
        }
        return new SubArtifact(mainArtifact, classifier, extension, properties, path);
    }

    private static String expand(String pattern, String replacement) {
        String result = "";
        if (pattern != null) {
            result = pattern.replace("*", replacement);

            if (replacement.isEmpty()) {
                if (pattern.startsWith("*")) {
                    int i = 0;
                    for (; i < result.length(); i++) {
                        char c = result.charAt(i);
                        if (c != '-' && c != '.') {
                            break;
                        }
                    }
                    result = result.substring(i);
                }
                if (pattern.endsWith("*")) {
                    int i = result.length() - 1;
                    for (; i >= 0; i--) {
                        char c = result.charAt(i);
                        if (c != '-' && c != '.') {
                            break;
                        }
                    }
                    result = result.substring(0, i + 1);
                }
            }
        }
        return result;
    }
}
