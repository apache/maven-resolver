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

import java.util.Collection;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.collection.DependencyGraphTransformationContext;
import org.eclipse.aether.collection.DependencyGraphTransformer;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;

import static java.util.Objects.requireNonNull;

/**
 * A dependency graph transformer that resolves version and scope conflicts among dependencies. For a given set of
 * conflicting nodes, one node will be chosen as the winner and the other nodes are removed from the dependency graph.
 * The exact rules by which a winning node and its effective scope are determined are controlled by user-supplied
 * implementations of {@link VersionSelector}, {@link ScopeSelector}, {@link OptionalitySelector} and
 * {@link ScopeDeriver}.
 * <p>
 * By default, this graph transformer will turn the dependency graph into a tree without duplicate artifacts. Using the
 * configuration property {@link #CONFIG_PROP_VERBOSE}, a verbose mode can be enabled where the graph is still turned
 * into a tree but all nodes participating in a conflict are retained. The nodes that were rejected during conflict
 * resolution have no children and link back to the winner node via the {@link #NODE_DATA_WINNER} key in their custom
 * data. Additionally, the keys {@link #NODE_DATA_ORIGINAL_SCOPE} and {@link #NODE_DATA_ORIGINAL_OPTIONALITY} are used
 * to store the original scope and optionality of each node. Obviously, the resulting dependency tree is not suitable
 * for artifact resolution unless a filter is employed to exclude the duplicate dependencies.
 *
 * @see ClassicConflictResolver
 * @see PathConflictResolver
 */
public abstract class ConflictResolver implements DependencyGraphTransformer {

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
     * The enum representing verbosity levels of conflict resolver.
     *
     * @since 1.9.8
     */
    public enum Verbosity {
        /**
         * Verbosity level to be used in all "common" resolving use cases (ie. dependencies to build class path). The
         * {@link ConflictResolver} in this mode will trim down the graph to the barest minimum: will not leave
         * any conflicting node in place, hence no conflicts will be present in transformed graph either.
         */
        NONE,

        /**
         * Verbosity level to be used in "analyze" resolving use cases (ie. dependency convergence calculations). The
         * {@link ConflictResolver} in this mode will remove any redundant collected nodes, in turn it will leave one
         * with recorded conflicting information. This mode corresponds to "classic verbose" mode when
         * {@link #CONFIG_PROP_VERBOSE} was set to {@code true}. Obviously, the resulting dependency tree is not
         * suitable for artifact resolution unless a filter is employed to exclude the duplicate dependencies.
         */
        STANDARD,

        /**
         * Verbosity level to be used in "analyze" resolving use cases (ie. dependency convergence calculations). The
         * {@link ConflictResolver} in this mode will not remove any collected node, in turn it will record on all
         * eliminated nodes the conflicting information. Obviously, the resulting dependency tree is not suitable
         * for artifact resolution unless a filter is employed to exclude the duplicate dependencies.
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
    protected static Verbosity getVerbosity(RepositorySystemSession session) {
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

    protected final VersionSelector versionSelector;

    protected final ScopeSelector scopeSelector;

    protected final ScopeDeriver scopeDeriver;

    protected final OptionalitySelector optionalitySelector;

    /**
     * Creates a new conflict resolver instance with the specified hooks.
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

    /**
     * A context used to hold information that is relevant for deriving the scope of a child dependency.
     *
     * @see ScopeDeriver
     * @noinstantiate This class is not intended to be instantiated by clients in production code, the constructor may
     *                change without notice and only exists to enable unit testing.
     */
    public interface ScopeContext {
        /**
         * Gets the scope of the parent dependency. This is usually the scope that was derived by earlier invocations of
         * the scope deriver.
         *
         * @return The scope of the parent dependency, never {@code null}.
         */
        String getParentScope();

        /**
         * Gets the original scope of the child dependency. This is the scope that was declared in the artifact
         * descriptor of the parent dependency.
         *
         * @return The original scope of the child dependency, never {@code null}.
         */
        String getChildScope();

        /**
         * Gets the derived scope of the child dependency. This is initially equal to {@link #getChildScope()} until the
         * scope deriver makes changes.
         *
         * @return The derived scope of the child dependency, never {@code null}.
         */
        String getDerivedScope();

