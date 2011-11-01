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
package org.eclipse.aether.util.filter;

import static org.junit.Assert.*;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.internal.test.util.NodeBuilder;
import org.eclipse.aether.util.filter.AndDependencyFilter;
import org.eclipse.aether.util.filter.OrDependencyFilter;
import org.junit.Test;

public class OrDependencyFilterTest
    extends AbstractDependencyFilterTest
{

    @Test
    public void acceptTest()
    {
        NodeBuilder builder = new NodeBuilder();
        builder.artifactId( "test" );
        List<DependencyNode> parents = new LinkedList<DependencyNode>();
        // Empty OR
        assertFalse( new OrDependencyFilter().accept( builder.build(), parents ) );

        // Basic Boolean Input
        assertTrue( new OrDependencyFilter( getAcceptFilter() ).accept( builder.build(), parents ) );
        assertFalse( new OrDependencyFilter( getDenyFilter() ).accept( builder.build(), parents ) );

        assertFalse( new OrDependencyFilter( getDenyFilter(), getDenyFilter() ).accept( builder.build(), parents ) );
        assertTrue( new OrDependencyFilter( getDenyFilter(), getAcceptFilter() ).accept( builder.build(), parents ) );
        assertTrue( new OrDependencyFilter( getAcceptFilter(), getDenyFilter() ).accept( builder.build(), parents ) );
        assertTrue( new OrDependencyFilter( getAcceptFilter(), getAcceptFilter() ).accept( builder.build(), parents ) );

        assertFalse( new OrDependencyFilter( getDenyFilter(), getDenyFilter(), getDenyFilter() ).accept( builder.build(),
                                                                                                         parents ) );
        assertTrue( new OrDependencyFilter( getAcceptFilter(), getDenyFilter(), getDenyFilter() ).accept( builder.build(),
                                                                                                          parents ) );
        assertTrue( new OrDependencyFilter( getAcceptFilter(), getAcceptFilter(), getDenyFilter() ).accept( builder.build(),
                                                                                                            parents ) );
        assertTrue( new OrDependencyFilter( getAcceptFilter(), getAcceptFilter(), getAcceptFilter() ).accept( builder.build(),
                                                                                                              parents ) );

        // User another constructor
        Collection<DependencyFilter> filters = new LinkedList<DependencyFilter>();
        filters.add( getDenyFilter() );
        filters.add( getAcceptFilter() );
        assertTrue( new OrDependencyFilter( filters ).accept( builder.build(), parents ) );

        filters = new LinkedList<DependencyFilter>();
        filters.add( getDenyFilter() );
        filters.add( getDenyFilter() );
        assertFalse( new OrDependencyFilter( filters ).accept( builder.build(), parents ) );

        // newInstance
        assertTrue( AndDependencyFilter.newInstance( getAcceptFilter(), getAcceptFilter() ).accept( builder.build(),
                                                                                                    parents ) );
        assertFalse( AndDependencyFilter.newInstance( getAcceptFilter(), getDenyFilter() ).accept( builder.build(),
                                                                                                   parents ) );
        assertTrue( AndDependencyFilter.newInstance( getAcceptFilter(), null ).accept( builder.build(), parents ) );
        assertFalse( AndDependencyFilter.newInstance( getDenyFilter(), null ).accept( builder.build(), parents ) );
        assertNull( AndDependencyFilter.newInstance( null, null ) );
    }

}
