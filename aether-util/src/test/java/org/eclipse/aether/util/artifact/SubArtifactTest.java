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
package org.eclipse.aether.util.artifact;

import static org.junit.Assert.*;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.util.artifact.DefaultArtifact;
import org.eclipse.aether.util.artifact.SubArtifact;
import org.junit.Test;

/**
 */
public class SubArtifactTest
{

    private Artifact newMainArtifact( String coords )
    {
        return new DefaultArtifact( coords );
    }

    @Test
    public void testMainArtifactFileNotRetained()
    {
        Artifact a = newMainArtifact( "gid:aid:ver" ).setFile( new File( "" ) );
        assertNotNull( a.getFile() );
        a = new SubArtifact( a, "", "pom" );
        assertNull( a.getFile() );
    }

    @Test
    public void testMainArtifactPropertiesNotRetained()
    {
        Artifact a = newMainArtifact( "gid:aid:ver" ).setProperties( Collections.singletonMap( "key", "value" ) );
        assertEquals( 1, a.getProperties().size() );
        a = new SubArtifact( a, "", "pom" );
        assertEquals( 0, a.getProperties().size() );
        assertSame( null, a.getProperty( "key", null ) );
    }

    @Test( expected = IllegalArgumentException.class )
    public void testMainArtifactMissing()
    {
        new SubArtifact( null, "", "pom" );
    }

    @Test
    public void testEmptyClassifier()
    {
        Artifact main = newMainArtifact( "gid:aid:ext:cls:ver" );
        Artifact sub = new SubArtifact( main, "", "pom" );
        assertEquals( "", sub.getClassifier() );
        sub = new SubArtifact( main, null, "pom" );
        assertEquals( "", sub.getClassifier() );
    }

    @Test
    public void testEmptyExtension()
    {
        Artifact main = newMainArtifact( "gid:aid:ext:cls:ver" );
        Artifact sub = new SubArtifact( main, "tests", "" );
        assertEquals( "", sub.getExtension() );
        sub = new SubArtifact( main, "tests", null );
        assertEquals( "", sub.getExtension() );
    }

    @Test
    public void testSameClassifier()
    {
        Artifact main = newMainArtifact( "gid:aid:ext:cls:ver" );
        Artifact sub = new SubArtifact( main, "*", "pom" );
        assertEquals( "cls", sub.getClassifier() );
    }

    @Test
    public void testSameExtension()
    {
        Artifact main = newMainArtifact( "gid:aid:ext:cls:ver" );
        Artifact sub = new SubArtifact( main, "tests", "*" );
        assertEquals( "ext", sub.getExtension() );
    }

    @Test
    public void testDerivedClassifier()
    {
        Artifact main = newMainArtifact( "gid:aid:ext:cls:ver" );
        Artifact sub = new SubArtifact( main, "*-tests", "pom" );
        assertEquals( "cls-tests", sub.getClassifier() );
        sub = new SubArtifact( main, "tests-*", "pom" );
        assertEquals( "tests-cls", sub.getClassifier() );

        main = newMainArtifact( "gid:aid:ext:ver" );
        sub = new SubArtifact( main, "*-tests", "pom" );
        assertEquals( "tests", sub.getClassifier() );
        sub = new SubArtifact( main, "tests-*", "pom" );
        assertEquals( "tests", sub.getClassifier() );
    }

    @Test
    public void testDerivedExtension()
    {
        Artifact main = newMainArtifact( "gid:aid:ext:cls:ver" );
        Artifact sub = new SubArtifact( main, "", "*.asc" );
        assertEquals( "ext.asc", sub.getExtension() );
        sub = new SubArtifact( main, "", "asc.*" );
        assertEquals( "asc.ext", sub.getExtension() );
    }

    @Test
    public void testImmutability()
    {
        Artifact a = new SubArtifact( newMainArtifact( "gid:aid:ver" ), "", "pom" );
        assertNotSame( a, a.setFile( new File( "file" ) ) );
        assertNotSame( a, a.setVersion( "otherVersion" ) );
        assertNotSame( a, a.setProperties( Collections.singletonMap( "key", "value" ) ) );
    }

    @Test
    public void testPropertiesCopied()
    {
        Map<String, String> props = new HashMap<String, String>();
        props.put( "key", "value1" );

        Artifact a = new SubArtifact( newMainArtifact( "gid:aid:ver" ), "", "pom", props, null );
        assertEquals( "value1", a.getProperty( "key", null ) );
        props.clear();
        assertEquals( "value1", a.getProperty( "key", null ) );

        props.put( "key", "value2" );
        a = a.setProperties( props );
        assertEquals( "value2", a.getProperty( "key", null ) );
        props.clear();
        assertEquals( "value2", a.getProperty( "key", null ) );
    }

}
