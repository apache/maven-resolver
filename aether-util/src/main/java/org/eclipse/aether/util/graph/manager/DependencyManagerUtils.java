/*******************************************************************************
 * Copyright (c) 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.util.graph.manager;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.graph.DependencyNode;

/**
 * A utility class assisting in analyzing the effects of dependency management.
 */
public final class DependencyManagerUtils
{

    /**
     * The key in the repository session's {@link RepositorySystemSession#getConfigProperties() configuration
     * properties} used to store a {@link Boolean} flag controlling the verbose mode for dependency management. If
     * enabled, the original attributes of a dependency before its update due to dependency managemnent will be recorded
     * in the node's {@link DependencyNode#getData() custom data} when building a dependency graph.
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
     * Gets the version or version range of the specified dependency node before dependency management was applied (if
     * any).
     * 
     * @param node The dependency node to retrieve the premanaged data for, must not be {@code null}.
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

    private static <T> T cast( Object obj, Class<T> type )
    {
        return type.isInstance( obj ) ? type.cast( obj ) : null;
    }

}
