/*******************************************************************************
 * Copyright (c) 2010, 2011 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.collection;

import java.util.Collection;
import java.util.Map;

import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;

/**
 * The management updates to apply to a dependency.
 * 
 * @see DependencyManager#manageDependency(Dependency)
 */
public final class DependencyManagement
{

    private String version;

    private String scope;

    private Collection<Exclusion> exclusions;

    private Map<String, String> properties;

    /**
     * Creates an empty management update.
     */
    public DependencyManagement()
    {
        // enables default constructor
    }

    /**
     * Gets the new version to apply to the dependency.
     * 
     * @return The new version or {@code null} if the version is not managed and the existing dependency version should
     *         remain unchanged.
     */
    public String getVersion()
    {
        return version;
    }

    /**
     * Sets the new version to apply to the dependency.
     * 
     * @param version The new version, may be {@code null} if the version is not managed.
     * @return This management update for chaining, never {@code null}.
     */
    public DependencyManagement setVersion( String version )
    {
        this.version = version;
        return this;
    }

    /**
     * Gets the new scope to apply to the dependency.
     * 
     * @return The new scope or {@code null} if the scope is not managed and the existing dependency scope should remain
     *         unchanged.
     */
    public String getScope()
    {
        return scope;
    }

    /**
     * Sets the new scope to apply to the dependency.
     * 
     * @param scope The new scope, may be {@code null} if the scope is not managed.
     * @return This management update for chaining, never {@code null}.
     */
    public DependencyManagement setScope( String scope )
    {
        this.scope = scope;
        return this;
    }

    /**
     * Gets the new exclusions to apply to the dependency. Note that this collection denotes the complete set of
     * exclusions for the dependency, i.e. the dependency manager controls whether any existing exclusions get merged
     * with information from dependency management or overridden by it.
     * 
     * @return The new exclusions or {@code null} if the exclusions are not managed and the existing dependency
     *         exclusions should remain unchanged.
     */
    public Collection<Exclusion> getExclusions()
    {
        return exclusions;
    }

    /**
     * Sets the new exclusions to apply to the dependency. Note that this collection denotes the complete set of
     * exclusions for the dependency, i.e. the dependency manager controls whether any existing exclusions get merged
     * with information from dependency management or overridden by it.
     * 
     * @param exclusions The new exclusions, may be {@code null} if the exclusions are not managed.
     * @return This management update for chaining, never {@code null}.
     */
    public DependencyManagement setExclusions( Collection<Exclusion> exclusions )
    {
        this.exclusions = exclusions;
        return this;
    }

    /**
     * Gets the new properties to apply to the dependency. Note that this map denotes the complete set of properties,
     * i.e. the dependency manager controls whether any existing properties get merged with the information from
     * dependency management or overridden by it.
     * 
     * @return The new artifact properties or {@code null} if the properties are not managed and the existing properties
     *         should remain unchanged.
     */
    public Map<String, String> getProperties()
    {
        return properties;
    }

    /**
     * Sets the new properties to apply to the dependency. Note that this map denotes the complete set of properties,
     * i.e. the dependency manager controls whether any existing properties get merged with the information from
     * dependency management or overridden by it.
     * 
     * @param properties The new artifact properties, may be {@code null} if the properties are not managed.
     * @return This management update for chaining, never {@code null}.
     */
    public DependencyManagement setProperties( Map<String, String> properties )
    {
        this.properties = properties;
        return this;
    }

}
