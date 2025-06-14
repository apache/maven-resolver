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
package org.eclipse.aether.internal.impl;

import javax.inject.Named;
import javax.inject.Singleton;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;

import static java.util.Objects.requireNonNull;

/**
 * Default implementation of {@link LocalPathComposer}.
 *
 * @since 1.8.1
 */
@Singleton
@Named
public final class DefaultLocalPathComposer implements LocalPathComposer {
    @Override
    public String getPathForArtifact(Artifact artifact, boolean local) {
        requireNonNull(artifact);

        StringBuilder path = new StringBuilder(128);

        path.append(artifact.getGroupId().replace('.', '/')).append('/');

        path.append(artifact.getArtifactId()).append('/');

        path.append(artifact.getBaseVersion()).append('/');

        path.append(artifact.getArtifactId()).append('-');
        if (local) {
            path.append(artifact.getBaseVersion());
        } else {
            path.append(artifact.getVersion());
        }

        if (!artifact.getClassifier().isEmpty()) {
            path.append('-').append(artifact.getClassifier());
        }

        if (!artifact.getExtension().isEmpty()) {
            path.append('.').append(artifact.getExtension());
        }

        return path.toString();
    }

    @Override
    public String getPathForMetadata(Metadata metadata, String repositoryKey) {
        requireNonNull(metadata);
        requireNonNull(repositoryKey);

        StringBuilder path = new StringBuilder(128);

        if (!metadata.getGroupId().isEmpty()) {
            path.append(metadata.getGroupId().replace('.', '/')).append('/');

            if (!metadata.getArtifactId().isEmpty()) {
                path.append(metadata.getArtifactId()).append('/');

                if (!metadata.getVersion().isEmpty()) {
                    path.append(metadata.getVersion()).append('/');
                }
            }
        }

        path.append(insertRepositoryKey(metadata.getType(), repositoryKey));

        return path.toString();
    }

    private String insertRepositoryKey(String metadataType, String repositoryKey) {
        if (metadataType.contains("/") && !metadataType.endsWith("/")) {
            int lastSlash = metadataType.lastIndexOf('/');
            return metadataType.substring(0, lastSlash + 1)
                    + insertRepositoryKey(metadataType.substring(lastSlash + 1), repositoryKey);
        } else {
            String result;
            int idx = metadataType.indexOf('.');
            if (idx < 0) {
                result = metadataType + '-' + repositoryKey;
            } else {
                result = metadataType.substring(0, idx) + '-' + repositoryKey + metadataType.substring(idx);
            }
            return result;
        }
    }
}
