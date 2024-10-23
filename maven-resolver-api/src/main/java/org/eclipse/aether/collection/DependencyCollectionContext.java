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

import java.util.List;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;

/**
 * A context used during dependency collection to update the dependency manager, selector and traverser.
 *
 * @see DependencyManager#deriveChildManager(DependencyCollectionContext)
 * @see DependencyTraverser#deriveChildTraverser(DependencyCollectionContext)
 * @see DependencySelector#deriveChildSelector(DependencyCollectionContext)
 * @see VersionFilter#deriveChildFilter(DependencyCollectionContext)
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 */
public interface DependencyCollectionContext {

    /**
     * Gets the repository system session during which the dependency collection happens.
     *
     * @return The repository system session, never {@code null}.
     */
    RepositorySystemSession getSession();

    /**
     * Gets the artifact whose children are to be processed next during dependency collection. For all nodes but the
     * root, this is simply shorthand for {@code getDependency().getArtifact()}. In case of the root node however,
     * {@link #getDependency()} might be {@code null} while the node still has an artifact which serves as its label and
     * is not to be resolved.
     *
     * @return The artifact whose children are going to be processed or {@code null} in case of the root node without
     *         dependency and label.
     */
    Artifact getArtifact();

    /**
     * Gets the dependency whose children are to be processed next during dependency collection.
     *
     * @return The dependency whose children are going to be processed or {@code null} in case of the root node without
     *         dependency.
     */
    Dependency getDependency();

    /**
     * Gets the dependency management information that was contributed by the artifact descriptor of the current
     * dependency.
     *
     * @return The dependency management information, never {@code null}.
     */
    List<Dependency> getManagedDependencies();
}
