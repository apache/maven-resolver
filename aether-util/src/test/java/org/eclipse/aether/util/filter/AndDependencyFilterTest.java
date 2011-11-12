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
import org.junit.Test;

public class AndDependencyFilterTest
    extends AbstractDependencyFilterTest
{
    @Test
    public void acceptTest()
    {
        NodeBuilder builder = new NodeBuilder();
        builder.artifactId( "test" );
        List<DependencyNode> parents = new LinkedList<DependencyNode>();

        // Empty AND
        assertTrue( new AndDependencyFilter().accept( builder.build(), parents ) );

        // Basic Boolean Input
        assertTrue( new AndDependencyFilter( getAcceptFilter() ).accept( builder.build(), parents ) );
        assertFalse( new AndDependencyFilter( getDenyFilter() ).accept( builder.build(), parents ) );

        assertFalse( new AndDependencyFilter( getDenyFilter(), getDenyFilter() ).accept( builder.build(), parents ) );
        assertFalse( new AndDependencyFilter( getDenyFilter(), getAcceptFilter() ).accept( builder.build(), parents ) );
        assertFalse( new AndDependencyFilter( getAcceptFilter(), getDenyFilter() ).accept( builder.build(), parents ) );
        assertTrue( new AndDependencyFilter( getAcceptFilter(), getAcceptFilter() ).accept( builder.build(), parents ) );

        assertFalse( new AndDependencyFilter( getDenyFilter(), getDenyFilter(), getDenyFilter() ).accept( builder.build(),
                                                                                                          parents ) );
        assertFalse( new AndDependencyFilter( getAcceptFilter(), getDenyFilter(), getDenyFilter() ).accept( builder.build(),
                                                                                                            parents ) );
        assertFalse( new AndDependencyFilter( getAcceptFilter(), getAcceptFilter(), getDenyFilter() ).accept( builder.build(),
                                                                                                              parents ) );
        assertTrue( new AndDependencyFilter( getAcceptFilter(), getAcceptFilter(), getAcceptFilter() ).accept( builder.build(),
                                                                                                               parents ) );

        // User another constructor
        Collection<DependencyFilter> filters = new LinkedList<DependencyFilter>();
        filters.add( getDenyFilter() );
        filters.add( getAcceptFilter() );
        assertFalse( new AndDependencyFilter( filters ).accept( builder.build(), parents ) );

        filters = new LinkedList<DependencyFilter>();
        filters.add( getDenyFilter() );
        filters.add( getDenyFilter() );
        assertFalse( new AndDependencyFilter( filters ).accept( builder.build(), parents ) );

        filters = new LinkedList<DependencyFilter>();
        filters.add( getAcceptFilter() );
        filters.add( getAcceptFilter() );
        assertTrue( new AndDependencyFilter( filters ).accept( builder.build(), parents ) );

        // newInstance
        assertTrue( AndDependencyFilter.newInstance( getAcceptFilter(), getAcceptFilter() ).accept( builder.build(),
                                                                                                    parents ) );
        assertFalse( AndDependencyFilter.newInstance( getAcceptFilter(), getDenyFilter() ).accept( builder.build(),
                                                                                                   parents ) );

        assertFalse( AndDependencyFilter.newInstance( getDenyFilter(), null ).accept( builder.build(), parents ) );
        assertTrue( AndDependencyFilter.newInstance( getAcceptFilter(), null ).accept( builder.build(), parents ) );
        assertNull( AndDependencyFilter.newInstance( null, null ) );
    }

}
