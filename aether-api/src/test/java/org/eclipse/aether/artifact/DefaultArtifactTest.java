/*******************************************************************************
 * Copyright (c) 2010, 2012 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.artifact;

import static org.junit.Assert.*;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactProperties;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.Test;

/**
 */
public class DefaultArtifactTest
{

    @Test
    public void testDefaultArtifactString()
    {
        Artifact a;

        a = new DefaultArtifact( "gid:aid:ver" );
        assertEquals( "gid", a.getGroupId() );
        assertEquals( "aid", a.getArtifactId() );
        assertEquals( "ver", a.getVersion() );
        assertEquals( "jar", a.getExtension() );
        assertEquals( "", a.getClassifier() );

        a = new DefaultArtifact( "gid:aid:ext:ver" );
        assertEquals( "gid", a.getGroupId() );
        assertEquals( "aid", a.getArtifactId() );
        assertEquals( "ver", a.getVersion() );
        assertEquals( "ext", a.getExtension() );
        assertEquals( "", a.getClassifier() );

        a = new DefaultArtifact( "org.gid:foo-bar:jar:1.1-20101116.150650-3" );
        assertEquals( "org.gid", a.getGroupId() );
        assertEquals( "foo-bar", a.getArtifactId() );
        assertEquals( "1.1-20101116.150650-3", a.getVersion() );
        assertEquals( "jar", a.getExtension() );
        assertEquals( "", a.getClassifier() );

        a = new DefaultArtifact( "gid:aid:ext:cls:ver" );
        assertEquals( "gid", a.getGroupId() );
        assertEquals( "aid", a.getArtifactId() );
        assertEquals( "ver", a.getVersion() );
        assertEquals( "ext", a.getExtension() );
        assertEquals( "cls", a.getClassifier() );

        a = new DefaultArtifact( "gid:aid::cls:ver" );
        assertEquals( "gid", a.getGroupId() );
        assertEquals( "aid", a.getArtifactId() );
        assertEquals( "ver", a.getVersion() );
        assertEquals( "jar", a.getExtension() );
        assertEquals( "cls", a.getClassifier() );

        a = new DefaultArtifact( new DefaultArtifact( "gid:aid:ext:cls:ver" ).toString() );
        assertEquals( "gid", a.getGroupId() );
        assertEquals( "aid", a.getArtifactId() );
        assertEquals( "ver", a.getVersion() );
        assertEquals( "ext", a.getExtension() );
        assertEquals( "cls", a.getClassifier() );
    }

    @Test( expected = IllegalArgumentException.class )
    public void testDefaultArtifactBadString()
    {
        new DefaultArtifact( "gid:aid" );
    }

    @Test
    public void testImmutability()
    {
        Artifact a = new DefaultArtifact( "gid:aid:ext:cls:ver" );
        assertNotSame( a, a.setFile( new File( "file" ) ) );
        assertNotSame( a, a.setVersion( "otherVersion" ) );
        assertNotSame( a, a.setProperties( Collections.singletonMap( "key", "value" ) ) );
    }

    @Test
    public void testArtifactType()
    {
        DefaultArtifactType type = new DefaultArtifactType( "typeId", "typeExt", "typeCls", "typeLang", true, true );

        Artifact a = new DefaultArtifact( "gid", "aid", null, null, null, null, type );
        assertEquals( "typeExt", a.getExtension() );
        assertEquals( "typeCls", a.getClassifier() );
        assertEquals( "typeLang", a.getProperties().get( ArtifactProperties.LANGUAGE ) );
        assertEquals( "typeId", a.getProperties().get( ArtifactProperties.TYPE ) );
        assertEquals( "true", a.getProperties().get( ArtifactProperties.INCLUDES_DEPENDENCIES ) );
        assertEquals( "true", a.getProperties().get( ArtifactProperties.CONSTITUTES_BUILD_PATH ) );

        a = new DefaultArtifact( "gid", "aid", "cls", "ext", "ver", null, type );
        assertEquals( "ext", a.getExtension() );
        assertEquals( "cls", a.getClassifier() );
        assertEquals( "typeLang", a.getProperties().get( ArtifactProperties.LANGUAGE ) );
        assertEquals( "typeId", a.getProperties().get( ArtifactProperties.TYPE ) );
        assertEquals( "true", a.getProperties().get( ArtifactProperties.INCLUDES_DEPENDENCIES ) );
        assertEquals( "true", a.getProperties().get( ArtifactProperties.CONSTITUTES_BUILD_PATH ) );

        Map<String, String> props = new HashMap<String, String>();
        props.put( "someNonStandardProperty", "someNonStandardProperty" );
        a = new DefaultArtifact( "gid", "aid", "cls", "ext", "ver", props, type );
        assertEquals( "ext", a.getExtension() );
        assertEquals( "cls", a.getClassifier() );
        assertEquals( "typeLang", a.getProperties().get( ArtifactProperties.LANGUAGE ) );
        assertEquals( "typeId", a.getProperties().get( ArtifactProperties.TYPE ) );
        assertEquals( "true", a.getProperties().get( ArtifactProperties.INCLUDES_DEPENDENCIES ) );
        assertEquals( "true", a.getProperties().get( ArtifactProperties.CONSTITUTES_BUILD_PATH ) );
        assertEquals( "someNonStandardProperty", a.getProperties().get( "someNonStandardProperty" ) );

        props = new HashMap<String, String>();
        props.put( "someNonStandardProperty", "someNonStandardProperty" );
        props.put( ArtifactProperties.CONSTITUTES_BUILD_PATH, "rubbish" );
        props.put( ArtifactProperties.INCLUDES_DEPENDENCIES, "rubbish" );
        a = new DefaultArtifact( "gid", "aid", "cls", "ext", "ver", props, type );
        assertEquals( "ext", a.getExtension() );
        assertEquals( "cls", a.getClassifier() );
        assertEquals( "typeLang", a.getProperties().get( ArtifactProperties.LANGUAGE ) );
        assertEquals( "typeId", a.getProperties().get( ArtifactProperties.TYPE ) );
        assertEquals( "rubbish", a.getProperties().get( ArtifactProperties.INCLUDES_DEPENDENCIES ) );
        assertEquals( "rubbish", a.getProperties().get( ArtifactProperties.CONSTITUTES_BUILD_PATH ) );
        assertEquals( "someNonStandardProperty", a.getProperties().get( "someNonStandardProperty" ) );
    }

    @Test
    public void testPropertiesCopied()
    {
        Map<String, String> props = new HashMap<String, String>();
        props.put( "key", "value1" );

        Artifact a = new DefaultArtifact( "gid:aid:1", props );
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
