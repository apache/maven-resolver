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

import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.collection.DependencyGraphTransformationContext;
import org.eclipse.aether.collection.DependencyGraphTransformer;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.util.artifact.JavaScopes;

import static java.util.Objects.requireNonNull;

/**
 * A dependency graph transformer that refines the request context for nodes that belong to the "project" context by
 * appending the classpath type to which the node belongs. For instance, a compile-time project dependency will be
 * assigned the request context "project/compile".
 *
 * @see DependencyNode#getRequestContext()
 */
public final class JavaDependencyContextRefiner implements DependencyGraphTransformer {

    public DependencyNode transformGraph(DependencyNode node, DependencyGraphTransformationContext context)
            throws RepositoryException {
        requireNonNull(node, "node cannot be null");
        requireNonNull(context, "context cannot be null");
        String ctx = node.getRequestContext();

        if ("project".equals(ctx)) {
            String scope = getClasspathScope(node);
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

    private String getClasspathScope(DependencyNode node) {
        Dependency dependency = node.getDependency();
        if (dependency == null) {
            return null;
        }

        String scope = dependency.getScope();

        if (JavaScopes.COMPILE.equals(scope) || JavaScopes.SYSTEM.equals(scope) || JavaScopes.PROVIDED.equals(scope)) {
            return JavaScopes.COMPILE;
        } else if (JavaScopes.RUNTIME.equals(scope)) {
            return JavaScopes.RUNTIME;
        } else if (JavaScopes.TEST.equals(scope)) {
            return JavaScopes.TEST;
        }

        return null;
    }
}