        /**
         * Sets the derived scope of the child dependency.
         *
         * @param derivedScope The derived scope of the dependency, may be {@code null}.
         */
        void setDerivedScope(String derivedScope);
    }

    /**
     * A conflicting dependency.
     *
     * @noinstantiate This class is not intended to be instantiated by clients in production code, the constructor may
     *                change without notice and only exists to enable unit testing.
     */
    public interface ConflictItem {
        /**
         * Determines whether the specified conflict item is a sibling of this item.
         *
         * @param item The other conflict item, must not be {@code null}.
         * @return {@code true} if the given item has the same parent as this item, {@code false} otherwise.
         */
        boolean isSibling(ConflictItem item);

        /**
         * Gets the dependency node involved in the conflict.
         *
         * @return The involved dependency node, never {@code null}.
         */
        DependencyNode getNode();

        /**
         * Gets the dependency involved in the conflict, short for {@code getNode.getDependency()}.
         *
         * @return The involved dependency, never {@code null}.
         */
        Dependency getDependency();

        /**
         * Gets the zero-based depth at which the conflicting node occurs in the graph. As such, the depth denotes the
         * number of parent nodes. If actually multiple paths lead to the node, the return value denotes the smallest
         * possible depth.
         *
         * @return The zero-based depth of the node in the graph.
         */
        int getDepth();

        /**
         * Gets the derived scopes of the dependency. In general, the same dependency node could be reached via
         * different paths and each path might result in a different derived scope.
         *
         * @see ScopeDeriver
         * @return The (read-only) set of derived scopes of the dependency, never {@code null}.
         */
        Collection<String> getScopes();

        /**
         * Bit flag indicating whether one or more paths consider the dependency non-optional.
         */
        int OPTIONAL_FALSE = 0x01;

        /**
         * Bit flag indicating whether one or more paths consider the dependency optional.
         */
        int OPTIONAL_TRUE = 0x02;

        /**
         * Gets the derived optionalities of the dependency. In general, the same dependency node could be reached via
         * different paths and each path might result in a different derived optionality.
         *
         * @return A bit field consisting of {@link ConflictResolver.ConflictItem#OPTIONAL_FALSE} and/or
         *         {@link ConflictResolver.ConflictItem#OPTIONAL_TRUE} indicating the derived optionalities the
         *         dependency was encountered with.
         */
        int getOptionalities();
    }

    /**
     * A context used to hold information that is relevant for resolving version and scope conflicts.
     *
     * @see VersionSelector
     * @see ScopeSelector
     * @noinstantiate This class is not intended to be instantiated by clients in production code, the constructor may
     *                change without notice and only exists to enable unit testing.
     */
    public interface ConflictContext {
        /**
         * Gets the root node of the dependency graph being transformed.
         *
         * @return The root node of the dependency graph, never {@code null}.
         */
        DependencyNode getRoot();

        /**
         * Determines whether the specified dependency node belongs to this conflict context.
         *
         * @param node The dependency node to check, must not be {@code null}.
         * @return {@code true} if the given node belongs to this conflict context, {@code false} otherwise.
         */
        boolean isIncluded(DependencyNode node);

        /**
         * Gets the collection of conflict items in this context.
         *
         * @return The (read-only) collection of conflict items in this context, never {@code null}.
         */
        Collection<ConflictItem> getItems();

        /**
         * Gets the conflict item which has been selected as the winner among the conflicting dependencies.
         *
         * @return The winning conflict item or {@code null} if not set yet.
         */
        ConflictItem getWinner();

        /**
         * Sets the conflict item which has been selected as the winner among the conflicting dependencies.
         *
         * @param winner The winning conflict item, may be {@code null}.
         */
        void setWinner(ConflictItem winner);

        /**
         * Gets the effective scope of the winning dependency.
         *
         * @return The effective scope of the winning dependency or {@code null} if none.
         */
        String getScope();

        /**
         * Sets the effective scope of the winning dependency.
         *
         * @param scope The effective scope, may be {@code null}.
         */
        void setScope(String scope);

        /**
         * Gets the effective optional flag of the winning dependency.
         *
         * @return The effective optional flag or {@code null} if none.
         */
        Boolean getOptional();

        /**
         * Sets the effective optional flag of the winning dependency.
         *
         * @param optional The effective optional flag, may be {@code null}.
         */
        void setOptional(Boolean optional);
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
