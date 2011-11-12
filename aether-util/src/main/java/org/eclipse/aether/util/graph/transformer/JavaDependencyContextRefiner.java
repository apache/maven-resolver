/*******************************************************************************
 * Copyright (c) 2010, 2011 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.util.graph.transformer;

import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.collection.DependencyGraphTransformationContext;
import org.eclipse.aether.collection.DependencyGraphTransformer;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.util.artifact.JavaScopes;

/**
 * A dependency graph transformer that refines the request context for nodes that belong to the "project" context by
 * appending the classpath type to which the node belongs. For instance, a compile-time project dependency will be
 * assigned the request context "project/compile".
 * 
 * @see DependencyNode#getRequestContext()
 */
public final class JavaDependencyContextRefiner
    implements DependencyGraphTransformer
{

    public DependencyNode transformGraph( DependencyNode node, DependencyGraphTransformationContext context )
        throws RepositoryException
    {
        String ctx = node.getRequestContext();

        if ( "project".equals( ctx ) )
        {
            String scope = getClasspathScope( node );
            if ( scope != null )
            {
                ctx += '/' + scope;
                node.setRequestContext( ctx );
            }
        }

        for ( DependencyNode child : node.getChildren() )
        {
            transformGraph( child, context );
        }

        return node;
    }

    private String getClasspathScope( DependencyNode node )
    {
        Dependency dependency = node.getDependency();
        if ( dependency == null )
        {
            return null;
        }

        String scope = dependency.getScope();

        if ( JavaScopes.COMPILE.equals( scope ) || JavaScopes.SYSTEM.equals( scope )
            || JavaScopes.PROVIDED.equals( scope ) )
        {
            return JavaScopes.COMPILE;
        }
        else if ( JavaScopes.RUNTIME.equals( scope ) )
        {
            return JavaScopes.RUNTIME;
        }
        else if ( JavaScopes.TEST.equals( scope ) )
        {
            return JavaScopes.TEST;
        }

        return null;
    }

}
