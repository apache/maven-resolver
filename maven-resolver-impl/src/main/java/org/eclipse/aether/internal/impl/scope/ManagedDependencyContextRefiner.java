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
package org.eclipse.aether.internal.impl.scope;

import org.eclipse.aether.collection.DependencyGraphTransformationContext;
import org.eclipse.aether.collection.DependencyGraphTransformer;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.impl.scope.BuildScope;
import org.eclipse.aether.impl.scope.InternalScopeManager;
import org.eclipse.aether.scope.DependencyScope;

import static java.util.Objects.requireNonNull;

/**
 * A dependency graph transformer that refines the request context for nodes that belong to the "project" context by
 * appending the buildpath type to which the node belongs. For instance, a compile-time project dependency will be
 * assigned the request context "project/compile".
 * <p>
 * This class also "bridges" between {@link DependencyScope} and Resolver that uses plain string labels for scopes.
 *
 * @see DependencyNode#getRequestContext()
 */
public final class ManagedDependencyContextRefiner implements DependencyGraphTransformer {
    private final InternalScopeManager scopeManager;

    public ManagedDependencyContextRefiner(InternalScopeManager scopeManager) {
        this.scopeManager = requireNonNull(scopeManager, "scopeManager");
    }

    @Override
    public DependencyNode transformGraph(DependencyNode node, DependencyGraphTransformationContext context) {
        requireNonNull(node, "node cannot be null");
        requireNonNull(context, "context cannot be null");
        String ctx = node.getRequestContext();

        if ("project".equals(ctx)) {
            String scope = getBuildpathScope(node);
            if (scope != null) {
                ctx += '/' + scope;
                node.setRequestContext(ctx);
            }
        }

        for (DependencyNode child : node.getChildren()) {
            transformGraph(child, context);
        }

        return node;
    }

    private String getBuildpathScope(DependencyNode node) {
        Dependency dependency = node.getDependency();
        if (dependency == null) {
            return null;
        }

        return scopeManager
                .getDependencyScope(dependency.getScope())
                .flatMap(s ->
                        scopeManager.getDependencyScopeMainProjectBuildScope(s).map(BuildScope::getId))
                .orElse(null);
    }
}
