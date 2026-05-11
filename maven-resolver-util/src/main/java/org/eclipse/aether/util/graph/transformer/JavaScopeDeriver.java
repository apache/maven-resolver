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
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.graph.transformer.ConflictResolver.ScopeContext;
import org.eclipse.aether.util.graph.transformer.ConflictResolver.ScopeDeriver;

/**
 * A scope deriver for use with {@link ConflictResolver} that supports the scopes from {@link JavaScopes}.
 */
public final class JavaScopeDeriver extends ScopeDeriver {

    /**
     * Creates a new instance of this scope deriver.
     */
    public JavaScopeDeriver() {}

    @Override
    public void deriveScope(ScopeContext context) throws RepositoryException {
        context.setDerivedScope(getDerivedScope(context.getParentScope(), context.getChildScope()));
    }

    private String getDerivedScope(String parentScope, String childScope) {
        String derivedScope;

        if (JavaScopes.SYSTEM.equals(childScope) || JavaScopes.TEST.equals(childScope)) {
            derivedScope = childScope;
        } else if (parentScope == null || parentScope.isEmpty() || JavaScopes.COMPILE.equals(parentScope)) {
            derivedScope = childScope;
        } else if (JavaScopes.TEST.equals(parentScope) || JavaScopes.RUNTIME.equals(parentScope)) {
            derivedScope = parentScope;
        } else if (JavaScopes.SYSTEM.equals(parentScope) || JavaScopes.PROVIDED.equals(parentScope)) {
            derivedScope = JavaScopes.PROVIDED;
        } else {
            derivedScope = JavaScopes.RUNTIME;
        }

        return derivedScope;
    }
}
