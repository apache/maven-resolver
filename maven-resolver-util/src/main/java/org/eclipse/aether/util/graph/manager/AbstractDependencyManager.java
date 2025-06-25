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
import java.util.HashMap;
import java.util.LinkedHashSet;
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
 * Details: Model builder handles version, scope from own dependency management (think "effective POM"). On the other
 * hand it does not handle optional for example. System paths are aligned across whole graph, making sure there is
 * same system path used by same dependency. Finally, exclusions (exclusions are additional information not effective
 * or applied in same POM) are always applied. This implementation makes sure, that version and scope are not applied
 * onto same node that actually provided the rules, to no override work that ModelBuilder did. It achieves this goal
 * by tracking "depth" for each collected rule and ignoring rules coming from same depth as processed dependency node is.
 * <p>
 * Note for future: the field {@code managedLocalPaths} is <em>intentionally left out of hash/equals</em>, with
 * reason explained above.
 *
 * @since 2.0.0
 */
public abstract class AbstractDependencyManager implements DependencyManager {

    protected final int depth;

    protected final int deriveUntil;

    protected final int applyFrom;

    protected final MMap<Key, Holder<String>> managedVersions;

    protected final MMap<Key, Holder<String>> managedScopes;

    protected final MMap<Key, Holder<Boolean>> managedOptionals;

    protected final MMap<Key, Holder<String>> managedLocalPaths;

    protected final MMap<Key, Collection<Holder<Collection<Exclusion>>>> managedExclusions;

    protected final SystemDependencyScope systemDependencyScope;

    private final int hashCode;

    protected AbstractDependencyManager(int deriveUntil, int applyFrom, ScopeManager scopeManager) {
        this(
                0,
                deriveUntil,
                applyFrom,
                MMap.empty(),
                MMap.empty(),
                MMap.empty(),
                MMap.empty(),
                MMap.empty(),
                scopeManager != null
                        ? scopeManager.getSystemDependencyScope().orElse(null)
                        : SystemDependencyScope.LEGACY);
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    protected AbstractDependencyManager(
            int depth,
            int deriveUntil,
            int applyFrom,
            MMap<Key, Holder<String>> managedVersions,
            MMap<Key, Holder<String>> managedScopes,
            MMap<Key, Holder<Boolean>> managedOptionals,
            MMap<Key, Holder<String>> managedLocalPaths,
            MMap<Key, Collection<Holder<Collection<Exclusion>>>> managedExclusions,
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

        // exclude managedLocalPaths
        this.hashCode = Objects.hash(depth, managedVersions, managedScopes, managedOptionals, managedExclusions);
    }

    protected abstract DependencyManager newInstance(
            MMap<Key, Holder<String>> managedVersions,
            MMap<Key, Holder<String>> managedScopes,
            MMap<Key, Holder<Boolean>> managedOptionals,
            MMap<Key, Holder<String>> managedLocalPaths,
            MMap<Key, Collection<Holder<Collection<Exclusion>>>> managedExclusions);

    @Override
    public DependencyManager deriveChildManager(DependencyCollectionContext context) {
        requireNonNull(context, "context cannot be null");
        if (!isDerived()) {
            return this;
        }

        MMap<Key, Holder<String>> managedVersions = this.managedVersions;
        MMap<Key, Holder<String>> managedScopes = this.managedScopes;
        MMap<Key, Holder<Boolean>> managedOptionals = this.managedOptionals;
        MMap<Key, Holder<String>> managedLocalPaths = this.managedLocalPaths;
        MMap<Key, Collection<Holder<Collection<Exclusion>>>> managedExclusions = this.managedExclusions;

        for (Dependency managedDependency : context.getManagedDependencies()) {
            Artifact artifact = managedDependency.getArtifact();
            Key key = new Key(artifact);

            String version = artifact.getVersion();
            if (!version.isEmpty() && !managedVersions.containsKey(key)) {
                if (managedVersions == this.managedVersions) {
                    managedVersions = MMap.copy(this.managedVersions);
                }
                managedVersions.put(key, new Holder<>(depth, version));
            }

            String scope = managedDependency.getScope();
            if (!scope.isEmpty() && !managedScopes.containsKey(key)) {
                if (managedScopes == this.managedScopes) {
                    managedScopes = MMap.copy(this.managedScopes);
                }
                managedScopes.put(key, new Holder<>(depth, scope));
            }

            Boolean optional = managedDependency.getOptional();
            if (optional != null && !managedOptionals.containsKey(key)) {
                if (managedOptionals == this.managedOptionals) {
                    managedOptionals = MMap.copy(this.managedOptionals);
                }
                managedOptionals.put(key, new Holder<>(depth, optional));
            }

            String localPath = systemDependencyScope == null
                    ? null
                    : systemDependencyScope.getSystemPath(managedDependency.getArtifact());
            if (localPath != null && !managedLocalPaths.containsKey(key)) {
                if (managedLocalPaths == this.managedLocalPaths) {
                    managedLocalPaths = MMap.copy(this.managedLocalPaths);
                }
                managedLocalPaths.put(key, new Holder<>(depth, localPath));
            }

            Collection<Exclusion> exclusions = managedDependency.getExclusions();
            if (!exclusions.isEmpty()) {
                if (managedExclusions == this.managedExclusions) {
                    managedExclusions = MMap.copyWithKey(key, this.managedExclusions);
                }
                Collection<Holder<Collection<Exclusion>>> managed = managedExclusions.get(key);
                if (managed == null) {
                    managed = new ArrayList<>();
                    managedExclusions.put(key, managed);
                }
                managed.add(new Holder<>(depth, exclusions));
            }
        }

        return newInstance(
                managedVersions.done(),
                managedScopes.done(),
                managedOptionals.done(),
                managedLocalPaths.done(),
                managedExclusions.done());
    }

    @Override
    public DependencyManagement manageDependency(Dependency dependency) {
        requireNonNull(dependency, "dependency cannot be null");
        DependencyManagement management = null;
        Key key = new Key(dependency.getArtifact());

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
                    HashMap<String, String> properties =
                            new HashMap<>(dependency.getArtifact().getProperties());
                    systemDependencyScope.setSystemPath(properties, null);
                    management.setProperties(properties);
                }
            }

            // system scope paths always applied to have them aligned
            // (same artifact == same path) in whole graph
            if (systemDependencyScope != null
                    && (scope != null && systemDependencyScope.is(scope.getValue())
                            || (scope == null && systemDependencyScope.is(dependency.getScope())))) {
                Holder<String> localPath = managedLocalPaths.get(key);
                if (localPath != null) {
                    if (management == null) {
                        management = new DependencyManagement();
                    }
                    HashMap<String, String> properties =
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
        // ModelBuilder does not merge exclusions (only applies if dependency does not have exclusion)
        // so we merge it here even from same level
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
        // exclude managedLocalPaths
        return depth == that.depth
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
            this.hashCode = Objects.hash(
                    artifact.getArtifactId(), artifact.getGroupId(), artifact.getExtension(), artifact.getClassifier());
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
        private final int hashCode;

        Holder(int depth, T value) {
            this.depth = depth;
            this.value = requireNonNull(value);
            this.hashCode = Objects.hash(depth, value);
        }

        public int getDepth() {
            return depth;
        }

        public T getValue() {
            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Holder)) {
                return false;
            }
            Holder<?> holder = (Holder<?>) o;
            return depth == holder.depth && Objects.equals(value, holder.value);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}
