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

import java.util.ArrayList;
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
 * <p>
 * This implementation is Maven specific, as it works hand-in-hand along with Maven ModelBuilder. While model builder
 * handles dependency management in the context of single POM (inheritance, imports, etc.), this implementation carries
 * in-lineage modifications based on previously recorded dependency management rules sourced from ascendants while
 * building the dependency graph. Root sourced management rules are special, in a way they are always applied, while
 * en-route collected ones are carefully applied to proper descendants only, to not override work done by model
 * builder already.
 * <p>
 * Details: Model builder handles version and scope from own dependency management (think "effective POM"). On the other
 * hand it does not handle optional, system paths and exclusions (exclusions are additional information not effective
 * or applied in same POM). Hence, this implementation makes sure, that version and scope are not applied onto same
 * node that actually provided the rules. It achieves this goal by tracking "depth" for each collected rule and ignoring
 * rules coming from same depth as processed dependency node is.
 *
 * @since 2.0.0
 */
public abstract class AbstractDependencyManager implements DependencyManager {

    protected final int depth;

    protected final int deriveUntil;

    protected final int applyFrom;

    protected final Map<Object, Holder<String>> managedVersions;

    protected final Map<Object, Holder<String>> managedScopes;

    protected final Map<Object, Holder<Boolean>> managedOptionals;

    protected final Map<Object, Holder<String>> managedLocalPaths;

    protected final Map<Object, Collection<Holder<Collection<Exclusion>>>> managedExclusions;

