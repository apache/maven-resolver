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
package org.eclipse.aether.util.graph.transformer;

import java.util.Arrays;
import java.util.Collection;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.collection.DependencyGraphTransformationContext;
import org.eclipse.aether.collection.DependencyGraphTransformer;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.util.ConfigUtils;

import static java.util.Objects.requireNonNull;

/**
 * Abstract base class for dependency graph transformers that resolve version and scope conflicts among dependencies.
 * For a given set of conflicting nodes, one node will be chosen as the winner. How losing nodes are handled depends
 * on the configured verbosity level: they may be removed entirely, have their children removed, or be left in place
 * with conflict information. The exact rules by which a winning node and its effective scope are determined are
 * controlled by user-supplied implementations of {@link VersionSelector}, {@link ScopeSelector},
 * {@link OptionalitySelector} and {@link ScopeDeriver}.
 * <p>
 * <strong>Available Implementations:</strong>
 * <ul>
 * <li><strong>{@link PathConflictResolver}</strong> - Recommended high-performance implementation with O(N) complexity</li>
 * <li><strong>{@link ClassicConflictResolver}</strong> - Legacy implementation for backward compatibility (O(N²) worst-case)</li>
 * </ul>
 * <p>
 * <strong>Implementation Selection Guide:</strong>
 * <ul>
 * <li><strong>New Projects:</strong> Use {@link PathConflictResolver} for optimal performance</li>
 * <li><strong>Large Multi-Module Projects:</strong> Use {@link PathConflictResolver} to avoid performance bottlenecks</li>
 * <li><strong>Maven 4+ Environments:</strong> Use {@link PathConflictResolver} for best build performance</li>
 * <li><strong>Legacy Compatibility:</strong> Use {@link ClassicConflictResolver} only when exact Maven 3.x behavior is required</li>
 * </ul>
 * <p>
 * <strong>Usage Example:</strong>
 * <pre>{@code
 * // Recommended: High-performance path-based resolver
 * DependencyGraphTransformer transformer = new ChainedDependencyGraphTransformer(
 *     new PathConflictResolver(
 *         new NearestVersionSelector(),
 *         new JavaScopeSelector(),
 *         new SimpleOptionalitySelector(),
 *         new JavaScopeDeriver()),
 *     // other transformers...
 * );
 *
 * // Legacy: Classic resolver for backward compatibility
 * DependencyGraphTransformer legacyTransformer = new ChainedDependencyGraphTransformer(
 *     new ClassicConflictResolver(
 *         new NearestVersionSelector(),
 *         new JavaScopeSelector(),
 *         new SimpleOptionalitySelector(),
 *         new JavaScopeDeriver()),
 *     // other transformers...
 * );
 * }</pre>
 * <p>
 * <strong>Verbosity Levels and Conflict Handling:</strong>
 * <ul>
 * <li><strong>NONE (default):</strong> Creates a clean dependency tree without duplicate artifacts.
 *     Losing nodes are completely removed from the graph, so are cycles as well.</li>
 * <li><strong>STANDARD:</strong> Retains losing nodes for analysis but removes their children to prevent
 *     duplicate dependencies. Special handling for version ranges: redundant nodes may still be removed
 *     if multiple versions of the same artifact exist. Losing nodes link back to the winner via
 *     {@link #NODE_DATA_WINNER} and preserve original scope/optionality information. This mode removes cycles only,
 *     while conflict nodes/duplicates are left in place. Graphs in this verbosity level cannot be resolved,
 *     their purpose is for analysis only.</li>
 * <li><strong>FULL:</strong> Preserves the complete original graph structure including all conflicts and cycles.
 *     All nodes remain with their children, but conflict information is recorded for analysis.
 *     Graphs in this verbosity level cannot be resolved, their purpose is for analysis only.</li>
 * </ul>
 * The verbosity level is controlled by the {@link #CONFIG_PROP_VERBOSE} configuration property.
 * <p>
 * <strong>Conflict Metadata:</strong> In STANDARD and FULL modes, the keys {@link #NODE_DATA_ORIGINAL_SCOPE}
 * and {@link #NODE_DATA_ORIGINAL_OPTIONALITY} are used to store the original scope and optionality of each node.
 * Obviously, dependency trees with verbosity STANDARD or FULL are not suitable for artifact resolution unless
 * a filter is employed to exclude the duplicate dependencies.
 * <p>
 * <strong>Conflict ID Processing Pipeline:</strong>
 * <ol>
 * <li><strong>{@link ConflictMarker}:</strong> Assigns conflict IDs based on GACE (groupId:artifactId:classifier:extension)
 *     coordinates, grouping artifacts that differ only in version (partitions the graph, assigning same conflict IDs
 *     to nodes belonging to same conflict group).</li>
 * <li><strong>{@link ConflictIdSorter}:</strong> Creates topological ordering of conflict IDs and detects cycles</li>
 * <li><strong>ConflictResolver implementation:</strong> Uses the sorted conflict IDs to resolve conflicts in dependency order</li>
 * </ol>
 * This transformer will query the keys {@link TransformationContextKeys#CONFLICT_IDS},
 * {@link TransformationContextKeys#SORTED_CONFLICT_IDS}, {@link TransformationContextKeys#CYCLIC_CONFLICT_IDS} for
 * existing information about conflict ids. In absence of this information, it will automatically invoke the
 * {@link ConflictIdSorter} to calculate it.
 *
 * @see PathConflictResolver
 * @see ClassicConflictResolver
 */
