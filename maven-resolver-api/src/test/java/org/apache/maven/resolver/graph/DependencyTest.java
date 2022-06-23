package org.apache.maven.resolver.graph;

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

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;

import org.apache.maven.resolver.artifact.DefaultArtifact;
import org.junit.Test;

/**
 */
public class DependencyTest
{

    @Test
    public void testSetScope()
    {
        Dependency d1 = new Dependency( new DefaultArtifact( "gid:aid:ver" ), "compile" );

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
            new Dependency( new DefaultArtifact( "gid:aid:ver" ), "compile", false,
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
