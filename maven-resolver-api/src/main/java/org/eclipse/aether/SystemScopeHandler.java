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
package org.eclipse.aether;

import java.util.Map;

import org.eclipse.aether.artifact.ArtifactProperties;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;

/**
 * In Resolver 1.x line, the "system" scope represented special artifacts. In 2.x resolver testing for this scope
 * is now delegated to consumer application. Class or component that wants to test for this special dependency scope
 * should use this interface, with implementation provided by consumer application.
 * <p>
 * System is a special scope that tells resolver that dependency is not to be found in any regular repository, so it
 * should not even try to resolve the artifact from them. Dependency in this scope does not have artifact descriptor
 * either. Artifacts in this scope should have the "local path" property set, pointing to a file on local system, where
 * the backing file should reside. Resolution of artifacts in this scope fails, if backing file does not exist
 * (no property set, or property contains invalid path, or the path points to a non-existent file).
 *
 * @since 2.0.0
 */
public interface SystemScopeHandler {
    /**
     * Returns {@code true} only, if given scope label equals "system" scope.
     */
    boolean isSystemScope(String scope);

    /**
     * Returns {@code true} if given dependency is in "system" scope.
     */
    default boolean isSystemScope(Dependency dependency) {
        return dependency != null && isSystemScope(dependency.getScope());
    }

    /**
     * Returns {@code true} if given dependency node dependency is in "system" scope.
     */
    default boolean isSystemScope(DependencyNode dependencyNode) {
        return dependencyNode != null
                && dependencyNode.getDependency() != null
                && isSystemScope(dependencyNode.getDependency());
    }

    /**
     * Returns system path string of provided artifact, or {@code null}.
     *
     * @return the system path from passed in properties, or {@code null} if not present.
     */
    String getSystemPath(Map<String, String> properties);

    /**
     * Sets system path in properties. The passed in {@code systemPath} can be {@code null}, in which case this is
     * "remove" operation (or "unset").
     */
    void setSystemPath(Map<String, String> properties, String systemPath);

    /**
     * The equivalent of Resolver 1.x "system" scope.
     */
    SystemScopeHandler LEGACY = new SystemScopeHandler() {
        @Override
        public boolean isSystemScope(String scope) {
            return "system".equals(scope);
        }

        @Override
        public String getSystemPath(Map<String, String> properties) {
            return properties.get(ArtifactProperties.LOCAL_PATH);
        }

        @Override
        public void setSystemPath(Map<String, String> properties, String systemPath) {
            if (systemPath == null) {
                properties.remove(ArtifactProperties.LOCAL_PATH);
            } else {
                properties.put(ArtifactProperties.LOCAL_PATH, systemPath);
            }
        }
    };
}
