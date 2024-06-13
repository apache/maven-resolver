/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.eclipse.aether.internal.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.eclipse.aether.spi.connector.ArtifactUpload;
import org.eclipse.aether.spi.connector.MetadataDownload;
import org.eclipse.aether.spi.connector.MetadataUpload;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.spi.connector.Transfer;
import org.eclipse.aether.transfer.ArtifactTransferException;
import org.eclipse.aether.transfer.MetadataTransferException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferListener;
import org.eclipse.aether.transfer.TransferResource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A repository connector recording all get/put-requests and faking the results.
 */
class RecordingRepositoryConnector implements RepositoryConnector {

    RepositorySystemSession session;

    boolean fail;

    private Artifact[] expectGet;

    private Artifact[] expectPut;

    private Metadata[] expectGetMD;

    private Metadata[] expectPutMD;

    private List<Artifact> actualGet = new ArrayList<>();

    private List<Metadata> actualGetMD = new ArrayList<>();

    private List<Artifact> actualPut = new ArrayList<>();

    private List<Metadata> actualPutMD = new ArrayList<>();

    public RecordingRepositoryConnector(
            RepositorySystemSession session,
            Artifact[] expectGet,
            Artifact[] expectPut,
            Metadata[] expectGetMD,
            Metadata[] expectPutMD) {
        this.session = session;
        this.expectGet = expectGet;
        this.expectPut = expectPut;
        this.expectGetMD = expectGetMD;
        this.expectPutMD = expectPutMD;
    }

    public RecordingRepositoryConnector(RepositorySystemSession session) {
        this.session = session;
    }

    public RecordingRepositoryConnector() {}

    @Override
    public void get(
            Collection<? extends ArtifactDownload> artifactDownloads,
            Collection<? extends MetadataDownload> metadataDownloads) {
        try {
            if (artifactDownloads != null) {
                for (ArtifactDownload download : artifactDownloads) {
                    fireInitiated(download);
                    Artifact artifact = download.getArtifact();
                    this.actualGet.add(artifact);
                    if (fail) {
                        download.setException(new ArtifactTransferException(artifact, null, "forced failure"));
                    } else {
                        TestFileUtils.writeString(download.getFile(), artifact.toString());
                    }
                    fireDone(download);
                }
            }
            if (metadataDownloads != null) {
                for (MetadataDownload download : metadataDownloads) {
                    fireInitiated(download);
                    Metadata metadata = download.getMetadata();
                    this.actualGetMD.add(metadata);
                    if (fail) {
                        download.setException(new MetadataTransferException(metadata, null, "forced failure"));
                    } else {
                        TestFileUtils.writeString(download.getFile(), metadata.toString());
                    }
                    fireDone(download);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void put(
            Collection<? extends ArtifactUpload> artifactUploads,
            Collection<? extends MetadataUpload> metadataUploads) {
        try {
            if (artifactUploads != null) {
                for (ArtifactUpload upload : artifactUploads) {
                    // mimic "real" connector
                    fireInitiated(upload);
                    if (upload.getFile() == null) {
                        upload.setException(new ArtifactTransferException(upload.getArtifact(), null, "no file"));
                    } else if (fail) {
                        upload.setException(
                                new ArtifactTransferException(upload.getArtifact(), null, "forced failure"));
                    }
                    this.actualPut.add(upload.getArtifact());
                    fireDone(upload);
                }
            }
            if (metadataUploads != null) {
                for (MetadataUpload upload : metadataUploads) {
                    // mimic "real" connector
                    fireInitiated(upload);
                    if (upload.getFile() == null) {
                        upload.setException(new MetadataTransferException(upload.getMetadata(), null, "no file"));
                    } else if (fail) {
                        upload.setException(
                                new MetadataTransferException(upload.getMetadata(), null, "forced failure"));
                    }
                    this.actualPutMD.add(upload.getMetadata());
                    fireDone(upload);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void fireInitiated(Transfer transfer) throws Exception {
        TransferListener listener = transfer.getListener();
        if (listener == null) {
            return;
        }
        TransferEvent.Builder event = new TransferEvent.Builder(
                session, new TransferResource(null, null, null, null, null, transfer.getTrace()));
        event.setType(TransferEvent.EventType.INITIATED);
        listener.transferInitiated(event.build());
    }

    private void fireDone(Transfer transfer) {
        TransferListener listener = transfer.getListener();
        if (listener == null) {
            return;
        }
        TransferEvent.Builder event = new TransferEvent.Builder(
                session, new TransferResource(null, null, null, null, null, transfer.getTrace()));
        event.setException(transfer.getException());
        if (transfer.getException() != null) {
            listener.transferFailed(
                    event.setType(TransferEvent.EventType.FAILED).build());
        } else {
            listener.transferSucceeded(
                    event.setType(TransferEvent.EventType.SUCCEEDED).build());
        }
    }

    @Override
    public void close() {}

    public void assertSeenExpected() {
        assertSeenExpected(actualGet, expectGet);
        assertSeenExpected(actualGetMD, expectGetMD);
        assertSeenExpected(actualPut, expectPut);
        assertSeenExpected(actualPutMD, expectPutMD);
    }

    private void assertSeenExpected(List<?> actual, Object[] expected) {
        if (expected == null) {
            expected = new Object[0];
        }

        assertEquals(expected.length, actual.size(), "different number of expected and actual elements:");
        int idx = 0;
        for (Object actualObject : actual) {
            assertEquals(expected[idx++], actualObject, "seen object differs");
        }
    }

    public List<Artifact> getActualArtifactGetRequests() {
        return actualGet;
    }

    public List<Metadata> getActualMetadataGetRequests() {
        return actualGetMD;
    }

    public List<Artifact> getActualArtifactPutRequests() {
        return actualPut;
    }

    public List<Metadata> getActualMetadataPutRequests() {
        return actualPutMD;
    }

    public void setExpectGet(Artifact... expectGet) {
        this.expectGet = expectGet;
    }

    public void setExpectPut(Artifact... expectPut) {
        this.expectPut = expectPut;
    }

    public void setExpectGet(Metadata... expectGetMD) {
        this.expectGetMD = expectGetMD;
    }

    public void setExpectPut(Metadata... expectPutMD) {
        this.expectPutMD = expectPutMD;
    }

    public void resetActual() {
        this.actualGet = new ArrayList<>();
        this.actualGetMD = new ArrayList<>();
        this.actualPut = new ArrayList<>();
        this.actualPutMD = new ArrayList<>();
    }
}
