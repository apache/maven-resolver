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
package org.eclipse.aether.internal.test.util;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;

import static java.util.Objects.requireNonNull;

/**
 * An artifact descriptor reader that gets data from a simple text file on the classpath. The data file for an artifact
 * with the coordinates {@code gid:aid:ext:ver} is expected to be named {@code gid_aid_ver.ini} and can optionally have
 * some prefix. The data file can have the following sections:
 * <ul>
 * <li>relocation</li>
 * <li>dependencies</li>
 * <li>managedDependencies</li>
 * <li>repositories</li>
 * </ul>
 * The relocation and dependency sections contain artifact coordinates of the form:
 *
 * <pre>
 * gid:aid:ext:ver[:scope][:optional]
 * </pre>
 *
 * The dependency sections may also specify exclusions:
 *
 * <pre>
 * -gid:aid
 * </pre>
 *
 * A repository definition is of the form:
 *
 * <pre>
 * id:type:url
 * </pre>
 *
 * <h2>Example</h2>
 *
 * <pre>
 * [relocation]
 * gid:aid:ext:ver
 *
 * [dependencies]
 * gid:aid:ext:ver:scope
 * -exclusion:aid
 * gid:aid2:ext:ver:scope:optional
 *
 * [managed-dependencies]
 * gid:aid2:ext:ver2:scope
 * -gid:aid
 * -gid:aid
 *
 * [repositories]
 * id:type:file:///test-repo
 * </pre>
 */
public class IniArtifactDescriptorReader {
    private final IniArtifactDataReader reader;

    /**
     * Use the given prefix to load the artifact descriptions from the classpath.
     */
    public IniArtifactDescriptorReader(String prefix) {
        reader = new IniArtifactDataReader(prefix);
    }

    /**
     * Parses the resource {@code $prefix/gid_aid_ver.ini} from the request artifact as an artifact description and
     * wraps it into an ArtifactDescriptorResult.
     */
    public ArtifactDescriptorResult readArtifactDescriptor(
            RepositorySystemSession session, ArtifactDescriptorRequest request) throws ArtifactDescriptorException {
        requireNonNull(session, "session cannot be null");
        requireNonNull(request, "request cannot be null");
        ArtifactDescriptorResult result = new ArtifactDescriptorResult(request);
        for (Artifact artifact = request.getArtifact(); ; ) {
            String resourceName = String.format(
                    "%s_%s_%s.ini", artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
            try {
                ArtifactDescription data = reader.parse(resourceName);
                if (data.getRelocation() != null) {
                    result.addRelocation(artifact);
                    result.setArtifact(data.getRelocation());
                    artifact = data.getRelocation();
                } else {
                    result.setArtifact(artifact);
                    result.setDependencies(data.getDependencies());
                    result.setManagedDependencies(data.getManagedDependencies());
                    result.setRepositories(data.getRepositories());
                    return result;
                }
            } catch (Exception e) {
                throw new ArtifactDescriptorException(result, e.getMessage(), e);
            }
        }
    }
}
