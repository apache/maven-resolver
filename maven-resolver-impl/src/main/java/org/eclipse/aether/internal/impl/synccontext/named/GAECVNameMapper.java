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
 * Artifact GAECV {@link NameMapper} extends {@link GAVNameMapper} and improves artifact name mapping selectivity by
 * using all coordinates.
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
    protected String getArtifactName(Artifact artifact, String prefix, String separator, String suffix) {
        if (artifact.getClassifier().isEmpty()) {
            return prefix
                    + artifact.getGroupId()
                    + separator
                    + artifact.getArtifactId()
                    + separator
                    + artifact.getExtension()
                    + separator
                    + artifact.getBaseVersion()
                    + suffix;
        } else {
            return prefix
                    + artifact.getGroupId()
                    + separator
                    + artifact.getArtifactId()
                    + separator
                    + artifact.getExtension()
                    + separator
                    + artifact.getClassifier()
                    + separator
                    + artifact.getBaseVersion()
                    + suffix;
        }
    }
}
