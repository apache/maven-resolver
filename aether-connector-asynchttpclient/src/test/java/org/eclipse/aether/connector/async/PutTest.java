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
package org.eclipse.aether.connector.async;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.spi.connector.ArtifactUpload;
import org.eclipse.aether.spi.connector.MetadataUpload;
import org.eclipse.aether.transfer.ArtifactTransferException;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sonatype.tests.http.runner.junit.ConfigurationRunner;

/**
 */
@RunWith( ConfigurationRunner.class )
public class PutTest
    extends AsyncConnectorSuiteConfiguration
{

    @Test
    public void testArtifactUpload()
        throws Exception
    {
        addExpectation( "gid/aid/version/aid-version-classifier.extension", "artifact" );
        addExpectation( "gid/aid/version/aid-version-classifier.extension.sha1", sha1( "artifact" ) );
        addExpectation( "gid/aid/version/aid-version-classifier.extension.md5", md5( "artifact" ) );

        Artifact artifact = artifact( "artifact" );
        ArtifactUpload up = new ArtifactUpload( artifact, artifact.getFile() );
        List<ArtifactUpload> uploads = Arrays.asList( up );
        connector().put( uploads, null );

        ArtifactTransferException ex = up.getException();
        assertNull( ex != null ? ex.getMessage() : "", ex );
        assertExpectations();
    }

    @Test
    public void testMetadataUpload()
        throws Exception
    {
        String content = "metadata";
        addExpectation( "gid/aid/version/maven-metadata.xml", content );
        addExpectation( "gid/aid/version/maven-metadata.xml.sha1", sha1( content ) );
        addExpectation( "gid/aid/version/maven-metadata.xml.md5", md5( content ) );

        Metadata metadata = metadata( content );

        List<MetadataUpload> uploads = Arrays.asList( new MetadataUpload( metadata, metadata.getFile() ) );
        connector().put( null, uploads );

        assertExpectations();
    }

    @Test( expected = Exception.class )
    public void testClosedPut()
        throws Exception
    {
        connector().close();
        Metadata metadata = metadata( "metadata" );

        List<MetadataUpload> uploads = Arrays.asList( new MetadataUpload( metadata, metadata.getFile() ) );
        connector().put( null, uploads );
    }

    @Test
    public void testCloseAfterArtifactUpload()
        throws Exception
    {
        Artifact artifact = artifact( "artifact" );
        List<ArtifactUpload> uploads = Arrays.asList( new ArtifactUpload( artifact, artifact.getFile() ) );
        connector().put( uploads, null );
        connector().close();
    }

    @Test
    public void testCloseAfterMetadataUpload()
        throws Exception
    {
        Metadata metadata = metadata( "metadata" );

        List<MetadataUpload> uploads = Arrays.asList( new MetadataUpload( metadata, metadata.getFile() ) );
        connector().put( null, uploads );
        connector().close();
    }

    @Test
    @Ignore( "https://issues.sonatype.org/browse/AHC-5" )
    public void testArtifactWithZeroBytesFile()
        throws Exception
    {
        String content = "";
        addExpectation( "gid/aid/version/aid-version-classifier.extension", content );
        addExpectation( "gid/aid/version/aid-version-classifier.extension.sha1", sha1( content ) );
        addExpectation( "gid/aid/version/aid-version-classifier.extension.md5", md5( content ) );

        Artifact artifact = artifact( content );
        ArtifactUpload up = new ArtifactUpload( artifact, artifact.getFile() );
        List<ArtifactUpload> uploads = Arrays.asList( up );
        connector().put( uploads, null );

        ArtifactTransferException ex = up.getException();
        assertNull( ex != null ? ex.getMessage() : "", ex );
        assertExpectations();
    }

    @Test
    @Ignore( "https://issues.sonatype.org/browse/AHC-5" )
    public void testMetadataWithZeroBytesFile()
        throws Exception
    {
        String content = "";
        addExpectation( "gid/aid/version/maven-metadata.xml", content );
        addExpectation( "gid/aid/version/maven-metadata.xml.sha1", sha1( content ) );
        addExpectation( "gid/aid/version/maven-metadata.xml.md5", md5( content ) );

        Metadata metadata = metadata( content );

        List<MetadataUpload> uploads = Arrays.asList( new MetadataUpload( metadata, metadata.getFile() ) );
        connector().put( null, uploads );

        assertExpectations();
    }
    
}
