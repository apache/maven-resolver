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
package org.eclipse.aether.scope;

import java.util.Map;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactProperties;

/**
 * A special dependency scope: "system".
 * <p>
 * This is a special scope. In this scope case, Resolver should handle dependencies specially, as they have no POM (so
 * are always a leaf on graph), are not in any repository, but are actually hosted on host OS file system. On resolution
 * resolver merely checks is file present or not.
 *
 * @since 2.0.0
 *
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 */
public interface SystemDependencyScope extends DependencyScope {
    /**
     * Returns system path string of provided artifact, or {@code null}.
     *
     * @param artifact The artifact that we want system path from, must not be {@code null}.
     * @return the system path from passed in properties, or {@code null} if not present.
     */
    String getSystemPath(Artifact artifact);

    /**
     * Sets system path in properties. The passed in {@code systemPath} can be {@code null}, in which case expected
     * operation is "remove" (or "unset").
     *
     * @param properties the properties map, must not be {@code null}.
     * @param systemPath the system path to set (if not {@code null}) or unset (if {@code null}).
     */
    void setSystemPath(Map<String, String> properties, String systemPath);

    /**
     * The "legacy" system scope, used when there is no {@link ScopeManager} set on session.
     */
    SystemDependencyScope LEGACY = new SystemDependencyScope() {
        @Override
        public String getSystemPath(Artifact artifact) {
            return artifact.getProperty(ArtifactProperties.LOCAL_PATH, null);
        }

        @Override
        public void setSystemPath(Map<String, String> properties, String systemPath) {
            if (systemPath == null) {
                properties.remove(ArtifactProperties.LOCAL_PATH);
            } else {
                properties.put(ArtifactProperties.LOCAL_PATH, systemPath);
            }
        }

        @Override
        public String getId() {
            return "system";
        }

        @Override
        public boolean isTransitive() {
            return false;
        }
    };
}
