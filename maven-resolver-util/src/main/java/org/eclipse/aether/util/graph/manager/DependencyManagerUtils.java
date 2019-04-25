package org.eclipse.aether.util.graph.manager;

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
import java.util.Map;

import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.Exclusion;

/**
 * A utility class assisting in analyzing the effects of dependency management.
 */
public final class DependencyManagerUtils
{

    /**
     * The key in the repository session's {@link org.eclipse.aether.RepositorySystemSession#getConfigProperties()
     * configuration properties} used to store a {@link Boolean} flag controlling the verbose mode for dependency
     * management. If enabled, the original attributes of a dependency before its update due to dependency managemnent
     * will be recorded * in the node's {@link DependencyNode#getData() custom data} when building a dependency graph.
     */
    public static final String CONFIG_PROP_VERBOSE = "aether.dependencyManager.verbose";

    /**
     * The key in the dependency node's {@link DependencyNode#getData() custom data} under which the original version is
     * stored.
     */
    public static final String NODE_DATA_PREMANAGED_VERSION = "premanaged.version";

    /**
     * The key in the dependency node's {@link DependencyNode#getData() custom data} under which the original scope is
     * stored.
     */
    public static final String NODE_DATA_PREMANAGED_SCOPE = "premanaged.scope";

    /**
     * The key in the dependency node's {@link DependencyNode#getData() custom data} under which the original optional
     * flag is stored.
     */
    public static final String NODE_DATA_PREMANAGED_OPTIONAL = "premanaged.optional";

    /**
     * The key in the dependency node's {@link DependencyNode#getData() custom data} under which the original exclusions
     * are stored.
     *
     * @since 1.1.0
     */
    public static final String NODE_DATA_PREMANAGED_EXCLUSIONS = "premanaged.exclusions";

    /**
     * The key in the dependency node's {@link DependencyNode#getData() custom data} under which the original properties
     * are stored.
     *
     * @since 1.1.0
     */
    public static final String NODE_DATA_PREMANAGED_PROPERTIES = "premanaged.properties";

    /**
     * Gets the version or version range of the specified dependency node before dependency management was applied (if
     * any).
     *
     * @param node The dependency node to retrieve the premanaged data for, must not be {@code null}.
     *
     * @return The node's dependency version before dependency management or {@code null} if the version was not managed
     *         or if {@link #CONFIG_PROP_VERBOSE} was not enabled.
     */
    public static String getPremanagedVersion( DependencyNode node )
    {
        if ( ( node.getManagedBits() & DependencyNode.MANAGED_VERSION ) == 0 )
        {
            return null;
        }
        return cast( node.getData().get( NODE_DATA_PREMANAGED_VERSION ), String.class );
    }

    /**
     * Gets the scope of the specified dependency node before dependency management was applied (if any).
     *
     * @param node The dependency node to retrieve the premanaged data for, must not be {@code null}.
     *
     * @return The node's dependency scope before dependency management or {@code null} if the scope was not managed or
     *         if {@link #CONFIG_PROP_VERBOSE} was not enabled.
     */
    public static String getPremanagedScope( DependencyNode node )
    {
        if ( ( node.getManagedBits() & DependencyNode.MANAGED_SCOPE ) == 0 )
        {
            return null;
        }
        return cast( node.getData().get( NODE_DATA_PREMANAGED_SCOPE ), String.class );
    }

    /**
     * Gets the optional flag of the specified dependency node before dependency management was applied (if any).
     *
     * @param node The dependency node to retrieve the premanaged data for, must not be {@code null}.
     *
     * @return The node's optional flag before dependency management or {@code null} if the flag was not managed or if
     *         {@link #CONFIG_PROP_VERBOSE} was not enabled.
     */
    public static Boolean getPremanagedOptional( DependencyNode node )
    {
        if ( ( node.getManagedBits() & DependencyNode.MANAGED_OPTIONAL ) == 0 )
        {
            return null;
        }
        return cast( node.getData().get( NODE_DATA_PREMANAGED_OPTIONAL ), Boolean.class );
    }

    /**
     * Gets the {@code Exclusion}s of the specified dependency node before dependency management was applied (if any).
     *
     * @param node The dependency node to retrieve the premanaged data for, must not be {@code null}.
     *
     * @return The nodes' {@code Exclusion}s before dependency management or {@code null} if exclusions were not managed
     *         or if {@link #CONFIG_PROP_VERBOSE} was not enabled.
     *
     * @since 1.1.0
     */
    @SuppressWarnings( "unchecked" )
    public static Collection<Exclusion> getPremanagedExclusions( DependencyNode node )
    {
        if ( ( node.getManagedBits() & DependencyNode.MANAGED_EXCLUSIONS ) == 0 )
        {
            return null;
        }
        return cast( node.getData().get( NODE_DATA_PREMANAGED_EXCLUSIONS ), Collection.class );
    }

    /**
     * Gets the properties of the specified dependency node before dependency management was applied (if any).
     *
     * @param node The dependency node to retrieve the premanaged data for, must not be {@code null}.
     *
     * @return The nodes' properties before dependency management or {@code null} if properties were not managed or if
     *         {@link #CONFIG_PROP_VERBOSE} was not enabled.
     *
     * @since 1.1.0
     */
    @SuppressWarnings( "unchecked" )
    public static Map<String, String> getPremanagedProperties( DependencyNode node )
    {
        if ( ( node.getManagedBits() & DependencyNode.MANAGED_PROPERTIES ) == 0 )
        {
            return null;
        }
        return cast( node.getData().get( NODE_DATA_PREMANAGED_PROPERTIES ), Map.class );
    }

    private static <T> T cast( Object obj, Class<T> type )
    {
        return type.isInstance( obj ) ? type.cast( obj ) : null;
    }

}
