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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A skeleton class for metadata.
 */
public abstract class AbstractMetadata implements Metadata {

    private Metadata newInstance(Map<String, String> properties, File file) {
        return new DefaultMetadata(
                getGroupId(), getArtifactId(), getVersion(), getType(), getNature(), file, properties);
    }

    public Metadata setFile(File file) {
        File current = getFile();
        if (Objects.equals(current, file)) {
            return this;
        }
        return newInstance(getProperties(), file);
    }

    public Metadata setProperties(Map<String, String> properties) {
        Map<String, String> current = getProperties();
        if (current.equals(properties) || (properties == null && current.isEmpty())) {
            return this;
        }
        return newInstance(copyProperties(properties), getFile());
    }

    public String getProperty(String key, String defaultValue) {
        String value = getProperties().get(key);
        return (value != null) ? value : defaultValue;
    }

    /**
     * Copies the specified metadata properties. This utility method should be used when creating new metadata instances
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
        if (getGroupId().length() > 0) {
            buffer.append(getGroupId());
        }
        if (getArtifactId().length() > 0) {
            buffer.append(':').append(getArtifactId());
        }
        if (getVersion().length() > 0) {
            buffer.append(':').append(getVersion());
        }
        buffer.append('/').append(getType());
        return buffer.toString();
    }

    /**
     * Compares this metadata with the specified object.
     *
     * @param obj The object to compare this metadata against, may be {@code null}.
     * @return {@code true} if and only if the specified object is another {@link Metadata} with equal coordinates,
     *         type, nature, properties and file, {@code false} otherwise.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof Metadata)) {
            return false;
        }

        Metadata that = (Metadata) obj;

        return Objects.equals(getArtifactId(), that.getArtifactId())
                && Objects.equals(getGroupId(), that.getGroupId())
                && Objects.equals(getVersion(), that.getVersion())
                && Objects.equals(getType(), that.getType())
                && Objects.equals(getNature(), that.getNature())
                && Objects.equals(getFile(), that.getFile())
                && Objects.equals(getProperties(), that.getProperties());
    }

    /**
     * Returns a hash code for this metadata.
     *
     * @return A hash code for the metadata.
     */
    @Override
    public int hashCode() {
        int hash = 17;
        hash = hash * 31 + getGroupId().hashCode();
        hash = hash * 31 + getArtifactId().hashCode();
        hash = hash * 31 + getType().hashCode();
        hash = hash * 31 + getNature().hashCode();
        hash = hash * 31 + getVersion().hashCode();
        hash = hash * 31 + hash(getFile());
        return hash;
    }

    private static int hash(Object obj) {
        return (obj != null) ? obj.hashCode() : 0;
    }
}
