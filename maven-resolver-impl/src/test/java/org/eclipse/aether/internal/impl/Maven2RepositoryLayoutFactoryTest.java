package org.eclipse.aether.internal.impl;

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

import java.net.URI;
import java.util.List;
import java.util.Locale;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.layout.RepositoryLayout;
import org.eclipse.aether.spi.connector.layout.RepositoryLayout.Checksum;
import org.eclipse.aether.transfer.NoRepositoryLayoutException;
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

    private void assertChecksums( List<Checksum> actual, String baseUri, String... algos )
    {
        assertEquals( algos.length, actual.size() );
        for ( int i = 0; i < algos.length; i++ )
        {
            String uri = baseUri + '.' + algos[i].replace( "-", "" ).toLowerCase( Locale.ENGLISH );
            assertChecksum( actual.get( i ), uri, algos[i] );
        }
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
        DefaultArtifact artifact = new DefaultArtifact( "g.i.d", "a-i.d", "cls", "ext", "1.0" );
        URI uri = layout.getLocation( artifact, false );
        assertEquals( "g/i/d/a-i.d/1.0/a-i.d-1.0-cls.ext", uri.toString() );
        uri = layout.getLocation( artifact, true );
        assertEquals( "g/i/d/a-i.d/1.0/a-i.d-1.0-cls.ext", uri.toString() );
    }

    @Test
    public void testArtifactLocation_Snapshot()
    {
        DefaultArtifact artifact = new DefaultArtifact( "g.i.d", "a-i.d", "cls", "ext", "1.0-20110329.221805-4" );
        URI uri = layout.getLocation( artifact, false );
        assertEquals( "g/i/d/a-i.d/1.0-SNAPSHOT/a-i.d-1.0-20110329.221805-4-cls.ext", uri.toString() );
        uri = layout.getLocation( artifact, true );
        assertEquals( "g/i/d/a-i.d/1.0-SNAPSHOT/a-i.d-1.0-20110329.221805-4-cls.ext", uri.toString() );
    }

    @Test
    public void testMetadataLocation_RootLevel()
    {
        DefaultMetadata metadata = new DefaultMetadata( "archetype-catalog.xml", Metadata.Nature.RELEASE_OR_SNAPSHOT );
        URI uri = layout.getLocation( metadata, false );
        assertEquals( "archetype-catalog.xml", uri.toString() );
        uri = layout.getLocation( metadata, true );
        assertEquals( "archetype-catalog.xml", uri.toString() );
    }

    @Test
    public void testMetadataLocation_GroupLevel()
    {
        DefaultMetadata metadata =
            new DefaultMetadata( "org.apache.maven.plugins", "maven-metadata.xml", Metadata.Nature.RELEASE_OR_SNAPSHOT );
        URI uri = layout.getLocation( metadata, false );
        assertEquals( "org/apache/maven/plugins/maven-metadata.xml", uri.toString() );
        uri = layout.getLocation( metadata, true );
        assertEquals( "org/apache/maven/plugins/maven-metadata.xml", uri.toString() );
    }

    @Test
    public void testMetadataLocation_ArtifactLevel()
    {
        DefaultMetadata metadata =
            new DefaultMetadata( "org.apache.maven.plugins", "maven-jar-plugin", "maven-metadata.xml",
                                 Metadata.Nature.RELEASE_OR_SNAPSHOT );
        URI uri = layout.getLocation( metadata, false );
        assertEquals( "org/apache/maven/plugins/maven-jar-plugin/maven-metadata.xml", uri.toString() );
        uri = layout.getLocation( metadata, true );
        assertEquals( "org/apache/maven/plugins/maven-jar-plugin/maven-metadata.xml", uri.toString() );
    }

    @Test
    public void testMetadataLocation_VersionLevel()
    {
        DefaultMetadata metadata =
            new DefaultMetadata( "org.apache.maven.plugins", "maven-jar-plugin", "1.0-SNAPSHOT", "maven-metadata.xml",
                                 Metadata.Nature.RELEASE_OR_SNAPSHOT );
        URI uri = layout.getLocation( metadata, false );
        assertEquals( "org/apache/maven/plugins/maven-jar-plugin/1.0-SNAPSHOT/maven-metadata.xml", uri.toString() );
        uri = layout.getLocation( metadata, true );
        assertEquals( "org/apache/maven/plugins/maven-jar-plugin/1.0-SNAPSHOT/maven-metadata.xml", uri.toString() );
    }

    @Test
    public void testArtifactChecksums_Download()
    {
        DefaultArtifact artifact = new DefaultArtifact( "g.i.d", "a-i.d", "cls", "ext", "1.0" );
        URI uri = layout.getLocation( artifact, false );
        List<Checksum> checksums = layout.getChecksums( artifact, false, uri );
        assertEquals( 4, checksums.size() );
        assertChecksum( checksums.get( 0 ), "g/i/d/a-i.d/1.0/a-i.d-1.0-cls.ext.sha512", "SHA-512" );
        assertChecksum( checksums.get( 1 ), "g/i/d/a-i.d/1.0/a-i.d-1.0-cls.ext.sha256", "SHA-256" );
        assertChecksum( checksums.get( 2 ), "g/i/d/a-i.d/1.0/a-i.d-1.0-cls.ext.sha1", "SHA-1" );
        assertChecksum( checksums.get( 3 ), "g/i/d/a-i.d/1.0/a-i.d-1.0-cls.ext.md5", "MD5" );
    }

    @Test
    public void testArtifactChecksums_Upload()
    {
        DefaultArtifact artifact = new DefaultArtifact( "g.i.d", "a-i.d", "cls", "ext", "1.0" );
        URI uri = layout.getLocation( artifact, true );
        List<Checksum> checksums = layout.getChecksums( artifact, true, uri );
        assertEquals( 4, checksums.size() );
        assertChecksum( checksums.get( 0 ), "g/i/d/a-i.d/1.0/a-i.d-1.0-cls.ext.sha512", "SHA-512" );
        assertChecksum( checksums.get( 1 ), "g/i/d/a-i.d/1.0/a-i.d-1.0-cls.ext.sha256", "SHA-256" );
        assertChecksum( checksums.get( 2 ), "g/i/d/a-i.d/1.0/a-i.d-1.0-cls.ext.sha1", "SHA-1" );
        assertChecksum( checksums.get( 3 ), "g/i/d/a-i.d/1.0/a-i.d-1.0-cls.ext.md5", "MD5" );
    }

    @Test
    public void testMetadataChecksums_Download()
    {
        DefaultMetadata metadata =
            new DefaultMetadata( "org.apache.maven.plugins", "maven-jar-plugin", "maven-metadata.xml",
                                 Metadata.Nature.RELEASE_OR_SNAPSHOT );
        URI uri = layout.getLocation( metadata, false );
        List<Checksum> checksums = layout.getChecksums( metadata, false, uri );
        assertEquals( 4, checksums.size() );
        assertChecksum( checksums.get( 0 ), "org/apache/maven/plugins/maven-jar-plugin/maven-metadata.xml.sha512",
                        "SHA-512" );
        assertChecksum( checksums.get( 1 ), "org/apache/maven/plugins/maven-jar-plugin/maven-metadata.xml.sha256",
                        "SHA-256" );
        assertChecksum( checksums.get( 2 ), "org/apache/maven/plugins/maven-jar-plugin/maven-metadata.xml.sha1",
                        "SHA-1" );
        assertChecksum( checksums.get( 3 ), "org/apache/maven/plugins/maven-jar-plugin/maven-metadata.xml.md5", "MD5" );
    }

    @Test
    public void testMetadataChecksums_Upload()
    {
        DefaultMetadata metadata =
            new DefaultMetadata( "org.apache.maven.plugins", "maven-jar-plugin", "maven-metadata.xml",
                                 Metadata.Nature.RELEASE_OR_SNAPSHOT );
        URI uri = layout.getLocation( metadata, true );
        List<Checksum> checksums = layout.getChecksums( metadata, true, uri );
        assertEquals( 4, checksums.size() );
        assertChecksum( checksums.get( 0 ), "org/apache/maven/plugins/maven-jar-plugin/maven-metadata.xml.sha512",
                        "SHA-512" );
        assertChecksum( checksums.get( 1 ), "org/apache/maven/plugins/maven-jar-plugin/maven-metadata.xml.sha256",
                        "SHA-256" );
        assertChecksum( checksums.get( 2 ), "org/apache/maven/plugins/maven-jar-plugin/maven-metadata.xml.sha1",
                        "SHA-1" );
        assertChecksum( checksums.get( 3 ), "org/apache/maven/plugins/maven-jar-plugin/maven-metadata.xml.md5", "MD5" );
    }

    @Test
    public void testSignatureChecksums_Download()
    {
        DefaultArtifact artifact = new DefaultArtifact( "g.i.d", "a-i.d", "cls", "asc", "1.0" );
        URI uri = layout.getLocation( artifact, false );
        List<Checksum> checksums = layout.getChecksums( artifact, false, uri );
        assertChecksums( checksums, "g/i/d/a-i.d/1.0/a-i.d-1.0-cls.asc", "SHA-512", "SHA-256", "SHA-1", "MD5" );

        artifact = new DefaultArtifact( "g.i.d", "a-i.d", "cls", "jar.asc", "1.0" );
        uri = layout.getLocation( artifact, false );
        checksums = layout.getChecksums( artifact, false, uri );
        assertEquals( 0, checksums.size() );
    }

    @Test
    public void testSignatureChecksums_Upload()
    {
        DefaultArtifact artifact = new DefaultArtifact( "g.i.d", "a-i.d", "cls", "asc", "1.0" );
        URI uri = layout.getLocation( artifact, true );
        List<Checksum> checksums = layout.getChecksums( artifact, true, uri );
        assertChecksums( checksums, "g/i/d/a-i.d/1.0/a-i.d-1.0-cls.asc", "SHA-512", "SHA-256", "SHA-1", "MD5" );

        artifact = new DefaultArtifact( "g.i.d", "a-i.d", "cls", "jar.asc", "1.0" );
        uri = layout.getLocation( artifact, true );
        checksums = layout.getChecksums( artifact, true, uri );
        assertEquals( 0, checksums.size() );
    }

    @Test
    public void testSignatureChecksums_Force()
        throws Exception
    {
        session.setConfigProperty( Maven2RepositoryLayoutFactory.CONFIG_PROP_SIGNATURE_CHECKSUMS, "true" );
        layout = factory.newInstance( session, newRepo( "default" ) );
        DefaultArtifact artifact = new DefaultArtifact( "g.i.d", "a-i.d", "cls", "jar.asc", "1.0" );
        URI uri = layout.getLocation( artifact, true );
        List<Checksum> checksums = layout.getChecksums( artifact, true, uri );
        assertChecksums( checksums, "g/i/d/a-i.d/1.0/a-i.d-1.0-cls.jar.asc", "SHA-512", "SHA-256", "SHA-1", "MD5" );
    }

}
