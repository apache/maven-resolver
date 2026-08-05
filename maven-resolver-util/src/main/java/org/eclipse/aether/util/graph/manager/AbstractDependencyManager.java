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
import java.util.List;
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

// Note on lookup semantics: management rules follow "nearest to root wins" precedence.
// Each instance maintains a cumulative ancestor LayeredMap — a zero-copy cons-list of
// map fragments — providing O(layers) lookups instead of O(depth) chain walks.
// The layered map is built incrementally at construction time: child.ancestors =
// new layer(parent.ancestors, parent.ownData). When the parent has no per-level data,
// the reference is shared. Since containsManagedXxx() during derive blocks duplicate
// entries for most properties, there is at most one value per key across all layers.

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
 * provided from this manager, {@link org.eclipse.aether.graph.DependencyNode#getManagedBits()}
 * will carry this information for the given property. Later graph transformations will abstain
 * from modifying these properties of marked nodes (assuming the node already has the property
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
    /**
     * Parent manager in the dependency graph (forms a linked list from leaf toward root).
     * Replaces the previous {@code ArrayList<AbstractDependencyManager> path} field —
     * siblings share the same parent reference (O(1) derive instead of O(depth) copy).
     */
    protected final AbstractDependencyManager parent;

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

    // ── Cumulative ancestor maps ──────────────────────────────────────────────
    // Zero-copy layered view of ALL management entries from root through parent.
    // Each layer holds a reference to the parent layer (older data) and its own entries.
    // Lookups traverse the chain from newest to oldest — first match wins (O(layers)).
    // Adding a new level is O(1): just link on top. No HashMap copying.
    // When the parent has no per-level data, the child shares the same reference.
    // These are derived data, excluded from equals/hashCode.

    /** Union of all ancestor version entries (root through parent). */
    private final LayeredMap<Key, String> ancestorVersions;

    /** Union of all ancestor scope entries (root through parent). */
    private final LayeredMap<Key, String> ancestorScopes;

    /** Union of all ancestor optional entries (root through parent). */
    private final LayeredMap<Key, Boolean> ancestorOptionals;

    /** Union of all ancestor local-path entries (root through parent). */
    private final LayeredMap<Key, String> ancestorLocalPaths;

    /** Union of all ancestor exclusion entries (root through parent), layered additively. */
    private final LayeredMap<Key, Collection<Exclusion>> ancestorExclusions;

    /**
     * Pre-computed hash code (excludes managedLocalPaths).
     * Cascading: incorporates the parent's hashCode so a single int comparison
     * reflects the entire ancestor chain without walking it.
     */
    private final int hashCode;

    /**
     * Multi-entry memoization cache for {@link #deriveChildManager(DependencyCollectionContext)}:
     * remembers recent managed-dependency lists (by reference identity) and their results.
     * <p>
     * In BFS dependency collection, siblings typically share the same interned managed-dependency
     * list (guaranteed by DataPool's {@code internArtifactDescriptorManagedDependencies}), but
     * a reactor with several distinct BOM patterns may alternate between many lists. A 16-entry
     * ring buffer captures these patterns while keeping constant memory — unlike an unbounded
     * {@code IdentityHashMap} which would retain every derived {@code DependencyManager} and
     * prevent GC of the dependency subtrees they reference.
     * <p>
     * The BFS collector's traversal loop is single-threaded, so no synchronization is needed.
     */
    private static final int MEMO_CACHE_SIZE = 16;

    @SuppressWarnings("unchecked")
    private transient List<Dependency>[] memoKeys = new List[MEMO_CACHE_SIZE];

    private transient DependencyManager[] memoValues = new DependencyManager[MEMO_CACHE_SIZE];
    private transient int memoIndex;

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
                null,
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
            AbstractDependencyManager parent,
            int depth,
            int deriveUntil,
            int applyFrom,
            MMap<Key, String> managedVersions,
            MMap<Key, String> managedScopes,
            MMap<Key, Boolean> managedOptionals,
            MMap<Key, String> managedLocalPaths,
            MMap<Key, Holder<Collection<Exclusion>>> managedExclusions,
            SystemDependencyScope systemDependencyScope) {
        this.parent = parent;
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

        // Build cumulative ancestor maps: parent's ancestors + parent's own per-level data.
        // When parent has no per-level data, the child shares the parent's reference (zero copy).
        if (parent != null) {
            this.ancestorVersions = mergeAncestors(parent.ancestorVersions, parent.managedVersions);
            this.ancestorScopes = mergeAncestors(parent.ancestorScopes, parent.managedScopes);
            this.ancestorOptionals = mergeAncestors(parent.ancestorOptionals, parent.managedOptionals);
            this.ancestorLocalPaths = mergeAncestors(parent.ancestorLocalPaths, parent.managedLocalPaths);
            this.ancestorExclusions = mergeAncestorExclusions(parent.ancestorExclusions, parent.managedExclusions);
        } else {
            this.ancestorVersions = null;
            this.ancestorScopes = null;
            this.ancestorOptionals = null;
            this.ancestorLocalPaths = null;
            this.ancestorExclusions = null;
        }

        // Cascading hash: incorporates the parent's pre-computed hash so a single int
        // comparison reflects the entire ancestor chain. Excludes managedLocalPaths.
        int h = parent != null ? parent.hashCode : 0;
        h = 31 * h + depth;
        h = 31 * h + Objects.hashCode(managedVersions);
        h = 31 * h + Objects.hashCode(managedScopes);
        h = 31 * h + Objects.hashCode(managedOptionals);
        h = 31 * h + Objects.hashCode(managedExclusions);
        this.hashCode = h;
    }

    /**
     * Links the parent's own per-level MMap on top of the parent's cumulative ancestor layers,
     * producing the child's cumulative ancestor map. O(1) — no HashMap copying.
     * When the parent has no per-level data, the parent's layered map is returned as-is.
     */
    private static <V> LayeredMap<Key, V> mergeAncestors(LayeredMap<Key, V> parentAncestors, MMap<Key, V> parentOwn) {
        if (parentAncestors == null && parentOwn == null) {
            return null;
        }
        if (parentOwn == null) {
            return parentAncestors; // share reference — no new data at this level
        }
        return new LayeredMap<>(parentAncestors, parentOwn.delegate);
    }

    /**
     * Links the parent's own per-level exclusions on top of the parent's cumulative ancestor layers.
     * O(1) — no HashMap copying. Unlike other properties, exclusions use additive semantics:
     * {@link #getManagedExclusions(Key)} walks all layers to collect the union.
     */
    private static LayeredMap<Key, Collection<Exclusion>> mergeAncestorExclusions(
            LayeredMap<Key, Collection<Exclusion>> parentAncestors,
            MMap<Key, Holder<Collection<Exclusion>>> parentOwn) {
        if (parentAncestors == null && parentOwn == null) {
            return null;
        }
        if (parentOwn == null) {
            return parentAncestors; // share reference
        }
        // Unwrap Holder values into a plain map for this layer
        HashMap<Key, Collection<Exclusion>> ownEntries = new HashMap<>();
        parentOwn.delegate.forEach((key, holder) -> ownEntries.put(key, holder.getValue()));
        return new LayeredMap<>(parentAncestors, ownEntries);
    }

    protected abstract DependencyManager newInstance(
            MMap<Key, String> managedVersions,
            MMap<Key, String> managedScopes,
            MMap<Key, Boolean> managedOptionals,
            MMap<Key, String> managedLocalPaths,
            MMap<Key, Holder<Collection<Exclusion>>> managedExclusions);

    private boolean containsManagedVersion(Key key, MMap<Key, String> managedVersions) {
        // Check current instance's own managed versions first (restores the pre-d4035d3a
        // check that was accidentally dropped when the parameter was introduced).
        if (this.managedVersions != null && this.managedVersions.containsKey(key)) {
            return true;
        }
        // O(1) lookup in cumulative ancestor map (replaces O(depth) parent chain walk)
        if (ancestorVersions != null && ancestorVersions.containsKey(key)) {
            return true;
        }
        // Check in-progress new map for duplicates within the same derivation step.
        return managedVersions != null && managedVersions.containsKey(key);
    }

    /**
     * O(1) lookup in the cumulative ancestor map for the managed version.
     * At depth 1, also checks own data (root self-application): when DefaultDependencyManager
     * applies management from depth 0, the root-level rules are stored in DM1.managedVersions
     * and must be visible to DM1.manageDependency().
     */
    private String getManagedVersion(Key key) {
        String result = ancestorVersions != null ? ancestorVersions.get(key) : null;
        if (depth == 1 && managedVersions != null && managedVersions.containsKey(key)) {
            result = managedVersions.get(key);
        }
        return result;
    }

    private boolean containsManagedScope(Key key, MMap<Key, String> managedScopes) {
        if (this.managedScopes != null && this.managedScopes.containsKey(key)) {
            return true;
        }
        if (ancestorScopes != null && ancestorScopes.containsKey(key)) {
            return true;
        }
        return managedScopes != null && managedScopes.containsKey(key);
    }

    private String getManagedScope(Key key) {
        String result = ancestorScopes != null ? ancestorScopes.get(key) : null;
        if (depth == 1 && managedScopes != null && managedScopes.containsKey(key)) {
            result = managedScopes.get(key);
        }
        return result;
    }

    private boolean containsManagedOptional(Key key, MMap<Key, Boolean> managedOptionals) {
        if (this.managedOptionals != null && this.managedOptionals.containsKey(key)) {
            return true;
        }
        if (ancestorOptionals != null && ancestorOptionals.containsKey(key)) {
            return true;
        }
        return managedOptionals != null && managedOptionals.containsKey(key);
    }

    private Boolean getManagedOptional(Key key) {
        Boolean result = ancestorOptionals != null ? ancestorOptionals.get(key) : null;
        if (depth == 1 && managedOptionals != null && managedOptionals.containsKey(key)) {
            result = managedOptionals.get(key);
        }
        return result;
    }

    private boolean containsManagedLocalPath(Key key, MMap<Key, String> managedLocalPaths) {
        if (this.managedLocalPaths != null && this.managedLocalPaths.containsKey(key)) {
            return true;
        }
        if (ancestorLocalPaths != null && ancestorLocalPaths.containsKey(key)) {
            return true;
        }
        return managedLocalPaths != null && managedLocalPaths.containsKey(key);
    }

    /**
     * Gets the managed local path for system dependencies.
     * Note: Local paths don't follow the depth=1 special rule like versions/scopes —
     * own data is always checked (system path alignment across the graph).
     */
    private String getManagedLocalPath(Key key) {
        String result = ancestorLocalPaths != null ? ancestorLocalPaths.get(key) : null;
        if (managedLocalPaths != null && managedLocalPaths.containsKey(key)) {
            result = managedLocalPaths.get(key);
        }
        return result;
    }

    /**
     * Returns merged exclusions from all ancestor layers plus own exclusions.
     * Unlike other managed properties, exclusions are accumulated additively
     * from all levels in the dependency path — each layer is walked to collect
     * the full union.
     *
     * @param key the dependency key
     * @return merged collection of exclusions, or null if none exist
     */
    private Collection<Exclusion> getManagedExclusions(Key key) {
        // Collect exclusions from all ancestor layers (parent/older layers first)
        Collection<Exclusion> ancestorExcl = collectExclusionsFromLayers(ancestorExclusions, key);
        Holder<Collection<Exclusion>> ownExcl = managedExclusions != null ? managedExclusions.get(key) : null;

        if (ancestorExcl == null && ownExcl == null) {
            return null;
        }
        if (ancestorExcl != null && ownExcl == null) {
            return ancestorExcl;
        }
        if (ancestorExcl == null) {
            return ownExcl.value;
        }
        // Both present: merge additively
        ArrayList<Exclusion> result = new ArrayList<>(ancestorExcl);
        result.addAll(ownExcl.value);
        return result;
    }

    /**
     * Walks all layers of the layered exclusions map, collecting exclusions for the given key.
     * Parent (older) layers are collected first via recursion to maintain order.
     * Recursion depth is bounded by the number of layers (typically 2–5), not tree depth.
     */
    private static Collection<Exclusion> collectExclusionsFromLayers(
            LayeredMap<Key, Collection<Exclusion>> layers, Key key) {
        if (layers == null) {
            return null;
        }
        // Recurse to parent first (older data)
        Collection<Exclusion> result = collectExclusionsFromLayers(layers.parent, key);
        Collection<Exclusion> layerExcl = layers.ownEntries.get(key);
        if (layerExcl != null) {
            if (result == null) {
                result = new ArrayList<>(layerExcl);
            } else {
                result.addAll(layerExcl);
            }
        }
        return result;
    }

    @Override
    public DependencyManager deriveChildManager(DependencyCollectionContext context) {
        requireNonNull(context, "context cannot be null");
        if (!isDerived()) {
            return this;
        }

        // Memoization: check if we've already derived for this managed dependencies list
        // (same object reference — guaranteed by DataPool's descriptor/list interning).
        // A 4-entry ring buffer captures the common BOM patterns in a large reactor.
        List<Dependency> managedDeps = context.getManagedDependencies();
        for (int i = 0; i < MEMO_CACHE_SIZE; i++) {
            if (managedDeps == memoKeys[i] && memoValues[i] != null) {
                return memoValues[i];
            }
        }

        MMap<Key, String> managedVersions = null;
        MMap<Key, String> managedScopes = null;
        MMap<Key, Boolean> managedOptionals = null;
        MMap<Key, String> managedLocalPaths = null;
        MMap<Key, Holder<Collection<Exclusion>>> managedExclusions = null;

        for (Dependency managedDependency : managedDeps) {
            Artifact artifact = managedDependency.getArtifact();
            Key key = new Key(artifact);

            String version = artifact.getVersion();
            if (!version.isEmpty() && !containsManagedVersion(key, managedVersions)) {
                if (managedVersions == null) {
                    managedVersions = MMap.emptyNotDone();
                }
                managedVersions.put(key, version);
            }

            if (isInheritedDerived()) {
                String scope = managedDependency.getScope();
                if (!scope.isEmpty() && !containsManagedScope(key, managedScopes)) {
                    if (managedScopes == null) {
                        managedScopes = MMap.emptyNotDone();
                    }
                    managedScopes.put(key, scope);
                }

                Boolean optional = managedDependency.getOptional();
                if (optional != null && !containsManagedOptional(key, managedOptionals)) {
                    if (managedOptionals == null) {
                        managedOptionals = MMap.emptyNotDone();
                    }
                    managedOptionals.put(key, optional);
                }
            }

            String localPath = systemDependencyScope == null
                    ? null
                    : systemDependencyScope.getSystemPath(managedDependency.getArtifact());
            if (localPath != null && !containsManagedLocalPath(key, managedLocalPaths)) {
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

        // Optimization: when no new management data was collected at this depth and management
        // is already being applied (depth >= applyFrom), reuse this instance. This avoids creating
        // unnecessarily distinct DependencyManager instances that would defeat the BF collector's
        // pool cache — the pool key includes the manager, so distinct-but-semantically-equal
        // managers cause pool misses, which in turn lets the skipper prune subtrees that should
        // have been served from the cache. This is the common case for transitive dependencies
        // whose POMs do not declare <dependencyManagement>.
        //
        // However, we can only reuse `this` when it carries no management data of its own.
        // If `this` has management data (e.g. managedVersions != null), returning `this` would
        // hide that data from the child: getManagedVersion() only checks the parent chain (not
        // `this.managedVersions`), so a reused instance's own rules become invisible. In that
        // case we must create a new child with null maps, making `this` the parent and putting
        // the management data on the parent chain where getManagedVersion() can find it.
        // See https://github.com/apache/maven-resolver/issues/2013
        DependencyManager result;
        if (managedVersions == null
                && managedScopes == null
                && managedOptionals == null
                && managedLocalPaths == null
                && managedExclusions == null
                && isApplied()
                && this.managedVersions == null
                && this.managedScopes == null
                && this.managedOptionals == null
                && this.managedLocalPaths == null
                && this.managedExclusions == null) {
            result = this;
        } else {
            result = newInstance(
                    managedVersions != null ? managedVersions.done() : null,
                    managedScopes != null ? managedScopes.done() : null,
                    managedOptionals != null ? managedOptionals.done() : null,
                    managedLocalPaths != null ? managedLocalPaths.done() : null,
                    managedExclusions != null ? managedExclusions.done() : null);
        }

        // Cache the result in the ring buffer for future calls with the same managed deps list
        memoKeys[memoIndex] = managedDeps;
        memoValues[memoIndex] = result;
        memoIndex = (memoIndex + 1) % MEMO_CACHE_SIZE;
        return result;
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
     * Manages dependency properties including "version", "scope", "optional", "local path", and "exclusions".
     * <p>
     * Property management behavior:
     * <ul>
     * <li><strong>Version:</strong> Follows {@link #isDerived()} pattern. Management is applied only at higher
     *     levels to avoid interference with the model builder.</li>
     * <li><strong>Scope:</strong> Derived from root only due to inheritance in dependency graphs. Special handling
     *     for "system" scope to align artifact paths.</li>
     * <li><strong>Optional:</strong> Derived from root only due to inheritance in dependency graphs.</li>
     * <li><strong>Local path:</strong> Managed only when scope is or was set to "system" to ensure consistent
     *     artifact path alignment.</li>
     * <li><strong>Exclusions:</strong> Accumulated additively from root to current level throughout the entire
     *     dependency path.</li>
     * </ul>
     * <p>
     * <strong>Inheritance handling:</strong> Since "scope" and "optional" properties inherit through dependency
     * graphs (beyond model builder scope), they are derived only from the root node. The actual manager
     * implementation determines specific handling behavior.
     * <p>
     * <strong>Default behavior:</strong> Defaults to {@link #isDerived()} to maintain compatibility with
     * "classic" behavior (equivalent to {@code deriveUntil=2}). For custom transitivity management, override
     * this method or ensure inherited managed properties are handled during graph transformation.
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
        // Fast rejection: cascading hashCode reflects the entire ancestor chain,
        // so a single int mismatch rejects without walking any parent pointers.
        if (hashCode != that.hashCode) {
            return false;
        }
        // exclude managedLocalPaths
        // Check cheap fields (depth) before expensive ones (maps, parent chain).
        // Parent comparison is recursive but each level is hash-guarded, and
        // shared parents (same identity) short-circuit via the this==obj check.
        return depth == that.depth
                && Objects.equals(managedVersions, that.managedVersions)
                && Objects.equals(managedScopes, that.managedScopes)
                && Objects.equals(managedOptionals, that.managedOptionals)
                && Objects.equals(managedExclusions, that.managedExclusions)
                && Objects.equals(parent, that.parent);
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
        private final String groupId;
        private final String artifactId;
        private final String extension;
        private final String classifier;
        private final int hashCode;

        /**
         * Creates a new key from the given artifact's GACE coordinates.
         * Coordinate strings are cached eagerly to avoid repeated virtual dispatch
         * through delegation wrappers like {@code RelocatedArtifact} during
         * {@link #equals} comparisons in hash maps.
         *
         * @param artifact the artifact to create a key for
         */
        Key(Artifact artifact) {
            this.groupId = artifact.getGroupId();
            this.artifactId = artifact.getArtifactId();
            this.extension = artifact.getExtension();
            this.classifier = artifact.getClassifier();
            int h = artifactId.hashCode();
            h = 31 * h + groupId.hashCode();
            h = 31 * h + extension.hashCode();
            h = 31 * h + classifier.hashCode();
            this.hashCode = h;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            } else if (!(obj instanceof Key)) {
                return false;
            }
            Key that = (Key) obj;
            return artifactId.equals(that.artifactId)
                    && groupId.equals(that.groupId)
                    && extension.equals(that.extension)
                    && classifier.equals(that.classifier);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public String toString() {
            return groupId + ":" + artifactId + ":" + extension + (classifier.isEmpty() ? "" : ":" + classifier);
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
            this.hashCode = value.hashCode();
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

    /**
     * A zero-copy layered map built as a cons-list of map fragments.
     * Each layer holds a reference to its parent (older data) and its own entries.
     * <p>
     * For "first match wins" properties (versions, scopes, optionals, local paths),
     * {@link #get(Object)} returns the first value found traversing from newest to oldest layer.
     * For additive properties (exclusions), callers walk all layers via the {@link #parent}
     * pointer to collect the union.
     * <p>
     * Adding a new level is O(1) — just link on top. Lookups are O(layers) where layers is
     * the number of depths that contributed management data (typically 2–5 in practice).
     *
     * @param <K> key type
     * @param <V> value type
     */
    static class LayeredMap<K, V> {
        final LayeredMap<K, V> parent;
        final Map<K, V> ownEntries;

        LayeredMap(LayeredMap<K, V> parent, Map<K, V> ownEntries) {
            this.parent = parent;
            this.ownEntries = ownEntries;
        }

        /** Lookup: newest layer first, O(layers). */
        V get(K key) {
            V value = ownEntries.get(key);
            if (value != null) {
                return value;
            }
            return parent != null ? parent.get(key) : null;
        }

        /** Contains check: any layer, O(layers). */
        boolean containsKey(K key) {
            return ownEntries.containsKey(key) || (parent != null && parent.containsKey(key));
        }
    }
}
