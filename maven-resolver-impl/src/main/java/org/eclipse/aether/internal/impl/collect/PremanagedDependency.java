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
    final Map<DependencyManagement.Subject, Boolean> managedSubjects;

    final Dependency managedDependency;

    final boolean premanagedState;

    @SuppressWarnings("checkstyle:parameternumber")
    PremanagedDependency(
            String premanagedVersion,
            String premanagedScope,
            Boolean premanagedOptional,
            Collection<Exclusion> premanagedExclusions,
            Map<String, String> premanagedProperties,
            Map<DependencyManagement.Subject, Boolean> managedSubjects,
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
        DependencyManagement depMngt = depManager != null ? depManager.manageDependency(dependency) : null;

        Map<DependencyManagement.Subject, Boolean> managedSubjects = new HashMap<>();
        String premanagedVersion = null;
        String premanagedScope = null;
        Boolean premanagedOptional = null;
        Collection<Exclusion> premanagedExclusions = null;
        Map<String, String> premanagedProperties = null;

        if (depMngt != null) {
            if (depMngt.getVersion() != null && !disableVersionManagement) {
                Artifact artifact = dependency.getArtifact();
                premanagedVersion = artifact.getVersion();
                dependency = dependency.setArtifact(artifact.setVersion(depMngt.getVersion()));
                managedSubjects.put(
                        DependencyManagement.Subject.VERSION,
                        depMngt.isSubjectEnforced(DependencyManagement.Subject.VERSION));
            }
            if (depMngt.getProperties() != null) {
                Artifact artifact = dependency.getArtifact();
                premanagedProperties = artifact.getProperties();
                dependency = dependency.setArtifact(artifact.setProperties(depMngt.getProperties()));
                managedSubjects.put(
                        DependencyManagement.Subject.PROPERTIES,
                        depMngt.isSubjectEnforced(DependencyManagement.Subject.PROPERTIES));
            }
            if (depMngt.getScope() != null) {
                premanagedScope = dependency.getScope();
                dependency = dependency.setScope(depMngt.getScope());
                managedSubjects.put(
                        DependencyManagement.Subject.SCOPE,
                        depMngt.isSubjectEnforced(DependencyManagement.Subject.SCOPE));
            }
            if (depMngt.getOptional() != null) {
                premanagedOptional = dependency.isOptional();
                dependency = dependency.setOptional(depMngt.getOptional());
                managedSubjects.put(
                        DependencyManagement.Subject.OPTIONAL,
                        depMngt.isSubjectEnforced(DependencyManagement.Subject.OPTIONAL));
            }
            if (depMngt.getExclusions() != null) {
                premanagedExclusions = dependency.getExclusions();
                dependency = dependency.setExclusions(depMngt.getExclusions());
                managedSubjects.put(
                        DependencyManagement.Subject.EXCLUSIONS,
                        depMngt.isSubjectEnforced(DependencyManagement.Subject.EXCLUSIONS));
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
