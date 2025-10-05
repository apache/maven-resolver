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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.aether.graph.Exclusion;

/**
 * The management updates to apply to a dependency.
 *
 * @see DependencyManager#manageDependency(org.eclipse.aether.graph.Dependency)
 */
public final class DependencyManagement {
    /**
     * Enumeration of manageable attributes, attributes that can be subjected to dependency management.
     */
    public enum Subject {
        VERSION,
        SCOPE,
        OPTIONAL,
        PROPERTIES,
        EXCLUSIONS
    }

    private final Map<Subject, Object> managedValues;
    private final Map<Subject, Boolean> managedEnforced;

    /**
     * Creates an empty management update.
     */
    public DependencyManagement() {
        this.managedValues = new HashMap<>();
        this.managedEnforced = new HashMap<>();
    }

    public boolean isSubjectEnforced(Subject subject) {
        return managedEnforced.getOrDefault(subject, false);
    }

    /**
     * Gets the new version to apply to the dependency.
     *
     * @return The new version or {@code null} if the version is not managed and the existing dependency version should
     *         remain unchanged.
     */
    public String getVersion() {
        return (String) managedValues.get(Subject.VERSION);
    }

    /**
     * Sets the new version to apply to the dependency.
     *
     * @param version The new version, may be {@code null} if the version is not managed.
     * @return This management update for chaining, never {@code null}.
     * @deprecated Use {@link #setVersion(String, boolean)} instead.
     */
    @Deprecated
    public DependencyManagement setVersion(String version) {
        return setVersion(version, true);
    }

    /**
     * Sets the new version to apply to the dependency.
     *
     * @param version The new version, may be {@code null} if the version is not managed.
     * @param enforced The enforcement of new value.
     * @return This management update for chaining, never {@code null}.
     */
    public DependencyManagement setVersion(String version, boolean enforced) {
        if (version == null) {
            this.managedValues.remove(Subject.VERSION);
            this.managedEnforced.remove(Subject.VERSION);
        } else {
            this.managedValues.put(Subject.VERSION, version);
            this.managedEnforced.put(Subject.VERSION, enforced);
        }
        return this;
    }

    /**
     * Gets the new scope to apply to the dependency.
     *
     * @return The new scope or {@code null} if the scope is not managed and the existing dependency scope should remain
     *         unchanged.
     */
    public String getScope() {
        return (String) managedValues.get(Subject.SCOPE);
    }

    /**
     * Sets the new scope to apply to the dependency.
     *
     * @param scope The new scope, may be {@code null} if the scope is not managed.
     * @return This management update for chaining, never {@code null}.
     */
    @Deprecated
    public DependencyManagement setScope(String scope) {
        return setScope(scope, true);
    }

    /**
     * Sets the new scope to apply to the dependency.
     *
     * @param scope The new scope, may be {@code null} if the scope is not managed.
     * @param enforced The enforcement of new value.
     * @return This management update for chaining, never {@code null}.
     */
    public DependencyManagement setScope(String scope, boolean enforced) {
        if (scope == null) {
            this.managedValues.remove(Subject.SCOPE);
            this.managedEnforced.remove(Subject.SCOPE);
        } else {
            this.managedValues.put(Subject.SCOPE, scope);
            this.managedEnforced.put(Subject.SCOPE, enforced);
        }
        return this;
    }

    /**
     * Gets the new optional flag to apply to the dependency.
     *
     * @return The new optional flag or {@code null} if the flag is not managed and the existing optional flag of the
     *         dependency should remain unchanged.
     */
    public Boolean getOptional() {
        return (Boolean) managedValues.get(Subject.OPTIONAL);
    }

    /**
     * Sets the new optional flag to apply to the dependency.
     *
     * @param optional The optional flag, may be {@code null} if the flag is not managed.
     * @return This management update for chaining, never {@code null}.
     */
    @Deprecated
    public DependencyManagement setOptional(Boolean optional) {
        return setOptional(optional, true);
    }

    /**
     * Sets the new optional flag to apply to the dependency.
     *
     * @param optional The optional flag, may be {@code null} if the flag is not managed.
     * @param enforced The enforcement of new value.
     * @return This management update for chaining, never {@code null}.
     */
    public DependencyManagement setOptional(Boolean optional, boolean enforced) {
        if (optional == null) {
            this.managedValues.remove(Subject.OPTIONAL);
            this.managedEnforced.remove(Subject.OPTIONAL);
        } else {
            this.managedValues.put(Subject.OPTIONAL, optional);
            this.managedEnforced.put(Subject.OPTIONAL, enforced);
        }
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
    @SuppressWarnings("unchecked")
    public Collection<Exclusion> getExclusions() {
        return (Collection<Exclusion>) managedValues.get(Subject.EXCLUSIONS);
    }

    /**
     * Sets the new exclusions to apply to the dependency. Note that this collection denotes the complete set of
     * exclusions for the dependency, i.e. the dependency manager controls whether any existing exclusions get merged
     * with information from dependency management or overridden by it.
     *
     * @param exclusions The new exclusions, may be {@code null} if the exclusions are not managed.
     * @return This management update for chaining, never {@code null}.
     */
    @Deprecated
    public DependencyManagement setExclusions(Collection<Exclusion> exclusions) {
        return setExclusions(exclusions, true);
    }

    /**
     * Sets the new exclusions to apply to the dependency. Note that this collection denotes the complete set of
     * exclusions for the dependency, i.e. the dependency manager controls whether any existing exclusions get merged
     * with information from dependency management or overridden by it.
     *
     * @param exclusions The new exclusions, may be {@code null} if the exclusions are not managed.
     * @param enforced The enforcement of new value.
     * @return This management update for chaining, never {@code null}.
     */
    public DependencyManagement setExclusions(Collection<Exclusion> exclusions, boolean enforced) {
        if (exclusions == null) {
            this.managedValues.remove(Subject.EXCLUSIONS);
            this.managedEnforced.remove(Subject.EXCLUSIONS);
        } else {
            this.managedValues.put(Subject.EXCLUSIONS, exclusions);
            this.managedEnforced.put(Subject.EXCLUSIONS, enforced);
        }
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
    @SuppressWarnings("unchecked")
    public Map<String, String> getProperties() {
        return (Map<String, String>) managedValues.get(Subject.PROPERTIES);
    }

    /**
     * Sets the new properties to apply to the dependency. Note that this map denotes the complete set of properties,
     * i.e. the dependency manager controls whether any existing properties get merged with the information from
     * dependency management or overridden by it.
     *
     * @param properties The new artifact properties, may be {@code null} if the properties are not managed.
     * @return This management update for chaining, never {@code null}.
     */
    @Deprecated
    public DependencyManagement setProperties(Map<String, String> properties) {
        return setProperties(properties, true);
    }

    /**
     * Sets the new properties to apply to the dependency. Note that this map denotes the complete set of properties,
     * i.e. the dependency manager controls whether any existing properties get merged with the information from
     * dependency management or overridden by it.
     *
     * @param properties The new artifact properties, may be {@code null} if the properties are not managed.
     * @param enforced The enforcement of new value.
     * @return This management update for chaining, never {@code null}.
     */
    public DependencyManagement setProperties(Map<String, String> properties, boolean enforced) {
        if (properties == null) {
            this.managedValues.remove(Subject.PROPERTIES);
            this.managedEnforced.remove(Subject.PROPERTIES);
        } else {
            this.managedValues.put(Subject.PROPERTIES, properties);
            this.managedEnforced.put(Subject.PROPERTIES, enforced);
        }
        return this;
    }
}