public class ConflictResolver implements DependencyGraphTransformer {

    /**
     * The key in the repository session's {@link org.eclipse.aether.RepositorySystemSession#getConfigProperties()
     * configuration properties} used to store a {@link Boolean} flag controlling the transformer's verbose mode.
     * Accepted values are Boolean types, String type (where "true" would be interpreted as {@code true})
     * or Verbosity enum instances.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Object}
     * @configurationDefaultValue "NONE"
     */
    public static final String CONFIG_PROP_VERBOSE = ConfigurationProperties.PREFIX_AETHER + "conflictResolver.verbose";

    /**
     * The name of the conflict resolver implementation to use: "path" (default) or "classic" (same as Maven 3).
     *
     * @since 2.0.11
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     * @configurationDefaultValue {@link #DEFAULT_CONFLICT_RESOLVER_IMPL}
     */
    public static final String CONFIG_PROP_CONFLICT_RESOLVER_IMPL =
            ConfigurationProperties.PREFIX_AETHER + "conflictResolver.impl";

    public static final String CLASSIC_CONFLICT_RESOLVER = "classic";
    public static final String PATH_CONFLICT_RESOLVER = "path";

    public static final String DEFAULT_CONFLICT_RESOLVER_IMPL = PATH_CONFLICT_RESOLVER;

    /**
     * The enum representing verbosity levels of conflict resolver.
     *
     * @since 1.9.8
     */
    public enum Verbosity {
        /**
         * Verbosity level to be used in all "common" resolving use cases (ie dependencies to build class path). The
         * {@link ConflictResolver} in this mode will trim down the graph to the barest minimum: will not leave
         * any conflicting node in place, hence no conflicts will be present in transformed graph either.
         */
        NONE,

        /**
         * Verbosity level to be used in "analyze" resolving use cases (ie dependency convergence calculations). The
         * {@link ConflictResolver} in this mode will remove any redundant collected nodes and cycles, in turn it will
         * leave one with recorded conflicting information. This mode corresponds to "classic verbose" mode when
         * {@link #CONFIG_PROP_VERBOSE} was set to {@code true}. Obviously, the resulting dependency tree is not
         * suitable for artifact resolution unless a filter is employed to exclude the duplicate dependencies.
         */
        STANDARD,

        /**
         * Verbosity level to be used in "analyze" resolving use cases (ie dependency convergence calculations). The
         * {@link ConflictResolver} in this mode will not remove any collected node nor cycle, in turn it will record
         * on all eliminated nodes the conflicting information. Obviously, the resulting dependency tree is not suitable
         * for artifact resolution unless a filter is employed to exclude the duplicate dependencies and possible cycles.
         */
        FULL
    }

