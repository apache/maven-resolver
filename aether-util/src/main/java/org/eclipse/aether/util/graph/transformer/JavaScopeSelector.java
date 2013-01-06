/*******************************************************************************
 * Copyright (c) 2012, 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.util.graph.transformer;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.graph.transformer.ConflictResolver.ConflictContext;
import org.eclipse.aether.util.graph.transformer.ConflictResolver.ConflictItem;
import org.eclipse.aether.util.graph.transformer.ConflictResolver.ScopeSelector;

/**
 * A scope selector for use with {@link ConflictResolver} that supports the scopes from {@link JavaScopes}.
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
        String scope = context.getWinnerScope();
        if ( !JavaScopes.SYSTEM.equals( scope ) )
        {
            scope = chooseEffectiveScope( context.getItems() );
        }
        context.setScope( scope );
    }

    private String chooseEffectiveScope( Collection<ConflictItem> items )
    {
        Set<String> scopes = new HashSet<String>();
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
