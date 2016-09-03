package org.eclipse.aether.graph;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *  http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionConstraint;

/**
 * A node within a dependency graph. To conserve memory, dependency graphs may reuse a given node instance multiple
 * times to represent reoccurring dependencies. As such clients traversing a dependency graph should be prepared to
 * discover multiple paths leading to the same node instance unless the input graph is known to be a duplicate-free
 * tree. <em>Note:</em> Unless otherwise noted, implementation classes are not thread-safe and dependency nodes should
 * not be mutated by concurrent threads.
 * 
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 */
public interface DependencyNode
{

    /**
     * A bit flag indicating the dependency version was subject to dependency management
     * 
     * @see #getManagedBits()
     */
    int MANAGED_VERSION = 0x01;

    /**
     * A bit flag indicating the dependency scope was subject to dependency management
     * 
     * @see #getManagedBits()
     */
    int MANAGED_SCOPE = 0x02;

    /**
     * A bit flag indicating the optional flag was subject to dependency management
     * 
     * @see #getManagedBits()
     */
    int MANAGED_OPTIONAL = 0x04;

    /**
     * A bit flag indicating the artifact properties were subject to dependency management
     * 
     * @see #getManagedBits()
     */
    int MANAGED_PROPERTIES = 0x08;

    /**
     * A bit flag indicating the exclusions were subject to dependency management
     * 
     * @see #getManagedBits()
     */
    int MANAGED_EXCLUSIONS = 0x10;

    /**
     * Gets the child nodes of this node. To conserve memory, dependency nodes with equal dependencies may share the
     * same child list instance. Hence clients mutating the child list need to be aware that these changes might affect
     * more than this node. Where this is not desired, the child list should be copied before mutation if the client
     * cannot be sure whether it might be shared with other nodes in the graph.
     * 
     * @return The child nodes of this node, never {@code null}.
     */
    List<DependencyNode> getChildren();

    /**
     * Sets the child nodes of this node.
     * 
     * @param children The child nodes, may be {@code null}
     */
    void setChildren( List<DependencyNode> children );

    /**
     * Gets the dependency associated with this node. <em>Note:</em> For dependency graphs that have been constructed
     * without a root dependency, this method will yield {@code null} when invoked on the graph's root node. The root
     * node of such graphs may however still have a label as returned by {@link #getArtifact()}.
     * 
     * @return The dependency or {@code null} if none.
     */
    Dependency getDependency();

    /**
     * Gets the artifact associated with this node. If this node is associated with a dependency, this is equivalent to
     * {@code getDependency().getArtifact()}. Otherwise the artifact merely provides a label for this node in which case
     * the artifact must not be subjected to dependency collection/resolution.
     * 
     * @return The associated artifact or {@code null} if none.
     */
    Artifact getArtifact();

    /**
     * Updates the artifact of the dependency after resolution. The new artifact must have the same coordinates as the
     * original artifact. This method may only be invoked if this node actually has a dependency, i.e. if
     * {@link #getDependency()} is not null.
     * 
     * @param artifact The artifact satisfying the dependency, must not be {@code null}.
     */
    void setArtifact( Artifact artifact );

    /**
     * Gets the sequence of relocations that was followed to resolve the artifact referenced by the dependency.
     * 
     * @return The (read-only) sequence of relocations, never {@code null}.
     */
    List<? extends Artifact> getRelocations();

    /**
     * Gets the known aliases for this dependency's artifact. An alias can be used to mark a patched rebuild of some
     * other artifact as such, thereby allowing conflict resolution to consider the patched and the original artifact as
     * a conflict.
     * 
     * @return The (read-only) set of known aliases, never {@code null}.
     */
    Collection<? extends Artifact> getAliases();

    /**
     * Gets the version constraint that was parsed from the dependency's version declaration.
     * 
     * @return The version constraint for this node or {@code null}.
     */
    VersionConstraint getVersionConstraint();

    /**
     * Gets the version that was selected for the dependency's target artifact.
     * 
     * @return The parsed version or {@code null}.
     */
    Version getVersion();

    /**
     * Sets the scope of the dependency. This method may only be invoked if this node actually has a dependency, i.e. if
     * {@link #getDependency()} is not null.
     * 
     * @param scope The scope, may be {@code null}.
     */
    void setScope( String scope );

    /**
     * Sets the optional flag of the dependency. This method may only be invoked if this node actually has a dependency,
     * i.e. if {@link #getDependency()} is not null.
     * 
     * @param optional The optional flag, may be {@code null}.
     */
    void setOptional( Boolean optional );

    /**
     * Gets a bit field indicating which attributes of this node were subject to dependency management.
     * 
     * @return A bit field containing any of the bits {@link #MANAGED_VERSION}, {@link #MANAGED_SCOPE},
     *         {@link #MANAGED_OPTIONAL}, {@link #MANAGED_PROPERTIES} and {@link #MANAGED_EXCLUSIONS} if the
     *         corresponding attribute was set via dependency management.
     */
    int getManagedBits();

    /**
     * Gets the remote repositories from which this node's artifact shall be resolved.
     * 
     * @return The (read-only) list of remote repositories to use for artifact resolution, never {@code null}.
     */
    List<RemoteRepository> getRepositories();

    /**
     * Gets the request context in which this dependency node was created.
     * 
     * @return The request context, never {@code null}.
     */
    String getRequestContext();

    /**
     * Sets the request context in which this dependency node was created.
     * 
     * @param context The context, may be {@code null}.
     */
    void setRequestContext( String context );

    /**
     * Gets the custom data associated with this dependency node. Clients of the repository system can use this data to
     * annotate dependency nodes with domain-specific information. Note that the returned map is read-only and
     * {@link #setData(Object, Object)} needs to be used to update the custom data.
     * 
     * @return The (read-only) key-value mappings, never {@code null}.
     */
    Map<?, ?> getData();

    /**
     * Sets the custom data associated with this dependency node.
     * 
     * @param data The new custom data, may be {@code null}.
     */
    void setData( Map<Object, Object> data );

    /**
     * Associates the specified dependency node data with the given key. <em>Note:</em> This method must not be called
     * while {@link #getData()} is being iterated.
     * 
     * @param key The key under which to store the data, must not be {@code null}.
     * @param value The data to associate with the key, may be {@code null} to remove the mapping.
     */
    void setData( Object key, Object value );

    /**
     * Traverses this node and potentially its children using the specified visitor.
     * 
     * @param visitor The visitor to call back, must not be {@code null}.
     * @return {@code true} to visit siblings nodes of this node as well, {@code false} to skip siblings.
     */
    boolean accept( DependencyVisitor visitor );

}
