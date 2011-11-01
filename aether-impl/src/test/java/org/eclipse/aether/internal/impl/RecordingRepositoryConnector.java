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
package org.eclipse.aether.internal.impl;

import static junit.framework.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.eclipse.aether.spi.connector.ArtifactUpload;
import org.eclipse.aether.spi.connector.MetadataDownload;
import org.eclipse.aether.spi.connector.MetadataUpload;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.spi.connector.Transfer.State;
import org.eclipse.aether.transfer.ArtifactTransferException;
import org.eclipse.aether.transfer.MetadataTransferException;

/**
 * A repository connector recording all get/put-requests and faking the results.
 */
class RecordingRepositoryConnector
    implements RepositoryConnector
{
    private Artifact[] expectGet;

    private Artifact[] expectPut;

    private Metadata[] expectGetMD;

    private Metadata[] expectPutMD;

    private List<Artifact> actualGet = new ArrayList<Artifact>();

    private List<Metadata> actualGetMD = new ArrayList<Metadata>();

    private List<Artifact> actualPut = new ArrayList<Artifact>();

    private List<Metadata> actualPutMD = new ArrayList<Metadata>();

    public RecordingRepositoryConnector( Artifact[] expectGet, Artifact[] expectPut, Metadata[] expectGetMD,
                                         Metadata[] expectPutMD )
    {
        this.expectGet = expectGet;
        this.expectPut = expectPut;
        this.expectGetMD = expectGetMD;
        this.expectPutMD = expectPutMD;
    }

    public RecordingRepositoryConnector()
    {
    }

    public void get( Collection<? extends ArtifactDownload> artifactDownloads,
                     Collection<? extends MetadataDownload> metadataDownloads )
    {
        try
        {
            if ( artifactDownloads != null )
            {
                for ( ArtifactDownload artifactDownload : artifactDownloads )
                {
                    artifactDownload.setState( State.ACTIVE );
                    Artifact artifact = artifactDownload.getArtifact();
                    this.actualGet.add( artifact );
                    TestFileUtils.write( artifact.toString(), artifactDownload.getFile() );
                    artifactDownload.setState( State.DONE );
                }
            }
            if ( metadataDownloads != null )
            {
                for ( MetadataDownload metadataDownload : metadataDownloads )
                {
                    metadataDownload.setState( State.ACTIVE );
                    Metadata metadata = metadataDownload.getMetadata();
                    this.actualGetMD.add( metadata );
                    TestFileUtils.write( metadata.toString(), metadataDownload.getFile() );
                    metadataDownload.setState( State.DONE );
                }
            }
        }
        catch ( IOException e )
        {
            throw new IllegalStateException( "Cannot create temporary file", e );
        }

    }

    public void put( Collection<? extends ArtifactUpload> artifactUploads,
                     Collection<? extends MetadataUpload> metadataUploads )
    {
        if ( artifactUploads != null )
        {
            for ( ArtifactUpload artifactUpload : artifactUploads )
            {
                // mimic "real" connector
                artifactUpload.setState( State.ACTIVE );
                if ( artifactUpload.getFile() == null )
                {
                    artifactUpload.setException( new ArtifactTransferException( artifactUpload.getArtifact(), null,
                                                                                "no file" ) );
                }
                this.actualPut.add( artifactUpload.getArtifact() );
                artifactUpload.setState( State.DONE );
            }
        }
        if ( metadataUploads != null )
        {
            for ( MetadataUpload metadataUpload : metadataUploads )
            {
                // mimic "real" connector
                metadataUpload.setState( State.ACTIVE );
                if ( metadataUpload.getFile() == null )
                {
                    metadataUpload.setException( new MetadataTransferException( metadataUpload.getMetadata(), null,
                                                                                "no file" ) );
                }
                this.actualPutMD.add( metadataUpload.getMetadata() );
                metadataUpload.setState( State.DONE );
            }
        }

    }

    public void close()
    {
    }

    public void assertSeenExpected()
    {
        assertSeenExpected( actualGet, expectGet );
        assertSeenExpected( actualGetMD, expectGetMD );
        assertSeenExpected( actualPut, expectPut );
        assertSeenExpected( actualPutMD, expectPutMD );
    }

    private void assertSeenExpected( List<? extends Object> actual, Object[] expected )
    {
        if ( expected == null )
        {
            expected = new Object[0];
        }

        assertEquals( "different number of expected and actual elements:\n", expected.length, actual.size() );
        int idx = 0;
        for ( Object actualObject : actual )
        {
            assertEquals( "seen object differs", expected[idx++], actualObject );
        }
    }

    public List<Artifact> getActualArtifactGetRequests()
    {
        return actualGet;
    }

    public List<Metadata> getActualMetadataGetRequests()
    {
        return actualGetMD;
    }

    public List<Artifact> getActualArtifactPutRequests()
    {
        return actualPut;
    }

    public List<Metadata> getActualMetadataPutRequests()
    {
        return actualPutMD;
    }

    public void setExpectGet( Artifact... expectGet )
    {
        this.expectGet = expectGet;
    }

    public void setExpectPut( Artifact... expectPut )
    {
        this.expectPut = expectPut;
    }

    public void setExpectGet( Metadata... expectGetMD )
    {
        this.expectGetMD = expectGetMD;
    }

    public void setExpectPut( Metadata... expectPutMD )
    {
        this.expectPutMD = expectPutMD;
    }

    public void resetActual()
    {
        this.actualGet = new ArrayList<Artifact>();
        this.actualGetMD = new ArrayList<Metadata>();
        this.actualPut = new ArrayList<Artifact>();
        this.actualPutMD = new ArrayList<Metadata>();
    }

}
