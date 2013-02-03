/*******************************************************************************
 * Copyright (c) 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.internal.test.util;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

public class NodeDefinitionTest
{

    private void assertMatch( String text, String regex, String... groups )
    {
        Pattern pattern = Pattern.compile( regex );
        Matcher matcher = pattern.matcher( text );
        assertEquals( true, matcher.matches() );
        assertTrue( groups.length + " vs " + matcher.groupCount(), groups.length <= matcher.groupCount() );
        for ( int i = 1; i <= groups.length; i++ )
        {
            assertEquals( "Mismatch for group " + i, groups[i - 1], matcher.group( i ) );
        }
    }

    private void assertNoMatch( String text, String regex )
    {
        Pattern pattern = Pattern.compile( regex );
        Matcher matcher = pattern.matcher( text );
        assertEquals( false, matcher.matches() );
    }

    @Test
    public void testPatterns()
    {
        assertMatch( "(Example-ID_0123456789)", NodeDefinition.ID, "Example-ID_0123456789" );
        assertMatch( "^Example-ID_0123456789", NodeDefinition.IDREF, "Example-ID_0123456789" );

        assertMatch( "gid:aid:1", NodeDefinition.COORDS, "gid", "aid", null, null, "1" );
        assertMatch( "gid:aid:jar:1", NodeDefinition.COORDS, "gid", "aid", "jar", null, "1" );
        assertMatch( "gid:aid:jar:cls:1", NodeDefinition.COORDS, "gid", "aid", "jar", "cls", "1" );

        assertMatch( "[1]", NodeDefinition.RANGE, "[1]" );
        assertMatch( "[1,)", NodeDefinition.RANGE, "[1,)" );
        assertMatch( "(1,2)", NodeDefinition.RANGE, "(1,2)" );

        assertMatch( "scope  =  compile", NodeDefinition.SCOPE, "compile", null );
        assertMatch( "scope=compile<runtime", NodeDefinition.SCOPE, "compile", "runtime" );
        assertMatch( "compile<runtime", NodeDefinition.SCOPE, "compile", "runtime" );
        assertNoMatch( "optional", NodeDefinition.SCOPE );
        assertNoMatch( "!optional", NodeDefinition.SCOPE );

        assertMatch( "optional", NodeDefinition.OPTIONAL, "optional" );
        assertMatch( "!optional", NodeDefinition.OPTIONAL, "!optional" );

        assertMatch( "relocations  =  g:a:1", NodeDefinition.RELOCATIONS, "g:a:1" );
        assertMatch( "relocations=g:a:1 , g:a:2", NodeDefinition.RELOCATIONS, "g:a:1 , g:a:2" );

        assertMatch( "props  =  Key:Value", NodeDefinition.PROPS, "Key:Value" );
        assertMatch( "props=k:1 , k_2:v_2", NodeDefinition.PROPS, "k:1 , k_2:v_2" );

        assertMatch( "gid:aid:1", NodeDefinition.COORDSX, "gid:aid:1", null, null );
        assertMatch( "gid:aid:1[1,2)", NodeDefinition.COORDSX, "gid:aid:1", "[1,2)", null );
        assertMatch( "gid:aid:1<2", NodeDefinition.COORDSX, "gid:aid:1", null, "2" );
        assertMatch( "gid:aid:1(, 2)<[1, 3]", NodeDefinition.COORDSX, "gid:aid:1", "(, 2)", "[1, 3]" );

        assertMatch( "gid:aid:1(, 2)<[1, 3] props=k:v scope=c<r optional relocations=g:a:v (id)", NodeDefinition.NODE,
                     "gid:aid:1", "(, 2)", "[1, 3]", "k:v", "c", "r", "optional", "g:a:v", "id" );

        assertMatch( "gid:aid:1(, 2)<[1, 3] props=k:v c<r optional relocations=g:a:v (id)", NodeDefinition.LINE, null,
                     "gid:aid:1", "(, 2)", "[1, 3]", "k:v", "c", "r", "optional", "g:a:v", "id" );
        assertMatch( "^id", NodeDefinition.LINE, "id", null, null, null );
    }

    @Test
    public void testParsing_Reference()
    {
        NodeDefinition desc = new NodeDefinition( "^id" );
        assertEquals( "id", desc.reference );
    }

    @Test
    public void testParsing_Node()
    {
        NodeDefinition desc = new NodeDefinition( "g:a:1" );
        assertEquals( null, desc.reference );
        assertEquals( "g:a:1", desc.coords );
        assertEquals( null, desc.range );
        assertEquals( null, desc.premanagedVersion );
        assertEquals( null, desc.scope );
        assertEquals( null, desc.premanagedScope );
        assertEquals( null, desc.optional );
        assertEquals( null, desc.properties );
        assertEquals( null, desc.relocations );
        assertEquals( null, desc.id );

        desc = new NodeDefinition( "gid1:aid1:ext1:ver1 scope1 !optional" );
        assertEquals( null, desc.reference );
        assertEquals( "gid1:aid1:ext1:ver1", desc.coords );
        assertEquals( null, desc.range );
        assertEquals( null, desc.premanagedVersion );
        assertEquals( "scope1", desc.scope );
        assertEquals( null, desc.premanagedScope );
        assertEquals( false, desc.optional );
        assertEquals( null, desc.properties );
        assertEquals( null, desc.relocations );
        assertEquals( null, desc.id );

        desc = new NodeDefinition( "g:a:1 optional" );
        assertEquals( null, desc.reference );
        assertEquals( "g:a:1", desc.coords );
        assertEquals( null, desc.range );
        assertEquals( null, desc.premanagedVersion );
        assertEquals( null, desc.scope );
        assertEquals( null, desc.premanagedScope );
        assertEquals( true, desc.optional );
        assertEquals( null, desc.properties );
        assertEquals( null, desc.relocations );
        assertEquals( null, desc.id );

        desc =
            new NodeDefinition( "gid:aid:1(, 2)<[1, 3]" + " props = k:v" + " scope=c<r" + " optional"
                + " relocations = g:a:v , g:a:1" + " (id)" );
        assertEquals( null, desc.reference );
        assertEquals( "gid:aid:1", desc.coords );
        assertEquals( "(, 2)", desc.range );
        assertEquals( "[1, 3]", desc.premanagedVersion );
        assertEquals( "c", desc.scope );
        assertEquals( "r", desc.premanagedScope );
        assertEquals( true, desc.optional );
        assertEquals( Collections.singletonMap( "k", "v" ), desc.properties );
        assertEquals( Arrays.asList( "g:a:v", "g:a:1" ), desc.relocations );
        assertEquals( "id", desc.id );
    }

}
