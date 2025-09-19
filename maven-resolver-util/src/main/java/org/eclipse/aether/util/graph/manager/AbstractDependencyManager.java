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
 * <p>
 * Implementation note for all managers extending this class: this class maintains "path" (list of parent managers)
 * and "depth". Depth {@code 0} is used as "empty factory" on session; is the instance created during session
 * creation and is just parameterized. Collection begins with "derive" operation using root context, ending with new
 * manager instance having depth 1 and root management information. Using this instance first level dependencies are
 * processed, then again, each first level dependency "derives" instance with own context, to process their own second
 * level dependencies and so on.
 * <p>
 * Depth 1 is special for "version", "scope" and "optional" properties, as at that level we do "apply onto itself"
 * to ensure root defined rules are being applied to first level siblings (which, if are managed by model builder will
 * be same, hence work will be no-op). Below depth 1 this does not stand, as "apply onto itself" is not in effect anymore
 * but only "apply below" is used.
 * <p>
 * The rules are keyed by dependency management entry coordinates ({@code GACE}, see {@link Key}) and are recorded only, if
 * previously a rule for same key did not exist yet. This implements the "nearer (to root) management wins" rule,
 * while root management overrides all.
 * <p>
 * Worth mentioning, that if a {@link org.eclipse.aether.graph.DependencyNode} becomes "managed" by any property
 * provided from this manager, the {@link org.eclipse.aether.graph.DependencyNode#getManagedBits()} will
 * carry this information for given property, and later on, graph transformation will abstain modifying these properties
 * of marked nodes in any way (assuming the node "is having already set property to what it should have"). Sometimes,
 * this is unwanted, especially for properties that in graph needs to be inherited (value is derived from parent-child
 * context of actual node, like "scope" or "optional" is).
 *
 *
 * @since 2.0.0
 */
public abstract class AbstractDependencyManager implements DependencyManager {
    protected final ArrayList<AbstractDependencyManager> path;

    protected final int depth;

    protected final int deriveUntil;

    protected final int applyFrom;

    protected final MMap<Key, String> managedVersions;

    protected final MMap<Key, String> managedScopes;

    protected final MMap<Key, Boolean> managedOptionals;

    protected final MMap<Key, String> managedLocalPaths;

    protected final MMap<Key, Holder<Collection<Exclusion>>> managedExclusions;

    protected final SystemDependencyScope systemDependencyScope;

    private final int hashCode;

    protected AbstractDependencyManager(int deriveUntil, int applyFrom, ScopeManager scopeManager) {
        this(
                new ArrayList<>(),
                0,
                deriveUntil,
                applyFrom,
                null,
                null,
                null,
                null,
                null,
                scopeManager != null
                        ? scopeManager.getSystemDependencyScope().orElse(null)
                        : SystemDependencyScope.LEGACY);
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    protected AbstractDependencyManager(
            ArrayList<AbstractDependencyManager> path,
            int depth,
            int deriveUntil,
            int applyFrom,
            MMap<Key, String> managedVersions,
            MMap<Key, String> managedScopes,
            MMap<Key, Boolean> managedOptionals,
            MMap<Key, String> managedLocalPaths,
            MMap<Key, Holder<Collection<Exclusion>>> managedExclusions,
            SystemDependencyScope systemDependencyScope) {
        this.path = path;
        this.depth = depth;
        this.deriveUntil = deriveUntil;
        this.applyFrom = applyFrom;
        this.managedVersions = managedVersions;
        this.managedScopes = managedScopes;
        this.managedOptionals = managedOptionals;
        this.managedLocalPaths = managedLocalPaths;
        this.managedExclusions = managedExclusions;
        // nullable: if using scope manager, but there is no system scope defined
        this.systemDependencyScope = systemDependencyScope;

        // exclude managedLocalPaths
        this.hashCode = Objects.hash(path, depth, managedVersions, managedScopes, managedOptionals, managedExclusions);
    }

    protected abstract DependencyManager newInstance(
            MMap<Key, String> managedVersions,
            MMap<Key, String> managedScopes,
            MMap<Key, Boolean> managedOptionals,
            MMap<Key, String> managedLocalPaths,
            MMap<Key, Holder<Collection<Exclusion>>> managedExclusions);

    private boolean containsManagedVersion(Key key) {
        for (AbstractDependencyManager ancestor : path) {
            if (ancestor.managedVersions != null && ancestor.managedVersions.containsKey(key)) {
                return true;
            }
        }
        return managedVersions != null && managedVersions.containsKey(key);
    }

    private String getManagedVersion(Key key) {
        for (AbstractDependencyManager ancestor : path) {
            if (ancestor.managedVersions != null && ancestor.managedVersions.containsKey(key)) {
                return ancestor.managedVersions.get(key);
            }
        }
        if (depth == 1 && managedVersions != null && managedVersions.containsKey(key)) {
            return managedVersions.get(key);
        }
        return null;
    }

    private boolean containsManagedScope(Key key) {
        for (AbstractDependencyManager ancestor : path) {
            if (ancestor.managedScopes != null && ancestor.managedScopes.containsKey(key)) {
                return true;
            }
        }
        return managedScopes != null && managedScopes.containsKey(key);
    }

    private String getManagedScope(Key key) {
        for (AbstractDependencyManager ancestor : path) {
            if (ancestor.managedScopes != null && ancestor.managedScopes.containsKey(key)) {
                return ancestor.managedScopes.get(key);
            }
        }
        if (depth == 1 && managedScopes != null && managedScopes.containsKey(key)) {
            return managedScopes.get(key);
        }
        return null;
    }

    private boolean containsManagedOptional(Key key) {
        for (AbstractDependencyManager ancestor : path) {
            if (ancestor.managedOptionals != null && ancestor.managedOptionals.containsKey(key)) {
                return true;
            }
        }
        return managedOptionals != null && managedOptionals.containsKey(key);
    }

    private Boolean getManagedOptional(Key key) {
        for (AbstractDependencyManager ancestor : path) {
            if (ancestor.managedOptionals != null && ancestor.managedOptionals.containsKey(key)) {
                return ancestor.managedOptionals.get(key);
            }
        }
        if (depth == 1 && managedOptionals != null && managedOptionals.containsKey(key)) {
            return managedOptionals.get(key);
        }
        return null;
    }

    private boolean containsManagedLocalPath(Key key) {
        for (AbstractDependencyManager ancestor : path) {
            if (ancestor.managedLocalPaths != null && ancestor.managedLocalPaths.containsKey(key)) {
                return true;
            }
        }
        return managedLocalPaths != null && managedLocalPaths.containsKey(key);
    }

    private String getManagedLocalPath(Key key) {
        for (AbstractDependencyManager ancestor : path) {
            if (ancestor.managedLocalPaths != null && ancestor.managedLocalPaths.containsKey(key)) {
                return ancestor.managedLocalPaths.get(key);
            }
        }
        if (managedLocalPaths != null && managedLocalPaths.containsKey(key)) {
            return managedLocalPaths.get(key);
        }
        return null;
    }

    /**
     * Merges all way down.
     */
    private Collection<Exclusion> getManagedExclusions(Key key) {
        ArrayList<Exclusion> result = new ArrayList<>();
        for (AbstractDependencyManager ancestor : path) {
            if (ancestor.managedExclusions != null && ancestor.managedExclusions.containsKey(key)) {
                result.addAll(ancestor.managedExclusions.get(key).value);
            }
        }
        if (managedExclusions != null && managedExclusions.containsKey(key)) {
            result.addAll(managedExclusions.get(key).value);
        }
        return result.isEmpty() ? null : result;
    }

    @Override
    public DependencyManager deriveChildManager(DependencyCollectionContext context) {
        requireNonNull(context, "context cannot be null");
        if (!isDerived()) {
            return this;
        }

        MMap<Key, String> managedVersions = null;
        MMap<Key, String> managedScopes = null;
        MMap<Key, Boolean> managedOptionals = null;
        MMap<Key, String> managedLocalPaths = null;
        MMap<Key, Holder<Collection<Exclusion>>> managedExclusions = null;

        for (Dependency managedDependency : context.getManagedDependencies()) {
            Artifact artifact = managedDependency.getArtifact();
            Key key = new Key(artifact);

            String version = artifact.getVersion();
            if (!version.isEmpty() && !containsManagedVersion(key)) {
                if (managedVersions == null) {
                    managedVersions = MMap.emptyNotDone();
                }
                managedVersions.put(key, version);
            }

            if (isInheritedDerived()) {
                String scope = managedDependency.getScope();
                if (!scope.isEmpty() && !containsManagedScope(key)) {
                    if (managedScopes == null) {
                        managedScopes = MMap.emptyNotDone();
                    }
                    managedScopes.put(key, scope);
                }

                Boolean optional = managedDependency.getOptional();
                if (optional != null && !containsManagedOptional(key)) {
                    if (managedOptionals == null) {
                        managedOptionals = MMap.emptyNotDone();
                    }
                    managedOptionals.put(key, optional);
                }
            }

            String localPath = systemDependencyScope == null
                    ? null
                    : systemDependencyScope.getSystemPath(managedDependency.getArtifact());
            if (localPath != null && !containsManagedLocalPath(key)) {
                if (managedLocalPaths == null) {
                    managedLocalPaths = MMap.emptyNotDone();
                }
                managedLocalPaths.put(key, localPath);
            }

            Collection<Exclusion> exclusions = managedDependency.getExclusions();
            if (!exclusions.isEmpty()) {
                if (managedExclusions == null) {
                    managedExclusions = MMap.emptyNotDone();
                }
                Holder<Collection<Exclusion>> managed = managedExclusions.get(key);
                if (managed != null) {
                    ArrayList<Exclusion> ex = new ArrayList<>(managed.getValue());
                    ex.addAll(exclusions);
                    managed = new Holder<>(ex);
                    managedExclusions.put(key, managed);
                } else {
                    managedExclusions.put(key, new Holder<>(exclusions));
                }
            }
        }

        return newInstance(
                managedVersions != null ? managedVersions.done() : null,
                managedScopes != null ? managedScopes.done() : null,
                managedOptionals != null ? managedOptionals.done() : null,
                managedLocalPaths != null ? managedLocalPaths.done() : null,
                managedExclusions != null ? managedExclusions.done() : null);
    }

    @Override
    public DependencyManagement manageDependency(Dependency dependency) {
        requireNonNull(dependency, "dependency cannot be null");
        DependencyManagement management = null;
        Key key = new Key(dependency.getArtifact());

        if (isApplied()) {
            String version = getManagedVersion(key);
            // is managed locally by model builder
            // apply only rules coming from "higher" levels
            if (version != null) {
                management = new DependencyManagement();
                management.setVersion(version);
            }

            String scope = getManagedScope(key);
            // is managed locally by model builder
            // apply only rules coming from "higher" levels
            if (scope != null) {
                if (management == null) {
                    management = new DependencyManagement();
                }
                management.setScope(scope);

                if (systemDependencyScope != null
                        && !systemDependencyScope.is(scope)
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
                    && (scope != null && systemDependencyScope.is(scope)
                            || (scope == null && systemDependencyScope.is(dependency.getScope())))) {
                String localPath = getManagedLocalPath(key);
                if (localPath != null) {
                    if (management == null) {
                        management = new DependencyManagement();
                    }
                    HashMap<String, String> properties =
                            new HashMap<>(dependency.getArtifact().getProperties());
                    systemDependencyScope.setSystemPath(properties, localPath);
                    management.setProperties(properties);
                }
            }

            // optional is not managed by model builder
            // apply only rules coming from "higher" levels
            Boolean optional = getManagedOptional(key);
            if (optional != null) {
                if (management == null) {
                    management = new DependencyManagement();
                }
                management.setOptional(optional);
            }
        }

        // exclusions affect only downstream
        // this will not "exclude" own dependency,
        // is just added as additional information
        // ModelBuilder does not merge exclusions (only applies if dependency does not have exclusion)
        // so we merge it here even from same level
        Collection<Exclusion> exclusions = getManagedExclusions(key);
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
     * Manager manages properties as "version", "scope" (with special care to "system" scope), "optional", "local path"
     * (which are properties applied to node having "system" scope to align all paths to same artifact) and "exclusions".
     * <p>
     * Out of these, "version" is following {@link #isDerived()} and managed with care to not interfere with model
     * builder (management is applied only from higher level). The "local path" is managed only if "scope"
     * was or is managed to "system", while "exclusions" are collected through whole path (from root to current level) and
     * accumulated (additive).
     * <p>
     * The "scope" and "optional" are special: because in dependency graph these two properties are subject to inheritance
     * (which is out of scope for model builder), the "scope" and "optional" is derived only from the root. The actual
     * manager should decide how to handle them.
     * <p>
     * Defaults to {@link #isDerived()}, as this is same how "classic" worked (due {@code deriveUntil=2}).
     * For any kind of transitivity management, it is recommended to override this method, or, make sure that
     * inherited properties that are/were managed are handled in some alternative way on graph transformation.
     */
    protected boolean isInheritedDerived() {
        return isDerived();
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
        // exclude managedLocalPaths
        return Objects.equals(path, that.path)
                && depth == that.depth
                && Objects.equals(managedVersions, that.managedVersions)
                && Objects.equals(managedScopes, that.managedScopes)
                && Objects.equals(managedOptionals, that.managedOptionals)
                && Objects.equals(managedExclusions, that.managedExclusions);
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

    /**
     * Wrapper class for collection to memoize hash code.
     *
     * @param <T> the collection type
     */
    protected static class Holder<T> {
        private final T value;
        private final int hashCode;

        Holder(T value) {
            this.value = requireNonNull(value);
            this.hashCode = Objects.hash(value);
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
            return Objects.equals(value, holder.value);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}
