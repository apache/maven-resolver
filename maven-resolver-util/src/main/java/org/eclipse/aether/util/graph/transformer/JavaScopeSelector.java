package org.eclipse.aether.util.graph.transformer;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *  http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.graph.transformer.ConflictResolver.ConflictContext;
import org.eclipse.aether.util.graph.transformer.ConflictResolver.ConflictItem;
import org.eclipse.aether.util.graph.transformer.ConflictResolver.ScopeSelector;

/**
 * A scope selector for use with {@link ConflictResolver} that supports the scopes from {@link JavaScopes}. In general,
 * this selector picks the widest scope present among conflicting dependencies where e.g. "compile" is wider than
 * "runtime" which is wider than "test". If however a direct dependency is involved, its scope is selected.
 */
public final class JavaScopeSelector
    extends ScopeSelector
{

    /**
     * Creates a new instance of this scope selector.
     */
    public JavaScopeSelector()
    {
    }

    @Override
    public void selectScope( ConflictContext context )
        throws RepositoryException
    {
        String scope = context.getWinner().getDependency().getScope();
        if ( !JavaScopes.SYSTEM.equals( scope ) )
        {
            scope = chooseEffectiveScope( context.getItems() );
        }
        context.setScope( scope );
    }

    private String chooseEffectiveScope( Collection<ConflictItem> items )
    {
        Set<String> scopes = new HashSet<>();
        for ( ConflictItem item : items )
        {
            if ( item.getDepth() <= 1 )
            {
                return item.getDependency().getScope();
            }
            scopes.addAll( item.getScopes() );
        }
        return chooseEffectiveScope( scopes );
    }

    private String chooseEffectiveScope( Set<String> scopes )
    {
        if ( scopes.size() > 1 )
        {
            scopes.remove( JavaScopes.SYSTEM );
        }

        String effectiveScope = "";

        if ( scopes.size() == 1 )
        {
            effectiveScope = scopes.iterator().next();
        }
        else if ( scopes.contains( JavaScopes.COMPILE ) )
        {
            effectiveScope = JavaScopes.COMPILE;
        }
        else if ( scopes.contains( JavaScopes.RUNTIME ) )
        {
            effectiveScope = JavaScopes.RUNTIME;
        }
        else if ( scopes.contains( JavaScopes.PROVIDED ) )
        {
            effectiveScope = JavaScopes.PROVIDED;
        }
        else if ( scopes.contains( JavaScopes.TEST ) )
        {
            effectiveScope = JavaScopes.TEST;
        }

        return effectiveScope;
    }

}
