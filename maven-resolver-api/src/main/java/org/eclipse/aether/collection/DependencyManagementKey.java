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
package org.eclipse.aether.collection;

import java.util.Objects;

import org.eclipse.aether.artifact.Artifact;

/**
 * A key used for dependency management.
 *
 * @since 2.0.13
 */
public final class DependencyManagementKey {
    private final String groupId;
    private final String artifactId;
    private final String extension;
    private final String classifier;
    private final int hashCode;

    /**
     * Creates a new DM key for given artifact.
     */
    public DependencyManagementKey(Artifact artifact) {
        this.groupId = artifact.getGroupId();
        this.artifactId = artifact.getArtifactId();
        this.extension = artifact.getExtension();
        this.classifier = artifact.getClassifier();
        this.hashCode = Objects.hash(groupId, artifactId, extension, classifier);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof DependencyManagementKey)) {
            return false;
        }
        DependencyManagementKey that = (DependencyManagementKey) obj;
        return artifactId.equals(that.artifactId)
                && groupId.equals(that.groupId)
                && extension.equals(that.extension)
                && classifier.equals(that.classifier);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        if (classifier.isEmpty()) {
            return groupId + ":" + artifactId + ":" + extension;
        } else {
            return groupId + ":" + artifactId + ":" + extension + ":" + classifier;
        }
    }
}
