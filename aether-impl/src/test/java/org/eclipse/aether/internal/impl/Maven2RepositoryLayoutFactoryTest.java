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
package org.eclipse.aether.internal.impl;

import static org.junit.Assert.*;

import java.net.URI;
import java.util.List;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.layout.NoRepositoryLayoutException;
import org.eclipse.aether.spi.connector.layout.RepositoryLayout;
import org.eclipse.aether.spi.connector.layout.RepositoryLayout.Checksum;
import org.junit.Before;
import org.junit.Test;

public class Maven2RepositoryLayoutFactoryTest
{

    private DefaultRepositorySystemSession session;

    private Maven2RepositoryLayoutFactory factory;

    private RepositoryLayout layout;

    private RemoteRepository newRepo( String type )
    {
        return new RemoteRepository.Builder( "test", type, "classpath:/nil" ).build();
    }

    private void assertChecksum( Checksum actual, String expectedUri, String expectedAlgo )
    {
        assertEquals( expectedUri, actual.getLocation().toString() );
        assertEquals( expectedAlgo, actual.getAlgorithm() );
    }

    @Before
    public void setUp()
        throws Exception
    {
        session = TestUtils.newSession();
        factory = new Maven2RepositoryLayoutFactory();
        layout = factory.newInstance( session, newRepo( "default" ) );
    }

    @Test( expected = NoRepositoryLayoutException.class )
    public void testBadLayout()
        throws Exception
    {
        factory.newInstance( session, newRepo( "DEFAULT" ) );
    }

    @Test
    public void testArtifactLocation_Release()
    {
        URI uri = layout.getLocation( new DefaultArtifact( "g.i.d", "a-i.d", "cls", "ext", "1.0" ) );
        assertEquals( "g/i/d/a-i.d/1.0/a-i.d-1.0-cls.ext", uri.toString() );
    }

    @Test
    public void testArtifactLocation_Snapshot()
    {
        URI uri = layout.getLocation( new DefaultArtifact( "g.i.d", "a-i.d", "cls", "ext", "1.0-20110329.221805-4" ) );
        assertEquals( "g/i/d/a-i.d/1.0-SNAPSHOT/a-i.d-1.0-20110329.221805-4-cls.ext", uri.toString() );
    }

    @Test
    public void testMetadataLocation_RootLevel()
    {
        URI uri =
            layout.getLocation( new DefaultMetadata( "archetype-catalog.xml", Metadata.Nature.RELEASE_OR_SNAPSHOT ) );
        assertEquals( "archetype-catalog.xml", uri.toString() );
    }

    @Test
    public void testMetadataLocation_GroupLevel()
    {
        URI uri =
            layout.getLocation( new DefaultMetadata( "org.apache.maven.plugins", "maven-metadata.xml",
                                                     Metadata.Nature.RELEASE_OR_SNAPSHOT ) );
        assertEquals( "org/apache/maven/plugins/maven-metadata.xml", uri.toString() );
    }

    @Test
    public void testMetadataLocation_ArtifactLevel()
    {
        URI uri =
            layout.getLocation( new DefaultMetadata( "org.apache.maven.plugins", "maven-jar-plugin",
                                                     "maven-metadata.xml", Metadata.Nature.RELEASE_OR_SNAPSHOT ) );
        assertEquals( "org/apache/maven/plugins/maven-jar-plugin/maven-metadata.xml", uri.toString() );
    }

    @Test
    public void testMetadataLocation_VersionLevel()
    {
        URI uri =
            layout.getLocation( new DefaultMetadata( "org.apache.maven.plugins", "maven-jar-plugin", "1.0-SNAPSHOT",
                                                     "maven-metadata.xml", Metadata.Nature.RELEASE_OR_SNAPSHOT ) );
        assertEquals( "org/apache/maven/plugins/maven-jar-plugin/1.0-SNAPSHOT/maven-metadata.xml", uri.toString() );
    }

    @Test
    public void testArtifactChecksums_Download()
    {
        DefaultArtifact artifact = new DefaultArtifact( "g.i.d", "a-i.d", "cls", "ext", "1.0" );
        URI uri = layout.getLocation( artifact );
        List<Checksum> checksums = layout.getChecksums( artifact, uri, false );
        assertEquals( 2, checksums.size() );
        assertChecksum( checksums.get( 0 ), "g/i/d/a-i.d/1.0/a-i.d-1.0-cls.ext.sha1", "SHA-1" );
        assertChecksum( checksums.get( 1 ), "g/i/d/a-i.d/1.0/a-i.d-1.0-cls.ext.md5", "MD5" );
    }

    @Test
    public void testArtifactChecksums_Upload()
    {
        DefaultArtifact artifact = new DefaultArtifact( "g.i.d", "a-i.d", "cls", "ext", "1.0" );
        URI uri = layout.getLocation( artifact );
        List<Checksum> checksums = layout.getChecksums( artifact, uri, true );
        assertEquals( 2, checksums.size() );
        assertChecksum( checksums.get( 0 ), "g/i/d/a-i.d/1.0/a-i.d-1.0-cls.ext.sha1", "SHA-1" );
        assertChecksum( checksums.get( 1 ), "g/i/d/a-i.d/1.0/a-i.d-1.0-cls.ext.md5", "MD5" );
    }

    @Test
    public void testMetadataChecksums_Download()
    {
        DefaultMetadata metadata =
            new DefaultMetadata( "org.apache.maven.plugins", "maven-jar-plugin", "maven-metadata.xml",
                                 Metadata.Nature.RELEASE_OR_SNAPSHOT );
        URI uri = layout.getLocation( metadata );
        List<Checksum> checksums = layout.getChecksums( metadata, uri, false );
        assertEquals( 2, checksums.size() );
        assertChecksum( checksums.get( 0 ), "org/apache/maven/plugins/maven-jar-plugin/maven-metadata.xml.sha1",
                        "SHA-1" );
        assertChecksum( checksums.get( 1 ), "org/apache/maven/plugins/maven-jar-plugin/maven-metadata.xml.md5", "MD5" );
    }

    @Test
    public void testMetadataChecksums_Upload()
    {
        DefaultMetadata metadata =
            new DefaultMetadata( "org.apache.maven.plugins", "maven-jar-plugin", "maven-metadata.xml",
                                 Metadata.Nature.RELEASE_OR_SNAPSHOT );
        URI uri = layout.getLocation( metadata );
        List<Checksum> checksums = layout.getChecksums( metadata, uri, true );
        assertEquals( 2, checksums.size() );
        assertChecksum( checksums.get( 0 ), "org/apache/maven/plugins/maven-jar-plugin/maven-metadata.xml.sha1",
                        "SHA-1" );
        assertChecksum( checksums.get( 1 ), "org/apache/maven/plugins/maven-jar-plugin/maven-metadata.xml.md5", "MD5" );
    }

}
