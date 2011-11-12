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
package org.eclipse.aether.internal.test.util.connector.suite;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.internal.test.impl.TestRepositorySystemSession;
import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.eclipse.aether.internal.test.util.impl.StubArtifact;
import org.eclipse.aether.internal.test.util.impl.StubMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.eclipse.aether.spi.connector.ArtifactUpload;
import org.eclipse.aether.spi.connector.MetadataDownload;
import org.eclipse.aether.spi.connector.MetadataUpload;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.Transfer;
import org.eclipse.aether.spi.connector.Transfer.State;
import org.eclipse.aether.transfer.NoRepositoryConnectorException;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferListener;
import org.junit.Test;

/**
 * The ConnectorTestSuite bundles standard tests for {@link RepositoryConnector}s.
 * <p>
 * To use these tests, provide a (Junit4-)class extending this class, and provide a default constructor calling
 * {@link ConnectorTestSuite#ConnectorTestSuite(ConnectorTestSetup)} with a self-implemented {@link ConnectorTestSetup}.
 */
public abstract class ConnectorTestSuite
    extends ConnectorTestSuiteSetup
{

    public ConnectorTestSuite( ConnectorTestSetup setup )
    {
        super( setup );
    }

    /**
     * Test successful event order.
     * 
     * @see TransferEventTester#testSuccessfulTransferEvents(RepositoryConnectorFactory, TestRepositorySystemSession,
     *      RemoteRepository)
     */
    @Test
    public void testSuccessfulEvents()
        throws NoRepositoryConnectorException, IOException
    {
        TransferEventTester.testSuccessfulTransferEvents( factory(), session, repository );
    }

    @Test
    public void testFailedEvents()
        throws NoRepositoryConnectorException, IOException
    {
        TransferEventTester.testFailedTransferEvents( factory(), session, repository );
    }

    @Test
    public void testFileHandleLeakage()
        throws IOException, NoRepositoryConnectorException
    {

        StubArtifact artifact = new StubArtifact( "testGroup", "testArtifact", "", "jar", "1-test" );
        StubMetadata metadata =
            new StubMetadata( "testGroup", "testArtifact", "1-test", "maven-metadata.xml",
                              Metadata.Nature.RELEASE_OR_SNAPSHOT );

        RepositoryConnector connector = factory().newInstance( session, repository );

        File tmpFile = TestFileUtils.createTempFile( "testFileHandleLeakage" );
        ArtifactUpload artUp = new ArtifactUpload( artifact, tmpFile );
        connector.put( Arrays.asList( artUp ), null );
        assertTrue( "Leaking file handle in artifact upload", tmpFile.delete() );

        tmpFile = TestFileUtils.createTempFile( "testFileHandleLeakage" );
        MetadataUpload metaUp = new MetadataUpload( metadata, tmpFile );
        connector.put( null, Arrays.asList( metaUp ) );
        assertTrue( "Leaking file handle in metadata upload", tmpFile.delete() );

        tmpFile = TestFileUtils.createTempFile( "testFileHandleLeakage" );
        ArtifactDownload artDown = new ArtifactDownload( artifact, null, tmpFile, null );
        connector.get( Arrays.asList( artDown ), null );
        new File( tmpFile.getAbsolutePath() + ".sha1" ).deleteOnExit();
        assertTrue( "Leaking file handle in artifact download", tmpFile.delete() );

        tmpFile = TestFileUtils.createTempFile( "testFileHandleLeakage" );
        MetadataDownload metaDown = new MetadataDownload( metadata, null, tmpFile, null );
        connector.get( null, Arrays.asList( metaDown ) );
        new File( tmpFile.getAbsolutePath() + ".sha1" ).deleteOnExit();
        assertTrue( "Leaking file handle in metadata download", tmpFile.delete() );

        connector.close();
    }

    @Test
    public void testBlocking()
        throws NoRepositoryConnectorException, IOException
    {

        RepositoryConnector connector = factory().newInstance( session, repository );

        int count = 10;

        byte[] pattern = "tmpFile".getBytes( "UTF-8" );
        File tmpFile = TestFileUtils.createTempFile( pattern, 100000 );

        List<ArtifactUpload> artUps = ConnectorTestUtils.createTransfers( ArtifactUpload.class, count, tmpFile );
        List<MetadataUpload> metaUps = ConnectorTestUtils.createTransfers( MetadataUpload.class, count, tmpFile );
        List<ArtifactDownload> artDowns = ConnectorTestUtils.createTransfers( ArtifactDownload.class, count, null );
        List<MetadataDownload> metaDowns = ConnectorTestUtils.createTransfers( MetadataDownload.class, count, null );

        // this should block until all transfers are done - racing condition, better way to test this?
        connector.put( artUps, metaUps );
        connector.get( artDowns, metaDowns );

        for ( int i = 0; i < count; i++ )
        {
            ArtifactUpload artUp = artUps.get( i );
            MetadataUpload metaUp = metaUps.get( i );
            ArtifactDownload artDown = artDowns.get( i );
            MetadataDownload metaDown = metaDowns.get( i );

            assertTrue( Transfer.State.DONE.equals( artUp.getState() ) );
            assertTrue( Transfer.State.DONE.equals( artDown.getState() ) );
            assertTrue( Transfer.State.DONE.equals( metaUp.getState() ) );
            assertTrue( Transfer.State.DONE.equals( metaDown.getState() ) );
        }

        connector.close();
    }

    @Test
    public void testMkdirConcurrencyBug()
        throws IOException, NoRepositoryConnectorException
    {
        RepositoryConnector connector = factory().newInstance( session, repository );
        File artifactFile = TestFileUtils.createTempFile( "mkdirsBug0" );
        File metadataFile = TestFileUtils.createTempFile( "mkdirsBug1" );

        int numTransfers = 2;

        ArtifactUpload[] artUps = new ArtifactUpload[numTransfers];
        MetadataUpload[] metaUps = new MetadataUpload[numTransfers];

        for ( int i = 0; i < numTransfers; i++ )
        {
            StubArtifact art = new StubArtifact( "testGroup", "testArtifact", "", "jar", i + "-test" );
            StubMetadata meta =
                new StubMetadata( "testGroup", "testArtifact", i + "-test", "maven-metadata.xml",
                                  Metadata.Nature.RELEASE_OR_SNAPSHOT );

            ArtifactUpload artUp = new ArtifactUpload( art, artifactFile );
            MetadataUpload metaUp = new MetadataUpload( meta, metadataFile );

            artUps[i] = artUp;
            metaUps[i] = metaUp;
        }

        connector.put( Arrays.asList( artUps ), null );
        connector.put( null, Arrays.asList( metaUps ) );

        File localRepo = session.getLocalRepository().getBasedir();

        StringBuilder localPath = new StringBuilder( localRepo.getAbsolutePath() );

        for ( int i = 0; i < 50; i++ )
        {
            localPath.append( "/d" );
        }

        ArtifactDownload[] artDowns = new ArtifactDownload[numTransfers];
        MetadataDownload[] metaDowns = new MetadataDownload[numTransfers];

        for ( int m = 0; m < 20; m++ )
        {
            for ( int i = 0; i < numTransfers; i++ )
            {
                File artFile = new File( localPath.toString() + "/a" + i );
                File metaFile = new File( localPath.toString() + "/m" + i );

                StubArtifact art = new StubArtifact( "testGroup", "testArtifact", "", "jar", i + "-test" );
                StubMetadata meta =
                    new StubMetadata( "testGroup", "testArtifact", i + "-test", "maven-metadata.xml",
                                      Metadata.Nature.RELEASE_OR_SNAPSHOT );

                ArtifactDownload artDown =
                    new ArtifactDownload( art, null, artFile, RepositoryPolicy.CHECKSUM_POLICY_FAIL );
                MetadataDownload metaDown =
                    new MetadataDownload( meta, null, metaFile, RepositoryPolicy.CHECKSUM_POLICY_FAIL );

                artDowns[i] = artDown;
                metaDowns[i] = metaDown;
            }

            connector.get( Arrays.asList( artDowns ), Arrays.asList( metaDowns ) );

            for ( int j = 0; j < numTransfers; j++ )
            {
                ArtifactDownload artDown = artDowns[j];
                MetadataDownload metaDown = metaDowns[j];

                assertNull( "artifact download had exception: " + artDown.getException(), artDown.getException() );
                assertNull( "metadata download had exception: " + metaDown.getException(), metaDown.getException() );
                assertEquals( State.DONE, artDown.getState() );
                assertEquals( State.DONE, metaDown.getState() );
            }

            TestFileUtils.delete( localRepo );
        }

        connector.close();
    }

    /**
     * See https://issues.sonatype.org/browse/AETHER-8
     */
    @Test
    public void testTransferZeroBytesFile()
        throws IOException, NoRepositoryConnectorException
    {
        File emptyFile = TestFileUtils.createTempFile( "" );

        Artifact artifact = new StubArtifact( "gid:aid:ext:ver" );
        ArtifactUpload upA = new ArtifactUpload( artifact, emptyFile );
        File dir = TestFileUtils.createTempDir( "con-test" );
        File downAFile = new File( dir, "downA.file" );
        downAFile.deleteOnExit();
        ArtifactDownload downA = new ArtifactDownload( artifact, "", downAFile, RepositoryPolicy.CHECKSUM_POLICY_FAIL );

        Metadata metadata =
            new StubMetadata( "gid", "aid", "ver", "maven-metadata.xml", Metadata.Nature.RELEASE_OR_SNAPSHOT );
        MetadataUpload upM = new MetadataUpload( metadata, emptyFile );
        File downMFile = new File( dir, "downM.file" );
        downMFile.deleteOnExit();
        MetadataDownload downM = new MetadataDownload( metadata, "", downMFile, RepositoryPolicy.CHECKSUM_POLICY_FAIL );

        RepositoryConnector connector = factory().newInstance( session, repository );
        connector.put( Arrays.asList( upA ), Arrays.asList( upM ) );
        connector.get( Arrays.asList( downA ), Arrays.asList( downM ) );

        assertNull( String.valueOf( upA.getException() ), upA.getException() );
        assertNull( String.valueOf( upM.getException() ), upM.getException() );
        assertNull( String.valueOf( downA.getException() ), downA.getException() );
        assertNull( String.valueOf( downM.getException() ), downM.getException() );

        assertEquals( 0, downAFile.length() );
        assertEquals( 0, downMFile.length() );

        connector.close();
    }

    @Test
    public void testProgressEventsDataBuffer()
        throws UnsupportedEncodingException, IOException, NoSuchAlgorithmException, NoRepositoryConnectorException
    {
        byte[] bytes = "These are the test contents.\n".getBytes( "UTF-8" );
        int count = 120000;
        MessageDigest digest = MessageDigest.getInstance( "SHA-1" );
        for ( int i = 0; i < count; i++ )
        {
            digest.update( bytes );
        }
        byte[] hash = digest.digest();

        File file = TestFileUtils.createTempFile( bytes, count );

        Artifact artifact = new StubArtifact( "gid:aid:ext:ver" );
        ArtifactUpload upA = new ArtifactUpload( artifact, file );

        File dir = TestFileUtils.createTempDir( "con-test" );
        File downAFile = new File( dir, "downA.file" );
        downAFile.deleteOnExit();
        ArtifactDownload downA = new ArtifactDownload( artifact, "", downAFile, RepositoryPolicy.CHECKSUM_POLICY_FAIL );

        Metadata metadata =
            new StubMetadata( "gid", "aid", "ver", "maven-metadata.xml", Metadata.Nature.RELEASE_OR_SNAPSHOT );
        MetadataUpload upM = new MetadataUpload( metadata, file );
        File downMFile = new File( dir, "downM.file" );
        downMFile.deleteOnExit();
        MetadataDownload downM = new MetadataDownload( metadata, "", downMFile, RepositoryPolicy.CHECKSUM_POLICY_FAIL );

        DigestingTransferListener listener = new DigestingTransferListener();
        session.setTransferListener( listener );

        RepositoryConnector connector = factory().newInstance( session, repository );
        connector.put( Arrays.asList( upA ), null );
        assertArrayEquals( hash, listener.getHash() );
        listener.rewind();
        connector.put( null, Arrays.asList( upM ) );
        assertArrayEquals( hash, listener.getHash() );
        listener.rewind();
        connector.get( Arrays.asList( downA ), null );
        assertArrayEquals( hash, listener.getHash() );
        listener.rewind();
        connector.get( null, Arrays.asList( downM ) );
        assertArrayEquals( hash, listener.getHash() );
        listener.rewind();

        connector.close();
    }

    private final class DigestingTransferListener
        implements TransferListener
    {

        private MessageDigest digest;

        private synchronized void initDigest()
            throws NoSuchAlgorithmException
        {
            digest = MessageDigest.getInstance( "SHA-1" );
        }

        public DigestingTransferListener()
            throws NoSuchAlgorithmException
        {
            initDigest();
        }

        public void rewind()
            throws NoSuchAlgorithmException
        {
            initDigest();
        }

        public void transferSucceeded( TransferEvent event )
        {
        }

        public void transferStarted( TransferEvent event )
            throws TransferCancelledException
        {
        }

        public synchronized void transferProgressed( TransferEvent event )
            throws TransferCancelledException
        {
            digest.update( event.getDataBuffer() );
        }

        public void transferInitiated( TransferEvent event )
            throws TransferCancelledException
        {
        }

        public void transferFailed( TransferEvent event )
        {
        }

        public void transferCorrupted( TransferEvent event )
            throws TransferCancelledException
        {
        }

        public synchronized byte[] getHash()
        {
            return digest.digest();
        }
    }

}
