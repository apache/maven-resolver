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
package org.eclipse.aether.util.graph;

import java.util.function.Predicate;

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
 * @see org.eclipse.aether.artifact.ArtifactProperties#LOCAL_PATH
 * @since 2.0.0
 */
@FunctionalInterface
public interface SystemScopePredicate extends Predicate<String> {
    default boolean test(Dependency dependency) {
        return test(dependency.getScope());
    }

    default boolean test(DependencyNode dependencyNode) {
        return dependencyNode.getDependency() != null && test(dependencyNode.getDependency());
    }
}
