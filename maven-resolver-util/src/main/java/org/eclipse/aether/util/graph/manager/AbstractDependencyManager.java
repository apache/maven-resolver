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
package org.eclipse.aether.util.graph.manager;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencyManagement;
import org.eclipse.aether.collection.DependencyManager;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.scope.ScopeManager;
import org.eclipse.aether.scope.SystemDependencyScope;

import static java.util.Objects.requireNonNull;

/**
 * A dependency manager support class.
 *
 * @since 2.0.0
 */
public abstract class AbstractDependencyManager implements DependencyManager {

    protected final int depth;

    protected final int deriveUntil;

    protected final int applyFrom;

    protected final Map<Object, String> managedVersions;

    protected final Map<Object, String> managedScopes;

    protected final Map<Object, Boolean> managedOptionals;

    protected final Map<Object, String> managedLocalPaths;

    protected final Map<Object, Collection<Exclusion>> managedExclusions;

    protected final SystemDependencyScope systemDependencyScope;

    protected final DependencyCollectionContext currentContext;

    private final int hashCode;

    protected AbstractDependencyManager(int deriveUntil, int applyFrom, ScopeManager scopeManager) {
        this(
                0,
                deriveUntil,
                applyFrom,
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                scopeManager != null
                        ? scopeManager.getSystemDependencyScope().orElse(null)
                        : SystemDependencyScope.LEGACY,
                null);
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    protected AbstractDependencyManager(
            int depth,
            int deriveUntil,
            int applyFrom,
            Map<Object, String> managedVersions,
            Map<Object, String> managedScopes,
            Map<Object, Boolean> managedOptionals,
            Map<Object, String> managedLocalPaths,
            Map<Object, Collection<Exclusion>> managedExclusions,
            SystemDependencyScope systemDependencyScope,
            DependencyCollectionContext currentContext) {
        this.depth = depth;
        this.deriveUntil = deriveUntil;
        this.applyFrom = applyFrom;
        this.managedVersions = requireNonNull(managedVersions);
        this.managedScopes = requireNonNull(managedScopes);
        this.managedOptionals = requireNonNull(managedOptionals);
        this.managedLocalPaths = requireNonNull(managedLocalPaths);
        this.managedExclusions = requireNonNull(managedExclusions);
        // nullable: if using scope manager, but there is no system scope defined
        this.systemDependencyScope = systemDependencyScope;
        // nullable until applicable, then "lock step" below
        this.currentContext = currentContext;

        this.hashCode = Objects.hash(
                depth,
                deriveUntil,
                applyFrom,
                managedVersions,
                managedScopes,
                managedOptionals,
                managedLocalPaths,
                managedExclusions);
    }

    protected abstract DependencyManager newInstance(
            Map<Object, String> managedVersions,
            Map<Object, String> managedScopes,
            Map<Object, Boolean> managedOptionals,
            Map<Object, String> managedLocalPaths,
            Map<Object, Collection<Exclusion>> managedExclusions,
            DependencyCollectionContext currentContext);

    @Override
    public DependencyManager deriveChildManager(DependencyCollectionContext context) {
        requireNonNull(context, "context cannot be null");
        if (!isDerived()) {
            return this;
        }

        if (!isApplied() || currentContext == null) {
            return derive(context, context); // original behaviour
        } else {
            return derive(currentContext, context); // clic-clac: defer application to children; not onto own deps
        }
    }

    protected DependencyManager derive(
            DependencyCollectionContext currentContext, DependencyCollectionContext nextContext) {
        Map<Object, String> managedVersions = this.managedVersions;
        Map<Object, String> managedScopes = this.managedScopes;
        Map<Object, Boolean> managedOptionals = this.managedOptionals;
        Map<Object, String> managedLocalPaths = this.managedLocalPaths;
        Map<Object, Collection<Exclusion>> managedExclusions = this.managedExclusions;

        for (Dependency managedDependency : currentContext.getManagedDependencies()) {
            Artifact artifact = managedDependency.getArtifact();
            Object key = new Key(artifact);

            String version = artifact.getVersion();
            if (!version.isEmpty() && !managedVersions.containsKey(key)) {
                if (managedVersions == this.managedVersions) {
                    managedVersions = new HashMap<>(this.managedVersions);
                }
                managedVersions.put(key, version);
            }

            String scope = managedDependency.getScope();
            if (!scope.isEmpty() && !managedScopes.containsKey(key)) {
                if (managedScopes == this.managedScopes) {
                    managedScopes = new HashMap<>(this.managedScopes);
                }
                managedScopes.put(key, scope);
            }

            Boolean optional = managedDependency.getOptional();
            if (optional != null && !managedOptionals.containsKey(key)) {
                if (managedOptionals == this.managedOptionals) {
                    managedOptionals = new HashMap<>(this.managedOptionals);
                }
                managedOptionals.put(key, optional);
            }

            String localPath = systemDependencyScope == null
                    ? null
                    : systemDependencyScope.getSystemPath(managedDependency.getArtifact());
            if (localPath != null && !managedLocalPaths.containsKey(key)) {
                if (managedLocalPaths == this.managedLocalPaths) {
                    managedLocalPaths = new HashMap<>(this.managedLocalPaths);
                }
                managedLocalPaths.put(key, localPath);
            }

            Collection<Exclusion> exclusions = managedDependency.getExclusions();
            if (!exclusions.isEmpty()) {
                if (managedExclusions == this.managedExclusions) {
                    managedExclusions = new HashMap<>(this.managedExclusions);
                }
                Collection<Exclusion> managed = managedExclusions.computeIfAbsent(key, k -> new LinkedHashSet<>());
                managed.addAll(exclusions);
            }
        }

        return newInstance(
                managedVersions, managedScopes, managedOptionals, managedLocalPaths, managedExclusions, nextContext);
    }

    @Override
    public DependencyManagement manageDependency(Dependency dependency) {
        requireNonNull(dependency, "dependency cannot be null");
        DependencyManagement management = null;
        Object key = new Key(dependency.getArtifact());

        if (isApplied()) {
            String version = managedVersions.get(key);
            if (version != null) {
                management = new DependencyManagement();
                management.setVersion(version);
            }

            String scope = managedScopes.get(key);
            if (scope != null) {
                if (management == null) {
                    management = new DependencyManagement();
                }
                management.setScope(scope);

                if (systemDependencyScope != null
                        && !systemDependencyScope.is(scope)
                        && systemDependencyScope.getSystemPath(dependency.getArtifact()) != null) {
                    Map<String, String> properties =
                            new HashMap<>(dependency.getArtifact().getProperties());
                    systemDependencyScope.setSystemPath(properties, null);
                    management.setProperties(properties);
                }
            }

            if (systemDependencyScope != null
                    && (systemDependencyScope.is(scope)
                            || (scope == null && systemDependencyScope.is(dependency.getScope())))) {
                String localPath = managedLocalPaths.get(key);
                if (localPath != null) {
                    if (management == null) {
                        management = new DependencyManagement();
                    }
                    Map<String, String> properties =
                            new HashMap<>(dependency.getArtifact().getProperties());
                    systemDependencyScope.setSystemPath(properties, localPath);
                    management.setProperties(properties);
                }
            }

            Boolean optional = managedOptionals.get(key);
            if (optional != null) {
                if (management == null) {
                    management = new DependencyManagement();
                }
                management.setOptional(optional);
            }
        }

        Collection<Exclusion> exclusions = managedExclusions.get(key);
        if (exclusions != null) {
            if (management == null) {
                management = new DependencyManagement();
            }
            Collection<Exclusion> result = new LinkedHashSet<>(dependency.getExclusions());
            result.addAll(exclusions);
            management.setExclusions(result);
        }

        return management;
    }

    /**
     * Returns {@code true} if current context should be factored in (collected/derived).
     */
    protected boolean isDerived() {
        return depth < deriveUntil;
    }

    /**
     * Returns {@code true} if current dependency should be managed according to so far collected/derived rules.
     */
    protected boolean isApplied() {
        return depth >= applyFrom;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (null == obj || !getClass().equals(obj.getClass())) {
            return false;
        }

        AbstractDependencyManager that = (AbstractDependencyManager) obj;
        return depth == that.depth
                && deriveUntil == that.deriveUntil
                && applyFrom == that.applyFrom
                && managedVersions.equals(that.managedVersions)
                && managedScopes.equals(that.managedScopes)
                && managedOptionals.equals(that.managedOptionals)
                && managedExclusions.equals(that.managedExclusions);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    protected static class Key {

        private final Artifact artifact;

        private final int hashCode;

        Key(Artifact artifact) {
            this.artifact = artifact;
            this.hashCode = Objects.hash(artifact.getGroupId(), artifact.getArtifactId());
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            } else if (!(obj instanceof Key)) {
                return false;
            }
            Key that = (Key) obj;
            return artifact.getArtifactId().equals(that.artifact.getArtifactId())
                    && artifact.getGroupId().equals(that.artifact.getGroupId())
                    && artifact.getExtension().equals(that.artifact.getExtension())
                    && artifact.getClassifier().equals(that.artifact.getClassifier());
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public String toString() {
            return String.valueOf(artifact);
        }
    }
}
