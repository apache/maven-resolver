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
package org.eclipse.aether.graph;

/**
 * An exclusion of one or more transitive dependencies.
 *
 * @see Dependency#getExclusions()
 */
public final class Exclusion {

    private final String groupId;

    private final String artifactId;

    private final String classifier;

    private final String extension;

    /**
     * Creates an exclusion for artifacts with the specified coordinates.
     *
     * @param groupId The group identifier, may be {@code null}.
     * @param artifactId The artifact identifier, may be {@code null}.
     * @param classifier The classifier, may be {@code null}.
     * @param extension The file extension, may be {@code null}.
     */
    public Exclusion(String groupId, String artifactId, String classifier, String extension) {
        this.groupId = (groupId != null) ? groupId : "";
        this.artifactId = (artifactId != null) ? artifactId : "";
        this.classifier = (classifier != null) ? classifier : "";
        this.extension = (extension != null) ? extension : "";
    }

    /**
     * Gets the group identifier for artifacts to exclude.
     *
     * @return The group identifier, never {@code null}.
     */
    public String getGroupId() {
        return groupId;
    }

    /**
     * Gets the artifact identifier for artifacts to exclude.
     *
     * @return The artifact identifier, never {@code null}.
     */
    public String getArtifactId() {
        return artifactId;
    }

    /**
     * Gets the classifier for artifacts to exclude.
     *
     * @return The classifier, never {@code null}.
     */
    public String getClassifier() {
        return classifier;
    }

    /**
     * Gets the file extension for artifacts to exclude.
     *
     * @return The file extension of artifacts to exclude, never {@code null}.
     */
    public String getExtension() {
        return extension;
    }

    @Override
    public String toString() {
        return getGroupId()
                + ':'
                + getArtifactId()
                + ':'
                + getExtension()
                + (getClassifier().length() > 0 ? ':' + getClassifier() : "");
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else if (obj == null || !getClass().equals(obj.getClass())) {
            return false;
        }

        Exclusion that = (Exclusion) obj;

        return artifactId.equals(that.artifactId)
                && groupId.equals(that.groupId)
                && extension.equals(that.extension)
                && classifier.equals(that.classifier);
    }

    @Override
    public int hashCode() {
        int hash = 17;
        hash = hash * 31 + artifactId.hashCode();
        hash = hash * 31 + groupId.hashCode();
        hash = hash * 31 + classifier.hashCode();
        hash = hash * 31 + extension.hashCode();
        return hash;
    }
}
