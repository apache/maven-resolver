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

import static org.eclipse.aether.transfer.TransferEvent.EventType.*;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Queue;

import org.eclipse.aether.internal.test.impl.RecordingTransferListener;
import org.eclipse.aether.internal.test.impl.TestRepositorySystemSession;
import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.eclipse.aether.spi.connector.ArtifactUpload;
import org.eclipse.aether.spi.connector.MetadataDownload;
import org.eclipse.aether.spi.connector.MetadataUpload;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.transfer.NoRepositoryConnectorException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferResource;
import org.eclipse.aether.transfer.TransferEvent.EventType;

/**
 * Utility class for connector tests. Provides methods to check the emitted transfer events for artifact and metadata
 * up- and downloads.
 */
public class TransferEventTester
{
    /**
     * Test the order of events and their properties for the successful up- and download of artifact and metadata.
     */
    public static void testSuccessfulTransferEvents( RepositoryConnectorFactory factory,
                                                     TestRepositorySystemSession session, RemoteRepository repository )
        throws NoRepositoryConnectorException, IOException
    {
        RecordingTransferListener listener = new RecordingTransferListener( session.getTransferListener() );
        session.setTransferListener( listener );

        RepositoryConnector connector = factory.newInstance( session, repository );

        byte[] pattern = "tmpFile".getBytes();
        File tmpFile = TestFileUtils.createTempFile( pattern, 10000 );
        long expectedBytes = tmpFile.length();

        Collection<ArtifactUpload> artUps = ConnectorTestUtils.createTransfers( ArtifactUpload.class, 1, tmpFile );
        Collection<ArtifactDownload> artDowns = ConnectorTestUtils.createTransfers( ArtifactDownload.class, 1, tmpFile );
        Collection<MetadataUpload> metaUps = ConnectorTestUtils.createTransfers( MetadataUpload.class, 1, tmpFile );
        Collection<MetadataDownload> metaDowns =
            ConnectorTestUtils.createTransfers( MetadataDownload.class, 1, tmpFile );

        connector.put( artUps, null );
        LinkedList<TransferEvent> events = new LinkedList<TransferEvent>( listener.getEvents() );
        checkEvents( events, expectedBytes );
        listener.clear();

        connector.get( artDowns, null );
        events = new LinkedList<TransferEvent>( listener.getEvents() );
        checkEvents( events, expectedBytes );
        listener.clear();

        connector.put( null, metaUps );
        events = new LinkedList<TransferEvent>( listener.getEvents() );
        checkEvents( events, expectedBytes );
        listener.clear();

        connector.get( null, metaDowns );
        events = new LinkedList<TransferEvent>( listener.getEvents() );
        checkEvents( events, expectedBytes );

        connector.close();
        session.setTransferListener( null );
    }

    private static void checkEvents( Queue<TransferEvent> events, long expectedBytes )
    {
        TransferEvent currentEvent = events.poll();
        String msg = "initiate event is missing";
        assertNotNull( msg, currentEvent );
        assertEquals( msg, INITIATED, currentEvent.getType() );
        checkProperties( currentEvent );

        TransferResource expectedResource = currentEvent.getResource();

        currentEvent = events.poll();
        msg = "start event is missing";
        assertNotNull( msg, currentEvent );
        assertEquals( msg, TransferEvent.EventType.STARTED, currentEvent.getType() );
        assertEquals( "bad content length", expectedBytes, currentEvent.getResource().getContentLength() );
        checkProperties( currentEvent );
        assertResourceEquals( expectedResource, currentEvent.getResource() );

        EventType progressed = TransferEvent.EventType.PROGRESSED;
        EventType succeeded = TransferEvent.EventType.SUCCEEDED;

        TransferEvent succeedEvent = null;

        int dataLength = 0;
        long transferredBytes = 0;
        while ( ( currentEvent = events.poll() ) != null )
        {
            EventType currentType = currentEvent.getType();

            assertResourceEquals( expectedResource, currentEvent.getResource() );

            if ( succeeded.equals( currentType ) )
            {
                succeedEvent = currentEvent;
                checkProperties( currentEvent );
                break;
            }
            else
            {
                assertTrue( "event is not 'succeeded' and not 'progressed'", progressed.equals( currentType ) );
                assertTrue( "wrong order of progressed events, transferredSize got smaller, last = " + transferredBytes
                                + ", current = " + currentEvent.getTransferredBytes(),
                            currentEvent.getTransferredBytes() >= transferredBytes );
                assertEquals( "bad content length", expectedBytes, currentEvent.getResource().getContentLength() );
                transferredBytes = currentEvent.getTransferredBytes();
                dataLength += currentEvent.getDataBuffer().remaining();
                checkProperties( currentEvent );
            }
        }

        // all events consumed
        assertEquals( "too many events left: " + events.toString(), 0, events.size() );

        // test transferred size
        assertEquals( "progress events transferred bytes don't match: data length does not add up", expectedBytes,
                      dataLength );
        assertEquals( "succeed event transferred bytes don't match", expectedBytes, succeedEvent.getTransferredBytes() );
    }