    /**
     * Helper method that uses {@link RepositorySystemSession} and {@link #CONFIG_PROP_VERBOSE} key to figure out
     * current {@link Verbosity}: if {@link Boolean} or {@code String} found, returns {@link Verbosity#STANDARD}
     * or {@link Verbosity#NONE}, depending on value (string is parsed with {@link Boolean#parseBoolean(String)}
     * for {@code true} or {@code false} correspondingly. This is to retain "existing" behavior, where the config
     * key accepted only these values.
     * Since 1.9.8 release, this key may contain {@link Verbosity} enum instance as well, in which case that instance
     * is returned.
     * This method never returns {@code null}.
     */
    public static Verbosity getVerbosity(RepositorySystemSession session) {
        final Object verbosityValue = session.getConfigProperties().get(CONFIG_PROP_VERBOSE);
        if (verbosityValue instanceof Boolean) {
            return (Boolean) verbosityValue ? Verbosity.STANDARD : Verbosity.NONE;
        } else if (verbosityValue instanceof String) {
            return Boolean.parseBoolean(verbosityValue.toString()) ? Verbosity.STANDARD : Verbosity.NONE;
        } else if (verbosityValue instanceof Verbosity) {
            return (Verbosity) verbosityValue;
        } else if (verbosityValue != null) {
            throw new IllegalArgumentException("Unsupported Verbosity configuration: " + verbosityValue);
        }
        return Verbosity.NONE;
    }

    /**
     * The key in the dependency node's {@link DependencyNode#getData() custom data} under which a reference to the
     * {@link DependencyNode} which has won the conflict is stored.
     */
    public static final String NODE_DATA_WINNER = "conflict.winner";

    /**
     * The key in the dependency node's {@link DependencyNode#getData() custom data} under which the scope of the
     * dependency before scope derivation and conflict resolution is stored.
     */
    public static final String NODE_DATA_ORIGINAL_SCOPE = "conflict.originalScope";

    /**
     * The key in the dependency node's {@link DependencyNode#getData() custom data} under which the optional flag of
     * the dependency before derivation and conflict resolution is stored.
     */
    public static final String NODE_DATA_ORIGINAL_OPTIONALITY = "conflict.originalOptionality";

    private final ConflictResolver.VersionSelector versionSelector;
    private final ConflictResolver.ScopeSelector scopeSelector;
    private final ConflictResolver.ScopeDeriver scopeDeriver;
    private final ConflictResolver.OptionalitySelector optionalitySelector;

    /**
     * No arg ctor for subclasses and default cases.
     */
    protected ConflictResolver() {
        this.versionSelector = null;
        this.scopeSelector = null;
        this.scopeDeriver = null;
        this.optionalitySelector = null;
    }

    /**
     * Creates a new conflict resolver instance with the specified hooks that delegates to configured conflict resolver
     * dynamically.
     *
     * @param versionSelector The version selector to use, must not be {@code null}.
     * @param scopeSelector The scope selector to use, must not be {@code null}.
     * @param optionalitySelector The optionality selector ot use, must not be {@code null}.
     * @param scopeDeriver The scope deriver to use, must not be {@code null}.
     */
    public ConflictResolver(
            VersionSelector versionSelector,
            ScopeSelector scopeSelector,
            OptionalitySelector optionalitySelector,
            ScopeDeriver scopeDeriver) {
        this.versionSelector = requireNonNull(versionSelector, "version selector cannot be null");
        this.scopeSelector = requireNonNull(scopeSelector, "scope selector cannot be null");
        this.optionalitySelector = requireNonNull(optionalitySelector, "optionality selector cannot be null");
        this.scopeDeriver = requireNonNull(scopeDeriver, "scope deriver cannot be null");
    }

    @Override
    public DependencyNode transformGraph(DependencyNode node, DependencyGraphTransformationContext context)
            throws RepositoryException {
        String cf = ConfigUtils.getString(
                context.getSession(), DEFAULT_CONFLICT_RESOLVER_IMPL, CONFIG_PROP_CONFLICT_RESOLVER_IMPL);
        ConflictResolver delegate;
        if (PATH_CONFLICT_RESOLVER.equals(cf)) {
            delegate = new PathConflictResolver(versionSelector, scopeSelector, optionalitySelector, scopeDeriver);
        } else if (CLASSIC_CONFLICT_RESOLVER.equals(cf)) {
            delegate = new ClassicConflictResolver(versionSelector, scopeSelector, optionalitySelector, scopeDeriver);
        } else {
            throw new IllegalArgumentException("Unknown conflict resolver: " + cf + "; known are "
                    + Arrays.asList(PATH_CONFLICT_RESOLVER, CLASSIC_CONFLICT_RESOLVER));
        }
        return delegate.transformGraph(node, context);
    }

