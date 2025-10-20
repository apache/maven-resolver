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
 * A dependency manager support class for Maven-specific dependency graph management.
 *
 * <h2>Overview</h2>
 * <p>
 * This implementation works in conjunction with Maven ModelBuilder to handle dependency
 * management across the dependency graph. While ModelBuilder manages dependencies within
 * a single POM context (inheritance, imports), this class applies lineage-based modifications
 * based on previously recorded dependency management rules sourced from ancestors while
 * building the dependency graph. Root-sourced management rules are special, in that they are
 * always applied, while rules collected during traversal are carefully applied to proper
 * descendants only, to not override work done by ModelBuilder already.
 * </p>
 *
 * <h2>Managed Properties</h2>
 * <ul>
 * <li><strong>Version &amp; Scope:</strong> Handled by ModelBuilder for own dependency management
 *     (think "effective POM"). This implementation ensures these are not applied to the same
 *     node that provided the rules, to not override ModelBuilder's work.</li>
 * <li><strong>Optional:</strong> Not handled by ModelBuilder; managed here.</li>
 * <li><strong>System Paths:</strong> Aligned across the entire graph, ensuring the same
 *     system path is used by the same dependency.</li>
 * <li><strong>Exclusions:</strong> Always applied as additional information (not effective
 *     or applied in the same POM).</li>
 * </ul>
 *
 * <h2>Depth-Based Rule Application</h2>
 * <p>
 * This implementation achieves proper rule application by tracking "depth" for each collected
 * rule and ignoring rules coming from the same depth as the processed dependency node.
 * </p>
 * <ul>
 * <li><strong>Depth 0:</strong> Factory instance created during session initialization and
 *     parameterized. Collection begins with "derive" operation using root context.</li>
 * <li><strong>Depth 1:</strong> Special case for "version", "scope" and "optional" properties.
 *     At this level, "apply onto itself" ensures root-defined rules are applied to first-level
 *     siblings (which, if managed by ModelBuilder, will be the same, making this a no-op).</li>
 * <li><strong>Depth > 1:</strong> "Apply onto itself" is not in effect; only "apply below" is used.</li>
 * </ul>
 *
 * <h2>Rule Precedence</h2>
 * <p>
 * Rules are keyed by dependency management entry coordinates (GACE: Group, Artifact, Classifier,
 * Extension - see {@link Key}) and are recorded only if a rule for the same key did not exist
 * previously. This implements the "nearer (to root) management wins" rule, while root management
 * overrides all.
 * </p>
 *
 * <h2>Managed Bits and Graph Transformations</h2>
 * <p>
 * When a {@link org.eclipse.aether.graph.DependencyNode} becomes "managed" by any property
 * provided from this manager, {@link org.eclipse.aether.graph.DependencyNode#isManagedSubject(DependencyManagementSubject)}
 * and {@link org.eclipse.aether.graph.DependencyNode#isManagedSubjectEnforced(DependencyManagementSubject)}
 * will carry this information for the given property. Later graph transformations will abstain
 * from modifying these properties of marked enforced nodes (assuming the node already has the property
 * set to what it should have). Sometimes this is unwanted, especially for properties that need
 * to be inherited in the graph (values derived from parent-child context of the actual node,
 * like "scope" or "optional").
 * </p>
 *
 * <h2>Implementation Notes</h2>
 * <ul>
 * <li>This class maintains a "path" (list of parent managers) and "depth".</li>
 * <li>The field {@code managedLocalPaths} is <em>intentionally left out of hash/equals</em>.</li>
 * <li>Each dependency "derives" an instance with its own context to process second-level
 *     dependencies and so on.</li>
 * </ul>
 *
 * @since 2.0.0
 */
public abstract class AbstractDependencyManager implements DependencyManager {
    /** The path of parent managers from root to current level. */
    protected final ArrayList<AbstractDependencyManager> path;

    /** The current depth in the dependency graph (0 = factory, 1 = root, 2+ = descendants). */
    protected final int depth;

    /** Maximum depth for rule derivation (exclusive). */
    protected final int deriveUntil;

    /** Minimum depth for rule application (inclusive). */
    protected final int applyFrom;

    /** Managed version rules keyed by dependency coordinates. */
    protected final MMap<Key, String> managedVersions;

    /** Managed scope rules keyed by dependency coordinates. */
    protected final MMap<Key, String> managedScopes;

    /** Managed optional flags keyed by dependency coordinates. */
    protected final MMap<Key, Boolean> managedOptionals;

    /** Managed local paths for system dependencies (intentionally excluded from equals/hashCode). */
    protected final MMap<Key, String> managedLocalPaths;

    /** Managed exclusions keyed by dependency coordinates. */
    protected final MMap<Key, Holder<Collection<Exclusion>>> managedExclusions;

    /** System dependency scope handler, may be null if no system scope is defined. */
    protected final SystemDependencyScope systemDependencyScope;

    /** Pre-computed hash code (excludes managedLocalPaths). */
    private final int hashCode;

    /**
     * Creates a new dependency manager with the specified derivation and application parameters.
     *
     * @param deriveUntil the maximum depth for rule derivation (exclusive), must be >= 0
     * @param applyFrom the minimum depth for rule application (inclusive), must be >= 0
     * @param scopeManager the scope manager for handling system dependencies, may be null
     * @throws IllegalArgumentException if deriveUntil or applyFrom are negative
     */
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

    private AbstractDependencyManager getManagedVersion(Key key) {
        for (AbstractDependencyManager ancestor : path) {
            if (ancestor.managedVersions != null && ancestor.managedVersions.containsKey(key)) {
                return ancestor;
            }
        }
        if (depth == 1 && managedVersions != null && managedVersions.containsKey(key)) {
            return this;
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

    private AbstractDependencyManager getManagedScope(Key key) {
        for (AbstractDependencyManager ancestor : path) {
            if (ancestor.managedScopes != null && ancestor.managedScopes.containsKey(key)) {
                return ancestor;
            }
        }
        if (depth == 1 && managedScopes != null && managedScopes.containsKey(key)) {
            return this;
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

    private AbstractDependencyManager getManagedOptional(Key key) {
        for (AbstractDependencyManager ancestor : path) {
            if (ancestor.managedOptionals != null && ancestor.managedOptionals.containsKey(key)) {
                return ancestor;
            }
        }
        if (depth == 1 && managedOptionals != null && managedOptionals.containsKey(key)) {
            return this;
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

    /**
     * Gets the managed local path for system dependencies.
     * Note: Local paths don't follow the depth=1 special rule like versions/scopes.
     *
     * @param key the dependency key
     * @return the managed local path, or null if not managed
     */
    private AbstractDependencyManager getManagedLocalPath(Key key) {
        for (AbstractDependencyManager ancestor : path) {
            if (ancestor.managedLocalPaths != null && ancestor.managedLocalPaths.containsKey(key)) {
                return ancestor;
            }
        }
        if (managedLocalPaths != null && managedLocalPaths.containsKey(key)) {
            return this;
        }
        return null;
    }

    /**
     * Merges exclusions from all levels in the dependency path.
     * Unlike other managed properties, exclusions are accumulated additively
     * from root to current level throughout the entire dependency path.
     *
     * @param key the dependency key
     * @return merged collection of exclusions, or null if none exist
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
            AbstractDependencyManager versionOwner = getManagedVersion(key);
            // is managed locally by model builder
            // apply only rules coming from "higher" levels
            if (versionOwner != null) {
                management = new DependencyManagement();
                management.setVersion(versionOwner.managedVersions.get(key), versionOwner.path.isEmpty());
            }

            AbstractDependencyManager scopeOwner = getManagedScope(key);
            // is managed locally by model builder
            // apply only rules coming from "higher" levels
            if (scopeOwner != null) {
                if (management == null) {
                    management = new DependencyManagement();
                }
                String managedScope = scopeOwner.managedScopes.get(key);
                management.setScope(managedScope, scopeOwner.path.isEmpty());

                if (systemDependencyScope != null
                        && !systemDependencyScope.is(managedScope)
                        && systemDependencyScope.getSystemPath(dependency.getArtifact()) != null) {
                    HashMap<String, String> properties =
                            new HashMap<>(dependency.getArtifact().getProperties());
                    systemDependencyScope.setSystemPath(properties, null);
                    management.setProperties(properties, false);
                }
            }

            // system scope paths always applied to have them aligned
            // (same artifact == same path) in whole graph
            if (systemDependencyScope != null
                    && (scopeOwner != null && systemDependencyScope.is(scopeOwner.managedScopes.get(key))
                            || (scopeOwner == null && systemDependencyScope.is(dependency.getScope())))) {
                AbstractDependencyManager localPathOwner = getManagedLocalPath(key);
                if (localPathOwner != null) {
                    if (management == null) {
                        management = new DependencyManagement();
                    }
                    HashMap<String, String> properties =
                            new HashMap<>(dependency.getArtifact().getProperties());
                    systemDependencyScope.setSystemPath(properties, localPathOwner.managedLocalPaths.get(key));
                    management.setProperties(properties, false);
                }
            }

            // optional is not managed by model builder
            // apply only rules coming from "higher" levels
            AbstractDependencyManager optionalOwner = getManagedOptional(key);
            if (optionalOwner != null) {
                if (management == null) {
                    management = new DependencyManagement();
                }
                management.setOptional(optionalOwner.managedOptionals.get(key), optionalOwner.path.isEmpty());
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
            management.setExclusions(result, false);
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

    /**
     * Key class for dependency management rules based on GACE coordinates.
     * GACE = Group, Artifact, Classifier, Extension (excludes version for management purposes).
     */
    protected static class Key {
        private final Artifact artifact;
        private final int hashCode;

        /**
         * Creates a new key from the given artifact's GACE coordinates.
         *
         * @param artifact the artifact to create a key for
         */
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