    private static void assertResourceEquals( TransferResource expected, TransferResource actual )
    {
        assertEquals( "TransferResource: content length does not match.", expected.getContentLength(),
                      actual.getContentLength() );
        assertEquals( "TransferResource: file does not match.", expected.getFile(), actual.getFile() );
        assertEquals( "TransferResource: repo url does not match.", expected.getRepositoryUrl(),
                      actual.getRepositoryUrl() );
        assertEquals( "TransferResource: transfer start time does not match.", expected.getTransferStartTime(),
                      actual.getTransferStartTime() );
        assertEquals( "TransferResource: name does not match.", expected.getResourceName(), actual.getResourceName() );
    }

    private static void checkProperties( TransferEvent event )
    {
        assertNotNull( "resource is null for type: " + event.getType(), event.getResource() );
        assertNotNull( "request type is null for type: " + event.getType(), event.getRequestType() );
        assertNotNull( "type is null for type: " + event.getType(), event.getType() );

        if ( PROGRESSED.equals( event.getType() ) )
        {
            assertNotNull( "data buffer is null for type: " + event.getType(), event.getDataBuffer() );
            assertTrue( "transferred byte is not set/not positive for type: " + event.getType(),
                        event.getTransferredBytes() > -1 );
        }
        else if ( SUCCEEDED.equals( event.getType() ) )
        {
            assertTrue( "transferred byte is not set/not positive for type: " + event.getType(),
                        event.getTransferredBytes() > -1 );
        }
    }

    /**
     * Test the order of events and their properties for the unsuccessful up- and download of artifact and metadata.
     * Failure is triggered by setting the file to transfer to {@code null} for uploads, and asking for a non-existent
     * item for downloads.
     */
    public static void testFailedTransferEvents( RepositoryConnectorFactory factory,
                                                 TestRepositorySystemSession session, RemoteRepository repository )
        throws NoRepositoryConnectorException, IOException
    {
        RecordingTransferListener listener = new RecordingTransferListener( session.getTransferListener() );
        session.setTransferListener( listener );

        RepositoryConnector connector = factory.newInstance( session, repository );

        byte[] pattern = "tmpFile".getBytes( "us-ascii" );
        File tmpFile = TestFileUtils.createTempFile( pattern, 10000 );

        Collection<ArtifactUpload> artUps = ConnectorTestUtils.createTransfers( ArtifactUpload.class, 1, null );
        Collection<ArtifactDownload> artDowns = ConnectorTestUtils.createTransfers( ArtifactDownload.class, 1, tmpFile );
        Collection<MetadataUpload> metaUps = ConnectorTestUtils.createTransfers( MetadataUpload.class, 1, null );
        Collection<MetadataDownload> metaDowns =
            ConnectorTestUtils.createTransfers( MetadataDownload.class, 1, tmpFile );

        connector.put( artUps, null );
        LinkedList<TransferEvent> events = new LinkedList<TransferEvent>( listener.getEvents() );
        checkFailedEvents( events, null );
        listener.clear();

        connector.get( artDowns, null );
        events = new LinkedList<TransferEvent>( listener.getEvents() );
        checkFailedEvents( events, null );
        listener.clear();

        connector.put( null, metaUps );
        events = new LinkedList<TransferEvent>( listener.getEvents() );
        checkFailedEvents( events, null );
        listener.clear();

        connector.get( null, metaDowns );
        events = new LinkedList<TransferEvent>( listener.getEvents() );
        checkFailedEvents( events, null );

        connector.close();
        session.setTransferListener( null );
    }

    private static void checkFailedEvents( Queue<TransferEvent> events, Class<? extends Throwable> expectedError )
    {
        if ( expectedError == null )
        {
            expectedError = Throwable.class;
        }

        TransferEvent currentEvent = events.poll();
        String msg = "initiate event is missing";
        assertNotNull( msg, currentEvent );
        assertEquals( msg, INITIATED, currentEvent.getType() );
        checkProperties( currentEvent );

        currentEvent = events.poll();
        msg = "fail event is missing";
        assertNotNull( msg, currentEvent );
        assertEquals( msg, TransferEvent.EventType.FAILED, currentEvent.getType() );
        checkProperties( currentEvent );
        assertNotNull( "exception is missing for fail event", currentEvent.getException() );
        Exception exception = currentEvent.getException();
        assertTrue( "exception is of wrong type, should be instance of " + expectedError + " but was "
                        + exception.getClass(), expectedError.isAssignableFrom( exception.getClass() ) );

        // all events consumed
        assertEquals( "too many events left: " + events.toString(), 0, events.size() );

    }
}