    /**
     * A context used to hold information that is relevant for deriving the scope of a child dependency.
     *
     * @see ScopeDeriver
     * @noinstantiate This class is not intended to be instantiated by clients in production code, the constructor may
     *                change without notice and only exists to enable unit testing.
     */
    public abstract static class ScopeContext {
        /**
         * Gets the scope of the parent dependency. This is usually the scope that was derived by earlier invocations of
         * the scope deriver.
         *
         * @return The scope of the parent dependency, never {@code null}.
         */
        public abstract String getParentScope();

        /**
         * Gets the original scope of the child dependency. This is the scope that was declared in the artifact
         * descriptor of the parent dependency.
         *
         * @return The original scope of the child dependency, never {@code null}.
         */
        public abstract String getChildScope();

        /**
         * Gets the derived scope of the child dependency. This is initially equal to {@link #getChildScope()} until the
         * scope deriver makes changes.
         *
         * @return The derived scope of the child dependency, never {@code null}.
         */
        public abstract String getDerivedScope();

        /**
         * Sets the derived scope of the child dependency.
         *
         * @param derivedScope The derived scope of the dependency, may be {@code null}.
         */
        public abstract void setDerivedScope(String derivedScope);
    }

    /**
     * A conflicting dependency.
     *
     * @noinstantiate This class is not intended to be instantiated by clients in production code, the constructor may
     *                change without notice and only exists to enable unit testing.
     */
    public abstract static class ConflictItem {
        /**
         * Determines whether the specified conflict item is a sibling of this item.
         *
         * @param item The other conflict item, must not be {@code null}.
         * @return {@code true} if the given item has the same parent as this item, {@code false} otherwise.
         */
        public abstract boolean isSibling(ConflictItem item);

        /**
         * Gets the dependency node involved in the conflict.
         *
         * @return The involved dependency node, never {@code null}.
         */
        public abstract DependencyNode getNode();

        /**
         * Gets the dependency involved in the conflict, short for {@code getNode.getDependency()}.
         *
         * @return The involved dependency, never {@code null}.
         */
        public abstract Dependency getDependency();

        /**
         * Gets the zero-based depth at which the conflicting node occurs in the graph. As such, the depth denotes the
         * number of parent nodes. If actually multiple paths lead to the node, the return value denotes the smallest
         * possible depth.
         *
         * @return The zero-based depth of the node in the graph.
         */
        public abstract int getDepth();

        /**
         * Gets the derived scopes of the dependency. In general, the same dependency node could be reached via
         * different paths and each path might result in a different derived scope.
         *
         * @see ScopeDeriver
         * @return The (read-only) set of derived scopes of the dependency, never {@code null}.
         */
        public abstract Collection<String> getScopes();

        /**
         * Bit flag indicating whether one or more paths consider the dependency non-optional.
         */
        public static final int OPTIONAL_FALSE = 0x01;

        /**
         * Bit flag indicating whether one or more paths consider the dependency optional.
         */
        public static final int OPTIONAL_TRUE = 0x02;

        /**
         * Gets the derived optionalities of the dependency. In general, the same dependency node could be reached via
         * different paths and each path might result in a different derived optionality.
         *
         * @return A bit field consisting of {@link ConflictResolver.ConflictItem#OPTIONAL_FALSE} and/or
         *         {@link ConflictResolver.ConflictItem#OPTIONAL_TRUE} indicating the derived optionalities the
         *         dependency was encountered with.
         */
        public abstract int getOptionalities();
    }

    /**
     * A context used to hold information that is relevant for resolving version and scope conflicts.
     *
     * @see VersionSelector
     * @see ScopeSelector
     * @noinstantiate This class is not intended to be instantiated by clients in production code, the constructor may
     *                change without notice and only exists to enable unit testing.
     */
    public abstract static class ConflictContext {
        /**
         * Gets the root node of the dependency graph being transformed.
         *
         * @return The root node of the dependency graph, never {@code null}.
         */
        public abstract DependencyNode getRoot();

        /**
         * Determines whether the specified dependency node belongs to this conflict context.
         *
         * @param node The dependency node to check, must not be {@code null}.
         * @return {@code true} if the given node belongs to this conflict context, {@code false} otherwise.
         */
        public abstract boolean isIncluded(DependencyNode node);

