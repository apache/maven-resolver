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
package org.eclipse.aether.connector.file;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.eclipse.aether.spi.connector.ArtifactUpload;
import org.eclipse.aether.spi.connector.MetadataDownload;
import org.eclipse.aether.spi.connector.MetadataUpload;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.spi.connector.Transfer;
import org.eclipse.aether.spi.connector.Transfer.State;
import org.eclipse.aether.spi.io.FileProcessor;
import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.spi.log.NullLoggerFactory;
import org.eclipse.aether.transfer.ArtifactNotFoundException;
import org.eclipse.aether.transfer.ArtifactTransferException;
import org.eclipse.aether.transfer.ChecksumFailureException;
import org.eclipse.aether.transfer.MetadataNotFoundException;
import org.eclipse.aether.transfer.MetadataTransferException;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferEvent.RequestType;
import org.eclipse.aether.transfer.TransferResource;
import org.eclipse.aether.util.ChecksumUtils;
import org.eclipse.aether.util.layout.MavenDefaultLayout;
import org.eclipse.aether.util.layout.RepositoryLayout;

/**
 * The actual class doing all the work. Handles artifact and metadata up- and downloads.
 */
class FileRepositoryWorker
    implements Runnable
{

    private Logger logger = NullLoggerFactory.LOGGER;

    private FileProcessor fileProcessor;

    private enum Direction
    {
        UPLOAD( TransferEvent.RequestType.PUT ), DOWNLOAD( TransferEvent.RequestType.GET );

        TransferEvent.RequestType type;

        private Direction( TransferEvent.RequestType type )
        {
            this.type = type;
        }

        public RequestType getType()
        {
            return type;
        }
    }

    private static LinkedHashMap<String, String> checksumAlgos;

    private final RepositoryLayout layout = new MavenDefaultLayout();

    private final TransferWrapper transfer;

    private final RemoteRepository repository;

    private final TransferEventCatapult catapult;

    private final Direction direction;

    private final TransferResource resource;

    private final TransferEvent.Builder eventBuilder;

    static
    {
        checksumAlgos = new LinkedHashMap<String, String>( 4 );
        checksumAlgos.put( "SHA-1", ".sha1" );
        checksumAlgos.put( "MD5", ".md5" );
    }

    private FileRepositoryWorker( TransferWrapper transfer, RemoteRepository repository, Direction direction,
                                  RepositorySystemSession session )
    {
        if ( repository == null )
        {
            throw new IllegalArgumentException( "RemoteRepository may not be null." );
        }
        if ( session == null )
        {
            throw new IllegalArgumentException( "RepositorySystemSession may not be null." );
        }

        this.catapult = new TransferEventCatapult( session.getTransferListener() );
        resource = newResource( transfer, repository );
        eventBuilder = new TransferEvent.Builder( session, resource ).setRequestType( direction.getType() );

        this.direction = direction;
        this.repository = repository;

        this.transfer = transfer;
    }

    /**
     * Initialize the worker for an artifact upload.
     * 
     * @param transfer The actual {@link Transfer}-object. May not be <code>null</code>.
     * @param repository The repository definition. May not be <code>null</code>.
     * @param session The current repository system session. May not be <code>null</code>.
     */
    public FileRepositoryWorker( ArtifactUpload transfer, RemoteRepository repository, RepositorySystemSession session )
    {
        this( new TransferWrapper( transfer ), repository, Direction.UPLOAD, session );
    }

    /**
     * Initialize the worker for an artifact download.
     * 
     * @param transfer The actual {@link Transfer}-object. May not be <code>null</code>.
     * @param repository The repository definition. May not be <code>null</code>.
     * @param session The current repository system session. May not be <code>null</code>.
     */
    public FileRepositoryWorker( ArtifactDownload transfer, RemoteRepository repository, RepositorySystemSession session )
    {
        this( new TransferWrapper( transfer ), repository, Direction.DOWNLOAD, session );
    }

    /**
     * Initialize the worker for an metadata download.
     * 
     * @param transfer The actual {@link Transfer}-object. May not be <code>null</code>.
     * @param repository The repository definition. May not be <code>null</code>.
     * @param session The current repository system session. May not be <code>null</code>.
     */
    public FileRepositoryWorker( MetadataDownload transfer, RemoteRepository repository, RepositorySystemSession session )
    {
        this( new TransferWrapper( transfer ), repository, Direction.DOWNLOAD, session );
    }

    /**
     * Initialize the worker for an metadata upload.
     * 
     * @param transfer The actual {@link Transfer}-object. May not be <code>null</code>.
     * @param repository The repository definition. May not be <code>null</code>.
     * @param session The current repository system session. May not be <code>null</code>.
     */
    public FileRepositoryWorker( MetadataUpload transfer, RemoteRepository repository, RepositorySystemSession session )
    {
        this( new TransferWrapper( transfer ), repository, Direction.UPLOAD, session );
    }

    /**
     * Do transfer according to {@link RepositoryConnector} specifications.
     * 
     * @see FileRepositoryConnector
     */
    public void run()
    {
        File target = null;
        long totalTransferred = -1;
        try
        {
            transfer.setState( State.ACTIVE );
            TransferEvent.Builder event = newEvent( transfer );
            catapult.fireInitiated( event );

            File baseDir = new File( PathUtils.basedir( repository.getUrl() ) );
            File localFile = transfer.getFile();
            File repoFile = new File( baseDir, transfer.getRelativePath() );
            File src = null;

            switch ( direction )
            {
                case UPLOAD:
                    src = localFile;
                    target = repoFile;
                    break;
                case DOWNLOAD:
                    src = repoFile;
                    target = localFile;
                    break;
            }

            if ( transfer.isExistenceCheck() )
            {
                if ( !src.exists() )
                {
                    throw new FileNotFoundException( src.getAbsolutePath() );
                }
            }
            else
            {
                File tmp = tmpfile( target );
                totalTransferred = copy( src, tmp );
                fileProcessor.move( tmp, target );

                switch ( direction )
                {
                    case UPLOAD:
                        writeChecksum( src, target.getPath() );
                        break;
                    case DOWNLOAD:
                        verifyChecksum( src );
                        break;
                }
            }
        }
        catch ( FileNotFoundException e )
        {
            switch ( transfer.getType() )
            {
                case ARTIFACT:
                    ArtifactTransferException artEx;
                    if ( Direction.DOWNLOAD.equals( direction ) )
                    {
                        artEx = new ArtifactNotFoundException( transfer.getArtifact(), repository );
                    }
                    else
                    {
                        artEx = new ArtifactTransferException( transfer.getArtifact(), repository, e );
                    }
                    transfer.setException( artEx );
                    break;
                case METADATA:
                    MetadataTransferException mdEx;
                    if ( Direction.DOWNLOAD.equals( direction ) )
                    {
                        mdEx = new MetadataNotFoundException( transfer.getMetadata(), repository );
                    }
                    else
                    {
                        mdEx = new MetadataTransferException( transfer.getMetadata(), repository, e );
                    }
                    transfer.setException( mdEx );
                    break;
            }

        }
        catch ( Throwable t )
        {
            logger.debug( t.getMessage(), t );
            switch ( transfer.getType() )
            {
                case ARTIFACT:
                    transfer.setException( new ArtifactTransferException( transfer.getArtifact(), repository, t ) );
                    break;
                case METADATA:
                    transfer.setException( new MetadataTransferException( transfer.getMetadata(), repository, t ) );
                    break;
            }
        }
        finally
        {
            transfer.setState( State.DONE );
            if ( transfer.getException() == null )
            {
                TransferEvent.Builder event = newEvent( transfer );
                event.setTransferredBytes( totalTransferred );
                catapult.fireSucceeded( event );
            }
            else
            {
                // cleanup
                if ( direction.equals( Direction.UPLOAD ) )
                {
                    for ( String ext : checksumAlgos.values() )
                    {
                        new File( target.getPath() + ext ).delete();
                    }
                }
                if ( target != null )
                {
                    target.delete();
                }

                TransferEvent.Builder event = newEvent( transfer );
                catapult.fireFailed( event );
            }
        }
    }

    private void writeChecksum( File src, String targetPath )
        throws IOException, Throwable
    {
        // write checksum files
        Map<String, Object> crcs = ChecksumUtils.calc( src, checksumAlgos.keySet() );
        for ( Entry<String, Object> crc : crcs.entrySet() )
        {
            String name = crc.getKey();
            Object sum = crc.getValue();

            if ( sum instanceof Throwable )
            {
                throw (Throwable) sum;
            }

            File crcTarget = new File( targetPath + checksumAlgos.get( name ) );
            FileWriter crcWriter = new FileWriter( crcTarget );
            crcWriter.write( sum.toString() );
            crcWriter.close();
        }
    }

    private void verifyChecksum( File src )
        throws ChecksumFailureException, IOException, TransferCancelledException
    {
        if ( RepositoryPolicy.CHECKSUM_POLICY_IGNORE.equals( transfer.getChecksumPolicy() ) )
        {
            return;
        }
        Map<String, Object> crcs = ChecksumUtils.calc( src, checksumAlgos.keySet() );
        boolean verified = false;
        try
        {
            for ( Entry<String, String> entry : checksumAlgos.entrySet() )
            {
                try
                {
                    String sum = ChecksumUtils.read( new File( src.getPath() + entry.getValue() ) );
                    verified = sum.equalsIgnoreCase( crcs.get( entry.getKey() ).toString() );
                    if ( !verified )
                    {
                        throw new ChecksumFailureException( sum, crcs.get( entry.getKey() ).toString() );
                    }
                    break;
                }
                catch ( IOException e )
                {
                    // skip verify - try next algorithm
                    continue;
                }
            }

            // all algorithms checked
            if ( !verified )
            {
                throw new ChecksumFailureException( "no supported algorithms found" );
            }
        }
        catch ( ChecksumFailureException e )
        {
            if ( RepositoryPolicy.CHECKSUM_POLICY_FAIL.equals( transfer.getChecksumPolicy() ) )
            {
                throw e;
            }

            TransferEvent.Builder event = newEvent( transfer );
            event.setException( e );
            catapult.fireCorrupted( event );
        }
    }

    private long copy( File src, File target )
        throws TransferCancelledException, IOException
    {
        if ( src == null )
        {
            throw new IllegalArgumentException( "source file not specified" );
        }
        if ( !src.isFile() )
        {
            throw new FileNotFoundException( src.getAbsolutePath() );
        }
        if ( target == null )
        {
            throw new IllegalArgumentException( "target file not specified" );
        }

        resource.setContentLength( src.length() );
        TransferEvent.Builder event = newEvent( transfer );
        catapult.fireStarted( event );

        return fileProcessor.copy( src, target, new FileProcessor.ProgressListener()
        {

            int total = 0;

            public void progressed( ByteBuffer buffer )
                throws IOException
            {
                total += buffer.remaining();
                TransferEvent.Builder event = newEvent( transfer );
                event.setDataBuffer( buffer ).setTransferredBytes( total );
                try
                {
                    catapult.fireProgressed( event );
                }
                catch ( TransferCancelledException e )
                {
                    throw new IOException( "Transfer was cancelled: " + e.getMessage() );
                }
            }
        } );
    }

    private TransferEvent.Builder newEvent( TransferWrapper transfer )
    {
        return eventBuilder.copy().setException( transfer.getException() );
    }

    private TransferResource newResource( TransferWrapper transfer, RemoteRepository repository )
    {
        String resourceName = null;
        switch ( transfer.getType() )
        {
            case ARTIFACT:
                Artifact artifact = transfer.getArtifact();
                resourceName = layout.getPath( artifact ).getPath();
                break;
            case METADATA:
                Metadata metadata = transfer.getMetadata();
                resourceName = layout.getPath( metadata ).getPath();
                break;
        }
        return new TransferResource( PathUtils.decode( repository.getUrl() ), resourceName, transfer.getFile(),
                                     transfer.getTrace() );
    }

    public void setLogger( Logger logger )
    {
        this.logger = logger;
    }

    public void setFileProcessor( FileProcessor fileProcessor )
    {
        this.fileProcessor = fileProcessor;
    }

    private File tmpfile( File target )
    {
        return new File( target.getAbsolutePath() + ".tmp"
            + UUID.randomUUID().toString().replace( "-", "" ).substring( 0, 16 ) );
    }

}
