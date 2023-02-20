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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A skeleton class for artifacts.
 */
public abstract class AbstractArtifact implements Artifact {

    private static final String SNAPSHOT = "SNAPSHOT";

    private static final Pattern SNAPSHOT_TIMESTAMP = Pattern.compile("^(.*-)?([0-9]{8}\\.[0-9]{6}-[0-9]+)$");

    public boolean isSnapshot() {
        return isSnapshot(getVersion());
    }

    private static boolean isSnapshot(String version) {
        return version.endsWith(SNAPSHOT) || SNAPSHOT_TIMESTAMP.matcher(version).matches();
    }

    public String getBaseVersion() {
        return toBaseVersion(getVersion());
    }

    private static String toBaseVersion(String version) {
        String baseVersion;

        if (version == null) {
            baseVersion = null;
        } else if (version.startsWith("[") || version.startsWith("(")) {
            baseVersion = version;
        } else {
            Matcher m = SNAPSHOT_TIMESTAMP.matcher(version);
            if (m.matches()) {
                if (m.group(1) != null) {
                    baseVersion = m.group(1) + SNAPSHOT;
                } else {
                    baseVersion = SNAPSHOT;
                }
            } else {
                baseVersion = version;
            }
        }

        return baseVersion;
    }

    /**
     * Creates a new artifact with the specified coordinates, properties and file.
     *
     * @param version The version of the artifact, may be {@code null}.
     * @param properties The properties of the artifact, may be {@code null} if none. The method may assume immutability
     *            of the supplied map, i.e. need not copy it.
     * @param file The resolved file of the artifact, may be {@code null}.
     * @return The new artifact instance, never {@code null}.
     */
    private Artifact newInstance(String version, Map<String, String> properties, File file) {
        return new DefaultArtifact(
                getGroupId(), getArtifactId(), getClassifier(), getExtension(), version, file, properties);
    }

    public Artifact setVersion(String version) {
        String current = getVersion();
        if (current.equals(version) || (version == null && current.isEmpty())) {
            return this;
        }
        return newInstance(version, getProperties(), getFile());
    }

    public Artifact setFile(File file) {
        File current = getFile();
        if (Objects.equals(current, file)) {
            return this;
        }
        return newInstance(getVersion(), getProperties(), file);
    }

    public Artifact setProperties(Map<String, String> properties) {
        Map<String, String> current = getProperties();
        if (current.equals(properties) || (properties == null && current.isEmpty())) {
            return this;
        }
        return newInstance(getVersion(), copyProperties(properties), getFile());
    }

    public String getProperty(String key, String defaultValue) {
        String value = getProperties().get(key);
        return (value != null) ? value : defaultValue;
    }

    /**
     * Copies the specified artifact properties. This utility method should be used when creating new artifact instances
     * with caller-supplied properties.
     *
     * @param properties The properties to copy, may be {@code null}.
     * @return The copied and read-only properties, never {@code null}.
     */
    protected static Map<String, String> copyProperties(Map<String, String> properties) {
        if (properties != null && !properties.isEmpty()) {
            return Collections.unmodifiableMap(new HashMap<>(properties));
        } else {
            return Collections.emptyMap();
        }
    }

    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder(128);
        buffer.append(getGroupId());
        buffer.append(':').append(getArtifactId());
        buffer.append(':').append(getExtension());
        if (getClassifier().length() > 0) {
            buffer.append(':').append(getClassifier());
        }
        buffer.append(':').append(getVersion());
        return buffer.toString();
    }

    /**
     * Compares this artifact with the specified object.
     *
     * @param obj The object to compare this artifact against, may be {@code null}.
     * @return {@code true} if and only if the specified object is another {@link Artifact} with equal coordinates,
     *         properties and file, {@code false} otherwise.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof Artifact)) {
            return false;
        }

        Artifact that = (Artifact) obj;

        return Objects.equals(getArtifactId(), that.getArtifactId())
                && Objects.equals(getGroupId(), that.getGroupId())
                && Objects.equals(getVersion(), that.getVersion())
                && Objects.equals(getExtension(), that.getExtension())
                && Objects.equals(getClassifier(), that.getClassifier())
                && Objects.equals(getFile(), that.getFile())
                && Objects.equals(getProperties(), that.getProperties());
    }

    /**
     * Returns a hash code for this artifact.
     *
     * @return A hash code for the artifact.
     */
    @Override
    public int hashCode() {
        int hash = 17;
        hash = hash * 31 + getGroupId().hashCode();
        hash = hash * 31 + getArtifactId().hashCode();
        hash = hash * 31 + getExtension().hashCode();
        hash = hash * 31 + getClassifier().hashCode();
        hash = hash * 31 + getVersion().hashCode();
        hash = hash * 31 + hash(getFile());
        return hash;
    }

    private static int hash(Object obj) {
        return (obj != null) ? obj.hashCode() : 0;
    }
}