        /**
         * Gets the collection of conflict items in this context.
         *
         * @return The (read-only) collection of conflict items in this context, never {@code null}.
         */
        public abstract Collection<ConflictItem> getItems();

        /**
         * Gets the conflict item which has been selected as the winner among the conflicting dependencies.
         *
         * @return The winning conflict item or {@code null} if not set yet.
         */
        public abstract ConflictItem getWinner();

        /**
         * Sets the conflict item which has been selected as the winner among the conflicting dependencies.
         *
         * @param winner The winning conflict item, may be {@code null}.
         */
        public abstract void setWinner(ConflictItem winner);

        /**
         * Gets the effective scope of the winning dependency.
         *
         * @return The effective scope of the winning dependency or {@code null} if none.
         */
        public abstract String getScope();

        /**
         * Sets the effective scope of the winning dependency.
         *
         * @param scope The effective scope, may be {@code null}.
         */
        public abstract void setScope(String scope);

        /**
         * Gets the effective optional flag of the winning dependency.
         *
         * @return The effective optional flag or {@code null} if none.
         */
        public abstract Boolean getOptional();

        /**
         * Sets the effective optional flag of the winning dependency.
         *
         * @param optional The effective optional flag, may be {@code null}.
         */
        public abstract void setOptional(Boolean optional);
    }

    /**
     * An extension point of {@link ConflictResolver} that determines the winner among conflicting dependencies. The
     * winning node (and its children) will be retained in the dependency graph, the other nodes will get removed. The
     * version selector does not need to deal with potential scope conflicts, these will be addressed afterwards by the
     * {@link ScopeSelector}.
     * <p>
     * <strong>Note:</strong> Implementations must be stateless.
     */
    public abstract static class VersionSelector {

        /**
         * Retrieves the version selector for use during the specified graph transformation. The conflict resolver calls
         * this method once per
         * {@link ConflictResolver#transformGraph(DependencyNode, DependencyGraphTransformationContext)} invocation to
         * allow implementations to prepare any auxiliary data that is needed for their operation. Given that
         * implementations must be stateless, a new instance needs to be returned to hold such auxiliary data. The
         * default implementation simply returns the current instance which is appropriate for implementations which do
         * not require auxiliary data.
         *
         * @param root The root node of the (possibly cyclic!) graph to transform, must not be {@code null}.
         * @param context The graph transformation context, must not be {@code null}.
         * @return The scope deriver to use for the given graph transformation, never {@code null}.
         * @throws RepositoryException If the instance could not be retrieved.
         */
        public VersionSelector getInstance(DependencyNode root, DependencyGraphTransformationContext context)
                throws RepositoryException {
            return this;
        }

        /**
         * Determines the winning node among conflicting dependencies. Implementations will usually iterate
         * {@link ConflictContext#getItems()}, inspect {@link ConflictItem#getNode()} and eventually call
         * {@link ConflictContext#setWinner(ConflictResolver.ConflictItem)} to deliver the winner. Failure to select a
         * winner will automatically fail the entire conflict resolution.
         *
         * @param context The conflict context, must not be {@code null}.
         * @throws RepositoryException If the version selection failed.
         */
        public abstract void selectVersion(ConflictContext context) throws RepositoryException;
    }

    /**
     * An extension point of {@link ConflictResolver} that determines the effective scope of a dependency from a
     * potentially conflicting set of {@link ScopeDeriver derived scopes}. The scope selector gets invoked after the
     * {@link VersionSelector} has picked the winning node.
     * <p>
     * <strong>Note:</strong> Implementations must be stateless.
     */
    public abstract static class ScopeSelector {

        /**
         * Retrieves the scope selector for use during the specified graph transformation. The conflict resolver calls
         * this method once per
         * {@link ConflictResolver#transformGraph(DependencyNode, DependencyGraphTransformationContext)} invocation to
         * allow implementations to prepare any auxiliary data that is needed for their operation. Given that
         * implementations must be stateless, a new instance needs to be returned to hold such auxiliary data. The
         * default implementation simply returns the current instance which is appropriate for implementations which do
         * not require auxiliary data.
         *
         * @param root The root node of the (possibly cyclic!) graph to transform, must not be {@code null}.
         * @param context The graph transformation context, must not be {@code null}.
         * @return The scope selector to use for the given graph transformation, never {@code null}.
         * @throws RepositoryException If the instance could not be retrieved.
         */
        public ScopeSelector getInstance(DependencyNode root, DependencyGraphTransformationContext context)
                throws RepositoryException {
            return this;
        }