    protected final SystemDependencyScope systemDependencyScope;

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
                        : SystemDependencyScope.LEGACY);
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    protected AbstractDependencyManager(
            int depth,
            int deriveUntil,
            int applyFrom,
            Map<Object, Holder<String>> managedVersions,
            Map<Object, Holder<String>> managedScopes,
            Map<Object, Holder<Boolean>> managedOptionals,
            Map<Object, Holder<String>> managedLocalPaths,
            Map<Object, Collection<Holder<Collection<Exclusion>>>> managedExclusions,
            SystemDependencyScope systemDependencyScope) {
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
            Map<Object, Holder<String>> managedVersions,
            Map<Object, Holder<String>> managedScopes,
            Map<Object, Holder<Boolean>> managedOptionals,
            Map<Object, Holder<String>> managedLocalPaths,
            Map<Object, Collection<Holder<Collection<Exclusion>>>> managedExclusions);

    @Override
    public DependencyManager deriveChildManager(DependencyCollectionContext context) {
        requireNonNull(context, "context cannot be null");
        if (!isDerived()) {
            return this;
        }

        Map<Object, Holder<String>> managedVersions = this.managedVersions;
        Map<Object, Holder<String>> managedScopes = this.managedScopes;
        Map<Object, Holder<Boolean>> managedOptionals = this.managedOptionals;
        Map<Object, Holder<String>> managedLocalPaths = this.managedLocalPaths;
        Map<Object, Collection<Holder<Collection<Exclusion>>>> managedExclusions = this.managedExclusions;

        for (Dependency managedDependency : context.getManagedDependencies()) {
            Artifact artifact = managedDependency.getArtifact();
            Object key = new Key(artifact);

            String version = artifact.getVersion();
            if (!version.isEmpty() && !managedVersions.containsKey(key)) {
                if (managedVersions == this.managedVersions) {
                    managedVersions = new HashMap<>(this.managedVersions);
                }
                managedVersions.put(key, new Holder<>(depth, version));
            }

            String scope = managedDependency.getScope();
            if (!scope.isEmpty() && !managedScopes.containsKey(key)) {
                if (managedScopes == this.managedScopes) {
                    managedScopes = new HashMap<>(this.managedScopes);
                }
                managedScopes.put(key, new Holder<>(depth, scope));
            }

            Boolean optional = managedDependency.getOptional();
            if (optional != null && !managedOptionals.containsKey(key)) {
                if (managedOptionals == this.managedOptionals) {
                    managedOptionals = new HashMap<>(this.managedOptionals);
                }
                managedOptionals.put(key, new Holder<>(depth, optional));
            }

            String localPath = systemDependencyScope == null
                    ? null
                    : systemDependencyScope.getSystemPath(managedDependency.getArtifact());
            if (localPath != null && !managedLocalPaths.containsKey(key)) {
                if (managedLocalPaths == this.managedLocalPaths) {
                    managedLocalPaths = new HashMap<>(this.managedLocalPaths);
                }
                managedLocalPaths.put(key, new Holder<>(depth, localPath));
            }

            Collection<Exclusion> exclusions = managedDependency.getExclusions();
            if (!exclusions.isEmpty()) {
                if (managedExclusions == this.managedExclusions) {
                    managedExclusions = new HashMap<>(this.managedExclusions);
                }
                Collection<Holder<Collection<Exclusion>>> managed =
                        managedExclusions.computeIfAbsent(key, k -> new ArrayList<>());
                managed.add(new Holder<>(depth, exclusions));
            }
        }

        return newInstance(managedVersions, managedScopes, managedOptionals, managedLocalPaths, managedExclusions);
    }

    @Override
    public DependencyManagement manageDependency(Dependency dependency) {
        requireNonNull(dependency, "dependency cannot be null");
        DependencyManagement management = null;
        Object key = new Key(dependency.getArtifact());

        if (isApplied()) {
            Holder<String> version = managedVersions.get(key);
            // is managed locally by model builder
            // apply only rules coming from "higher" levels
            if (version != null && isApplicable(version)) {
                management = new DependencyManagement();
                management.setVersion(version.getValue());
            }

            Holder<String> scope = managedScopes.get(key);
            // is managed locally by model builder
            // apply only rules coming from "higher" levels
            if (scope != null && isApplicable(scope)) {
                if (management == null) {
                    management = new DependencyManagement();
                }
                management.setScope(scope.getValue());

                if (systemDependencyScope != null
                        && !systemDependencyScope.is(scope.getValue())
                        && systemDependencyScope.getSystemPath(dependency.getArtifact()) != null) {
                    Map<String, String> properties =
                            new HashMap<>(dependency.getArtifact().getProperties());
                    systemDependencyScope.setSystemPath(properties, null);
                    management.setProperties(properties);
                }
            }

            // system scope paths always applied to have them aligned
            // (same artifact > same path) in whole graph
            if (systemDependencyScope != null
                    && (scope != null && systemDependencyScope.is(scope.getValue())
                            || (scope == null && systemDependencyScope.is(dependency.getScope())))) {
                Holder<String> localPath = managedLocalPaths.get(key);
                if (localPath != null) {
                    if (management == null) {
                        management = new DependencyManagement();
                    }
                    Map<String, String> properties =
                            new HashMap<>(dependency.getArtifact().getProperties());
                    systemDependencyScope.setSystemPath(properties, localPath.getValue());
                    management.setProperties(properties);
                }
            }

            // optional is not managed by model builder
            // apply only rules coming from "higher" levels
            Holder<Boolean> optional = managedOptionals.get(key);
            if (optional != null && isApplicable(optional)) {
                if (management == null) {
                    management = new DependencyManagement();
                }
                management.setOptional(optional.getValue());
            }
        }

        // exclusions affect only downstream
        // this will not "exclude" own dependency,
        // is just added as additional information
        Collection<Holder<Collection<Exclusion>>> exclusions = managedExclusions.get(key);
        if (exclusions != null) {
            if (management == null) {
                management = new DependencyManagement();
            }
            Collection<Exclusion> result = new LinkedHashSet<>(dependency.getExclusions());
            for (Holder<Collection<Exclusion>> exclusion : exclusions) {
                result.addAll(exclusion.getValue());
            }
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

    /**
     * Returns {@code true} if rule in holder is applicable at current depth.
     */
    protected boolean isApplicable(Holder<?> holder) {
        // explanation: derive collects rules (at given depth) and then last
        // call newInstance does depth++. This means that distance 1 is still "same node".
        // Hence, rules from depth - 2 or above should be applied.
        // root is special: is always applied.
        return holder.getDepth() == 0 || depth > holder.getDepth() + 1;
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

    protected static class Holder<T> {
        private final int depth;
        private final T value;

        Holder(int depth, T value) {
            this.depth = depth;
            this.value = requireNonNull(value);
        }

        public int getDepth() {
            return depth;
        }

        public T getValue() {
            return value;
        }
    }
}
