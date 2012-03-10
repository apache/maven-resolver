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
package org.eclipse.aether.connector.file;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.net.URI;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.connector.file.FileRepositoryWorker;
import org.eclipse.aether.internal.test.impl.TestFileProcessor;
import org.eclipse.aether.internal.test.impl.TestRepositorySystemSession;
import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata.Nature;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.eclipse.aether.spi.connector.ArtifactUpload;
import org.eclipse.aether.spi.connector.MetadataDownload;
import org.eclipse.aether.spi.connector.MetadataUpload;
import org.eclipse.aether.transfer.ArtifactTransferException;
import org.eclipse.aether.transfer.MetadataTransferException;
import org.eclipse.aether.util.repository.layout.MavenDefaultLayout;
import org.eclipse.aether.util.repository.layout.RepositoryLayout;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ArtifactWorkerTest
{

    private RemoteRepository repository;

    private TestRepositorySystemSession session;

    private RepositoryLayout layout;

    @Before
    public void setup()
        throws IOException
    {
        repository =
            new RemoteRepository( "test", "default",
                                  TestFileUtils.createTempDir( "test-remote-repository" ).toURI().toURL().toString() );
        session = new TestRepositorySystemSession();
        layout = new MavenDefaultLayout();
    }

    @After
    public void cleanup()
        throws Exception
    {
        TestFileUtils.delete( new File( new URI( repository.getUrl() ) ) );
    }

    @Test
    public void testArtifactTransfer()
        throws IOException, ArtifactTransferException
    {
        DefaultArtifact artifact = new DefaultArtifact( "test", "artId1", "jar", "1" );
        String expectedContent = "Dies ist ein Test.";

        uploadArtifact( artifact, expectedContent );

        File file = downloadArtifact( artifact );

        assertContentEquals( file, expectedContent );
    }

    private File downloadArtifact( DefaultArtifact artifact )
        throws IOException, ArtifactTransferException
    {
        File file = TestFileUtils.createTempFile( "" );
        ArtifactDownload down = new ArtifactDownload( artifact, "", file, "" );
        down.setChecksumPolicy( RepositoryPolicy.CHECKSUM_POLICY_FAIL );
        FileRepositoryWorker worker = new FileRepositoryWorker( down, repository, session );
        worker.setFileProcessor( TestFileProcessor.INSTANCE );
        worker.run();
        if ( down.getException() != null )
        {
            throw down.getException();
        }
        return file;
    }

    private void uploadArtifact( Artifact artifact, String content )
        throws IOException, ArtifactTransferException
    {
        File file = TestFileUtils.createTempFile( content );

        ArtifactUpload transfer = new ArtifactUpload( artifact, file );
        FileRepositoryWorker worker = new FileRepositoryWorker( transfer, repository, session );
        worker.setFileProcessor( TestFileProcessor.INSTANCE );
        worker.run();

        TestFileUtils.delete( file );
        if ( transfer.getException() != null )
        {
            throw transfer.getException();
        }
    }

    @Test
    public void testMetadataTransfer()
        throws IOException, MetadataTransferException
    {
        String expectedContent = "Dies ist ein Test.";
        File srcFile = TestFileUtils.createTempFile( expectedContent );

        DefaultMetadata metadata = new DefaultMetadata( "test", "artId1", "1", "jar", Nature.RELEASE_OR_SNAPSHOT );
        MetadataUpload up = new MetadataUpload( metadata, srcFile );
        FileRepositoryWorker worker = new FileRepositoryWorker( up, repository, session );
        worker.setFileProcessor( TestFileProcessor.INSTANCE );
        worker.run();
        if ( up.getException() != null )
        {
            throw up.getException();
        }

        File targetFile = TestFileUtils.createTempFile( "" );
        TestFileUtils.delete( targetFile );

        MetadataDownload down = new MetadataDownload();
        down.setChecksumPolicy( RepositoryPolicy.CHECKSUM_POLICY_FAIL );
        down.setMetadata( metadata ).setFile( targetFile );
        worker = new FileRepositoryWorker( down, repository, session );
        worker.setFileProcessor( TestFileProcessor.INSTANCE );
        worker.run();

        if ( down.getException() != null )
        {
            throw down.getException();
        }

        assertTrue( "download did not happen.", targetFile.exists() );

        assertContentEquals( targetFile, expectedContent );
    }

    private void assertContentEquals( File file, String expectedContent )
        throws IOException
    {
        byte[] expected = expectedContent.getBytes( "UTF-8" );
        byte[] actual = TestFileUtils.getContent( file );

        assertArrayEquals( expected, actual );
    }

    @Test
    public void testDecodeURL()
        throws ArtifactTransferException, IOException
    {
        String enc = "%72%65%70%6F";
        File dir = TestFileUtils.createTempDir();
        String repoDir = dir.toURI().toURL().toString() + "/" + enc;
        repository = new RemoteRepository( "test", "default", repoDir );

        Artifact artifact = new DefaultArtifact( "gid", "aid", "jar", "ver" );
        String content = "test content";
        uploadArtifact( artifact, content );

        File repo = new File( dir, "repo" );
        assertTrue( "Repository from encoded URL does not exist.", repo.exists() );
        assertTrue( "Artifact was not uploaded correctly.",
                    new File( repo, layout.getPath( artifact ).getRawPath() ).exists() );
        TestFileUtils.delete( dir );
    }

}