        /**
         * Determines the effective scope of the dependency given by {@link ConflictContext#getWinner()}.
         * Implementations will usually iterate {@link ConflictContext#getItems()}, inspect
         * {@link ConflictItem#getScopes()} and eventually call {@link ConflictContext#setScope(String)} to deliver the
         * effective scope.
         *
         * @param context The conflict context, must not be {@code null}.
         * @throws RepositoryException If the scope selection failed.
         */
        public abstract void selectScope(ConflictContext context) throws RepositoryException;
    }

    /**
     * An extension point of {@link ConflictResolver} that determines the scope of a dependency in relation to the scope
     * of its parent.
     * <p>
     * <strong>Note:</strong> Implementations must be stateless.
     */
    public abstract static class ScopeDeriver {

        /**
         * Retrieves the scope deriver for use during the specified graph transformation. The conflict resolver calls
         * this method once per
         * {@link ConflictResolver#transformGraph(DependencyNode, DependencyGraphTransformationContext)} invocation to
         * allow implementations to prepare any auxiliary data that is needed for their operation. Given that
         * implementations must be stateless, a new instance needs to be returned to hold such auxiliary data. The
         * default implementation simply returns the current instance which is appropriate for implementations which do
         * not require auxiliary data.
         *
         * @param root The root node of the (possibly cyclic!) graph to transform, must not be {@code null}.
         * @param context The graph transformation context, must not be {@code null}.
         * @return The scope deriver to use for the given graph transformation, never {@code null}.
         * @throws RepositoryException If the instance could not be retrieved.
         */
        public ScopeDeriver getInstance(DependencyNode root, DependencyGraphTransformationContext context)
                throws RepositoryException {
            return this;
        }

        /**
         * Determines the scope of a dependency in relation to the scope of its parent. Implementors need to call
         * {@link ScopeContext#setDerivedScope(String)} to deliver the result of their calculation. If said method is
         * not invoked, the conflict resolver will assume the scope of the child dependency remains unchanged.
         *
         * @param context The scope context, must not be {@code null}.
         * @throws RepositoryException If the scope deriviation failed.
         */
        public abstract void deriveScope(ScopeContext context) throws RepositoryException;
    }

    /**
     * An extension point of {@link ConflictResolver} that determines the effective optional flag of a dependency from a
     * potentially conflicting set of derived optionalities. The optionality selector gets invoked after the
     * {@link VersionSelector} has picked the winning node.
     * <p>
     * <strong>Note:</strong> Implementations must be stateless.
     */
    public abstract static class OptionalitySelector {

        /**
         * Retrieves the optionality selector for use during the specified graph transformation. The conflict resolver
         * calls this method once per
         * {@link ConflictResolver#transformGraph(DependencyNode, DependencyGraphTransformationContext)} invocation to
         * allow implementations to prepare any auxiliary data that is needed for their operation. Given that
         * implementations must be stateless, a new instance needs to be returned to hold such auxiliary data. The
         * default implementation simply returns the current instance which is appropriate for implementations which do
         * not require auxiliary data.
         *
         * @param root The root node of the (possibly cyclic!) graph to transform, must not be {@code null}.
         * @param context The graph transformation context, must not be {@code null}.
         * @return The optionality selector to use for the given graph transformation, never {@code null}.
         * @throws RepositoryException If the instance could not be retrieved.
         */
        public OptionalitySelector getInstance(DependencyNode root, DependencyGraphTransformationContext context)
                throws RepositoryException {
            return this;
        }

        /**
         * Determines the effective optional flag of the dependency given by {@link ConflictContext#getWinner()}.
         * Implementations will usually iterate {@link ConflictContext#getItems()}, inspect
         * {@link ConflictItem#getOptionalities()} and eventually call {@link ConflictContext#setOptional(Boolean)} to
         * deliver the effective optional flag.
         *
         * @param context The conflict context, must not be {@code null}.
         * @throws RepositoryException If the optionality selection failed.
         */
        public abstract void selectOptionality(ConflictContext context) throws RepositoryException;
    }
}
