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
package org.eclipse.aether.internal.impl.synccontext.named;

import org.eclipse.aether.artifact.Artifact;

/**
 * Artifact GAECV {@link NameMapper}, uses artifact and metadata coordinates to name their corresponding locks. Is not
 * considering local repository, only the artifact coordinates. May use custom prefixes and suffixes and separators,
 * hence this instance may or may not be filesystem friendly (depends on strings used).
 *
 * @since 1.9.25
 */
public class GAECVNameMapper extends GAVNameMapper {
    public GAECVNameMapper(
            boolean fileSystemFriendly,
            String artifactPrefix,
            String artifactSuffix,
            String metadataPrefix,
            String metadataSuffix,
            String fieldSeparator) {
        super(fileSystemFriendly, artifactPrefix, artifactSuffix, metadataPrefix, metadataSuffix, fieldSeparator);
    }

    @Override
    protected String getArtifactName(Artifact artifact) {
        if (artifact.getClassifier().isEmpty()) {
            return artifactPrefix
                    + artifact.getGroupId()
                    + fieldSeparator
                    + artifact.getArtifactId()
                    + fieldSeparator
                    + artifact.getExtension()
                    + fieldSeparator
                    + artifact.getBaseVersion()
                    + artifactSuffix;
        } else {
            return artifactPrefix
                    + artifact.getGroupId()
                    + fieldSeparator
                    + artifact.getArtifactId()
                    + fieldSeparator
                    + artifact.getExtension()
                    + fieldSeparator
                    + artifact.getClassifier()
                    + fieldSeparator
                    + artifact.getBaseVersion()
                    + artifactSuffix;
        }
    }
}
