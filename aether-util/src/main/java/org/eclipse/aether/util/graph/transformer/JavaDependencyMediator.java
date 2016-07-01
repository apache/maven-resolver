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

import java.util.Iterator;

import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.collection.DependencyGraphTransformationContext;
import org.eclipse.aether.collection.DependencyGraphTransformer;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.util.artifact.JavaScopes;

/**
 * A {@code DependencyGraphTransformer} applying the Maven dependency mediation mechanism.
 *
 * @author Christian Schulte
 * @since 1.2
 * @see <a href="http://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Dependency_Scope">Introduction to the Dependency Mechanism</a>
 */
public final class JavaDependencyMediator
    implements DependencyGraphTransformer
{

    @Override
    public DependencyNode transformGraph( final DependencyNode node,
                                          final DependencyGraphTransformationContext context )
        throws RepositoryException
    {
        return this.recurse( node );
    }

    private DependencyNode recurse( final DependencyNode parent )
    {
        final String parentScope = parent.getDependency() != null
                                       ? parent.getDependency().getScope() != null
                                             && parent.getDependency().getScope().length() >= 0
                                             ? parent.getDependency().getScope()
                                             : JavaScopes.COMPILE
                                       : null;

        for ( final Iterator<DependencyNode> it = parent.getChildren().iterator(); it.hasNext(); )
        {
            final DependencyNode child = it.next();
            boolean removed = false;

            if ( parentScope != null )
            {
                String childScope = child.getDependency().getScope() != null
                                        && child.getDependency().getScope().length() >= 0
                                        ? child.getDependency().getScope()
                                        : JavaScopes.COMPILE;

                if ( ( child.getManagedBits() & DependencyNode.MANAGED_SCOPE ) == 0 )
                {
                    // Non-managed child scopes are updated according to the table in the "Dependency Scope" section
                    // of the "Introduction to the Dependency Mechanism" document.

                    if ( JavaScopes.PROVIDED.equals( parentScope ) )
                    {
                        // Compile and runtime become provided.
                        if ( JavaScopes.COMPILE.equals( childScope )
                                 || JavaScopes.RUNTIME.equals( childScope ) )
                        {
                            childScope = JavaScopes.PROVIDED;
                            child.getDependency().setScope( childScope );
                        }
                    }
                    else if ( JavaScopes.RUNTIME.equals( parentScope ) )
                    {
                        // Compile becomes runtime.
                        if ( JavaScopes.COMPILE.equals( childScope ) )
                        {
                            childScope = JavaScopes.RUNTIME;
                            child.getDependency().setScope( childScope );
                        }
                    }
                    else if ( JavaScopes.TEST.equals( parentScope ) )
                    {
                        // Compile and runtime become test.
                        if ( JavaScopes.COMPILE.equals( childScope )
                                 || JavaScopes.RUNTIME.equals( childScope ) )
                        {
                            childScope = JavaScopes.TEST;
                            child.getDependency().setScope( childScope );
                        }
                    }
                }

                // Provided and test scopes are non-transitive.
                if ( JavaScopes.PROVIDED.equals( childScope )
                         || JavaScopes.TEST.equals( childScope ) )
                {
                    it.remove();
                    removed = true;
                }
            }

            if ( !removed )
            {
                this.recurse( child );
            }
        }

        return parent;
    }

}
