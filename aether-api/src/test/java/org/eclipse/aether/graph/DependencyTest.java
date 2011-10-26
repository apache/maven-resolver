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
package org.eclipse.aether.graph;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;

import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;
import org.junit.Test;

/**
 */
public class DependencyTest
{

    @Test
    public void testSetScope()
    {
        Dependency d1 = new Dependency( new StubArtifact( "gid:aid:ver" ), "compile" );

        Dependency d2 = d1.setScope( null );
        assertNotSame( d2, d1 );
        assertEquals( "", d2.getScope() );

        Dependency d3 = d1.setScope( "test" );
        assertNotSame( d3, d1 );
        assertEquals( "test", d3.getScope() );
    }

    @Test
    public void testSetExclusions()
    {
        Dependency d1 =
            new Dependency( new StubArtifact( "gid:aid:ver" ), "compile", false,
                            Collections.singleton( new Exclusion( "g", "a", "c", "e" ) ) );

        Dependency d2 = d1.setExclusions( null );
        assertNotSame( d2, d1 );
        assertEquals( 0, d2.getExclusions().size() );

        assertSame( d2, d2.setExclusions( null ) );
        assertSame( d2, d2.setExclusions( Collections.<Exclusion> emptyList() ) );
        assertSame( d2, d2.setExclusions( Collections.<Exclusion> emptySet() ) );
        assertSame( d1, d1.setExclusions( Arrays.asList( new Exclusion( "g", "a", "c", "e" ) ) ) );

        Dependency d3 =
            d1.setExclusions( Arrays.asList( new Exclusion( "g", "a", "c", "e" ), new Exclusion( "g", "a", "c", "f" ) ) );
        assertNotSame( d3, d1 );
        assertEquals( 2, d3.getExclusions().size() );
    }

}
