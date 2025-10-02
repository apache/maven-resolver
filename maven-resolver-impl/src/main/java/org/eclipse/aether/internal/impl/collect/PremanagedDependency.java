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
package org.eclipse.aether.internal.impl.collect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.DependencyManagement;
import org.eclipse.aether.collection.DependencyManager;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyManagementRule;
import org.eclipse.aether.graph.DependencyManagementSubject;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.util.graph.manager.DependencyManagerUtils;

/**
 * Helper class used during collection.
 */
public class PremanagedDependency {

    final String premanagedVersion;

    final String premanagedScope;

    final Boolean premanagedOptional;

    /**
     * @since 1.1.0
     */
    final Collection<Exclusion> premanagedExclusions;

    /**
     * @since 1.1.0
     */
    final Map<String, String> premanagedProperties;

    /**
     * @since 2.0.13
     */
    final Map<DependencyManagementSubject, Boolean> managedSubjects;

    final Dependency managedDependency;

    final boolean premanagedState;

    @SuppressWarnings("checkstyle:parameternumber")
    PremanagedDependency(
            String premanagedVersion,
            String premanagedScope,
            Boolean premanagedOptional,
            Collection<Exclusion> premanagedExclusions,
            Map<String, String> premanagedProperties,
            Map<DependencyManagementSubject, Boolean> managedSubjects,
            Dependency managedDependency,
            boolean premanagedState) {
        this.premanagedVersion = premanagedVersion;
        this.premanagedScope = premanagedScope;
        this.premanagedOptional = premanagedOptional;
        this.premanagedExclusions = premanagedExclusions != null
                ? Collections.unmodifiableCollection(new ArrayList<>(premanagedExclusions))
                : null;

        this.premanagedProperties =
                premanagedProperties != null ? Collections.unmodifiableMap(new HashMap<>(premanagedProperties)) : null;

        this.managedSubjects = managedSubjects;
        this.managedDependency = managedDependency;
        this.premanagedState = premanagedState;
    }

    public static PremanagedDependency create(
            DependencyManager depManager,
            Dependency dependency,
            boolean disableVersionManagement,
            boolean premanagedState) {
        DependencyManagement management = depManager != null ? depManager.manageDependency(dependency) : null;

        Map<DependencyManagementSubject, Boolean> managedSubjects = new HashMap<>();
        String premanagedVersion = null;
        String premanagedScope = null;
        Boolean premanagedOptional = null;
        Collection<Exclusion> premanagedExclusions = null;
        Map<String, String> premanagedProperties = null;

        if (management != null) {
            if (management.getRules() != null) {
                for (DependencyManagementRule<?> rule : management.getRules()) {
                    DependencyManagementSubject subject = rule.getSubject();
                    if (subject == DependencyManagementSubject.VERSION) {
                        if (disableVersionManagement) {
                            continue;
                        }
                        premanagedVersion = dependency.getArtifact().getVersion();
                    } else if (subject == DependencyManagementSubject.SCOPE) {
                        premanagedScope = dependency.getScope();
                    } else if (subject == DependencyManagementSubject.OPTIONAL) {
                        premanagedOptional = dependency.isOptional();
                    } else if (subject == DependencyManagementSubject.PROPERTIES) {
                        premanagedProperties = dependency.getArtifact().getProperties();
                    } else if (subject == DependencyManagementSubject.EXCLUSIONS) {
                        premanagedExclusions = dependency.getExclusions();
                    } else {
                        throw new IllegalArgumentException("unknown subject " + subject);
                    }
                    dependency = rule.apply(dependency);
                    managedSubjects.put(subject, rule.isEnforcing());
                }
            } else {
                // legacy path: here there are no means to distinguish "management" as enforced or advised (so all are
                // enforced)
                if (management.getVersion() != null && !disableVersionManagement) {
                    Artifact artifact = dependency.getArtifact();
                    premanagedVersion = artifact.getVersion();
                    dependency = dependency.setArtifact(artifact.setVersion(management.getVersion()));
                    managedSubjects.put(DependencyManagementSubject.VERSION, true);
                }
                if (management.getProperties() != null) {
                    Artifact artifact = dependency.getArtifact();
                    premanagedProperties = artifact.getProperties();
                    dependency = dependency.setArtifact(artifact.setProperties(management.getProperties()));
                    managedSubjects.put(DependencyManagementSubject.PROPERTIES, true);
                }
                if (management.getScope() != null) {
                    premanagedScope = dependency.getScope();
                    dependency = dependency.setScope(management.getScope());
                    managedSubjects.put(DependencyManagementSubject.SCOPE, true);
                }
                if (management.getOptional() != null) {
                    premanagedOptional = dependency.isOptional();
                    dependency = dependency.setOptional(management.getOptional());
                    managedSubjects.put(DependencyManagementSubject.OPTIONAL, true);
                }
                if (management.getExclusions() != null) {
                    premanagedExclusions = dependency.getExclusions();
                    dependency = dependency.setExclusions(management.getExclusions());
                    managedSubjects.put(DependencyManagementSubject.EXCLUSIONS, true);
                }
            }
        }

        return new PremanagedDependency(
                premanagedVersion,
                premanagedScope,
                premanagedOptional,
                premanagedExclusions,
                premanagedProperties,
                managedSubjects,
                dependency,
                premanagedState);
    }

    public Dependency getManagedDependency() {
        return managedDependency;
    }

    public void applyTo(DefaultDependencyNode child) {
        child.setManagedSubjects(managedSubjects);
        if (premanagedState) {
            child.setData(DependencyManagerUtils.NODE_DATA_PREMANAGED_VERSION, premanagedVersion);
            child.setData(DependencyManagerUtils.NODE_DATA_PREMANAGED_SCOPE, premanagedScope);
            child.setData(DependencyManagerUtils.NODE_DATA_PREMANAGED_OPTIONAL, premanagedOptional);
            child.setData(DependencyManagerUtils.NODE_DATA_PREMANAGED_EXCLUSIONS, premanagedExclusions);
            child.setData(DependencyManagerUtils.NODE_DATA_PREMANAGED_PROPERTIES, premanagedProperties);
        }
    }
}
