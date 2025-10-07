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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositoryEvent.EventType;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallResult;
import org.eclipse.aether.installation.InstallationException;
import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.eclipse.aether.internal.test.util.TestLocalRepositoryManager;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.metadata.Metadata.Nature;
import org.eclipse.aether.spi.io.PathProcessorSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DefaultInstallerTest {

    private Artifact artifact;

    private Metadata metadata;

    private DefaultRepositorySystemSession session;

    private String localArtifactPath;

    private String localMetadataPath;

    private DefaultInstaller installer;

    private InstallRequest request;

    private RecordingRepositoryListener listener;

    private File localArtifactFile;

    private TestLocalRepositoryManager lrm;

    @BeforeEach
    void setup() throws IOException {
        artifact = new DefaultArtifact("gid", "aid", "jar", "ver");
        artifact = artifact.setFile(TestFileUtils.createTempFile("artifact".getBytes(), 1));
        metadata = new DefaultMetadata(
                "gid",
                "aid",
                "ver",
                "type",
                Nature.RELEASE_OR_SNAPSHOT,
                TestFileUtils.createTempFile("metadata".getBytes(), 1));

        session = TestUtils.newSession();
        localArtifactPath = session.getLocalRepositoryManager().getPathForLocalArtifact(artifact);
        localMetadataPath = session.getLocalRepositoryManager().getPathForLocalMetadata(metadata);

        localArtifactFile = new File(session.getLocalRepository().getBasedir(), localArtifactPath);

        installer = new DefaultInstaller(
                new PathProcessorSupport(),
                new StubRepositoryEventDispatcher(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                new StubSyncContextFactory());
        request = new InstallRequest();
        listener = new RecordingRepositoryListener();
        session.setRepositoryListener(listener);

        lrm = (TestLocalRepositoryManager) session.getLocalRepositoryManager();

        TestFileUtils.deleteFile(session.getLocalRepository().getBasedir());
    }

    @AfterEach
    void teardown() throws Exception {
        TestFileUtils.deleteFile(session.getLocalRepository().getBasedir());
    }

    @Test
    void testSuccessfulInstall() throws InstallationException, IOException {
        File artifactFile =
                new File(session.getLocalRepositoryManager().getRepository().getBasedir(), localArtifactPath);
        File metadataFile =
                new File(session.getLocalRepositoryManager().getRepository().getBasedir(), localMetadataPath);

        artifactFile.delete();
        metadataFile.delete();

        request.addArtifact(artifact);
        request.addMetadata(metadata);

        InstallResult result = installer.install(session, request);

        assertTrue(artifactFile.exists());
        assertEquals("artifact", TestFileUtils.readString(artifactFile));

        assertTrue(metadataFile.exists());
        assertEquals("metadata", TestFileUtils.readString(metadataFile));

        assertEquals(result.getRequest(), request);

        assertEquals(1, result.getArtifacts().size());
        assertTrue(result.getArtifacts().contains(artifact));

        assertEquals(1, result.getMetadata().size());
        assertTrue(result.getMetadata().contains(metadata));

        assertEquals(1, lrm.getMetadataRegistration().size());
        assertTrue(lrm.getMetadataRegistration().contains(metadata));
        assertEquals(1, lrm.getArtifactRegistration().size());
        assertTrue(lrm.getArtifactRegistration().contains(artifact));
    }

    @Test
    void testNullArtifactFile() {
        InstallRequest request = new InstallRequest();
        request.addArtifact(artifact.setFile(null));

        assertThrows(InstallationException.class, () -> installer.install(session, request));
    }

    @Test
    void testNullMetadataFile() {
        InstallRequest request = new InstallRequest();
        request.addMetadata(metadata.setFile(null));

        assertThrows(InstallationException.class, () -> installer.install(session, request));
    }

    @Test
    void testNonExistentArtifactFile() {
        InstallRequest request = new InstallRequest();
        request.addArtifact(artifact.setFile(new File("missing.txt")));

        assertThrows(InstallationException.class, () -> installer.install(session, request));
    }

    @Test
    void testNonExistentMetadataFile() {
        InstallRequest request = new InstallRequest();
        request.addMetadata(metadata.setFile(new File("missing.xml")));

        assertThrows(InstallationException.class, () -> installer.install(session, request));
    }

    @Test
    void testArtifactExistsAsDir() {
        String path = session.getLocalRepositoryManager().getPathForLocalArtifact(artifact);
        File file = new File(session.getLocalRepository().getBasedir(), path);
        assertFalse(file.isFile(), file.getAbsolutePath() + " is a file, not directory");
        assertFalse(file.exists(), file.getAbsolutePath() + " already exists");
        assertTrue(
                file.mkdirs() || file.isDirectory(),
                "failed to setup test: could not create " + file.getAbsolutePath());

        request.addArtifact(artifact);
        assertThrows(InstallationException.class, () -> installer.install(session, request));
    }

    @Test
    void testMetadataExistsAsDir() {
        String path = session.getLocalRepositoryManager().getPathForLocalMetadata(metadata);
        assertTrue(
                new File(session.getLocalRepository().getBasedir(), path).mkdirs(),
                "failed to setup test: could not create " + path);

        request.addMetadata(metadata);
        assertThrows(InstallationException.class, () -> installer.install(session, request));
    }

    @Test
    void testArtifactDestinationEqualsSource() throws IOException {
        String path = session.getLocalRepositoryManager().getPathForLocalArtifact(artifact);
        File file = new File(session.getLocalRepository().getBasedir(), path);
        artifact = artifact.setFile(file);
        TestFileUtils.writeString(file, "test");

        request.addArtifact(artifact);
        assertThrows(InstallationException.class, () -> installer.install(session, request));
    }

    @Test
    void testMetadataDestinationEqualsSource() throws IOException {
        String path = session.getLocalRepositoryManager().getPathForLocalMetadata(metadata);
        File file = new File(session.getLocalRepository().getBasedir(), path);
        metadata = metadata.setFile(file);
        TestFileUtils.writeString(file, "test");

        request.addMetadata(metadata);
        assertThrows(InstallationException.class, () -> installer.install(session, request));
    }

    @Test
    void testSuccessfulArtifactEvents() throws InstallationException {
        InstallRequest request = new InstallRequest();
        request.addArtifact(artifact);

        installer.install(session, request);
        checkEvents("Repository Event problem", artifact, false);
    }

    @Test
    void testSuccessfulMetadataEvents() throws InstallationException {
        InstallRequest request = new InstallRequest();
        request.addMetadata(metadata);

        installer.install(session, request);
        checkEvents("Repository Event problem", metadata, false);
    }

    @Test
    void testFailingEventsNullArtifactFile() {
        checkFailedEvents("null artifact file", this.artifact.setFile(null));
    }

    @Test
    void testFailingEventsNullMetadataFile() {
        checkFailedEvents("null metadata file", this.metadata.setFile(null));
    }

    @Test
    void testFailingEventsArtifactExistsAsDir() {
        String path = session.getLocalRepositoryManager().getPathForLocalArtifact(artifact);
        assertTrue(
                new File(session.getLocalRepository().getBasedir(), path).mkdirs(),
                "failed to setup test: could not create " + path);
        checkFailedEvents("target exists as dir", artifact);
    }

    @Test
    void testFailingEventsMetadataExistsAsDir() {
        String path = session.getLocalRepositoryManager().getPathForLocalMetadata(metadata);
        assertTrue(
                new File(session.getLocalRepository().getBasedir(), path).mkdirs(),
                "failed to setup test: could not create " + path);
        checkFailedEvents("target exists as dir", metadata);
    }

    private void checkFailedEvents(String msg, Metadata metadata) {
        InstallRequest request = new InstallRequest().addMetadata(metadata);
        msg = "Repository events problem (case: " + msg + ")";

        try {
            installer.install(session, request);
            fail("expected exception");
        } catch (InstallationException e) {
            checkEvents(msg, metadata, true);
        }
    }

    private void checkEvents(String msg, Metadata metadata, boolean failed) {
        List<RepositoryEvent> events = listener.getEvents();
        assertEquals(2, events.size(), msg);
        RepositoryEvent event = events.get(0);
        assertEquals(EventType.METADATA_INSTALLING, event.getType(), msg);
        assertEquals(metadata, event.getMetadata(), msg);
        assertNull(event.getException(), msg);

        event = events.get(1);
        assertEquals(EventType.METADATA_INSTALLED, event.getType(), msg);
        assertEquals(metadata, event.getMetadata(), msg);
        if (failed) {
            assertNotNull(event.getException(), msg);
        } else {
            assertNull(event.getException(), msg);
        }
    }

    private void checkFailedEvents(String msg, Artifact artifact) {
        InstallRequest request = new InstallRequest().addArtifact(artifact);
        msg = "Repository events problem (case: " + msg + ")";

        try {
            installer.install(session, request);
            fail("expected exception");
        } catch (InstallationException e) {
            checkEvents(msg, artifact, true);
        }
    }

    private void checkEvents(String msg, Artifact artifact, boolean failed) {
        List<RepositoryEvent> events = listener.getEvents();
        assertEquals(2, events.size(), msg);
        RepositoryEvent event = events.get(0);
        assertEquals(EventType.ARTIFACT_INSTALLING, event.getType(), msg);
        assertEquals(artifact, event.getArtifact(), msg);
        assertNull(event.getException(), msg);

        event = events.get(1);
        assertEquals(EventType.ARTIFACT_INSTALLED, event.getType(), msg);
        assertEquals(artifact, event.getArtifact(), msg);
        if (failed) {
            assertNotNull(event.getException(), msg + " > expected exception");
        } else {
            assertNull(event.getException(), msg + " > " + event.getException());
        }
    }

    @Test
    @Disabled("Naive change detection is removed (MRESOLVER-392)")
    void testDoNotUpdateUnchangedArtifact() throws InstallationException {
        request.addArtifact(artifact);
        installer.install(session, request);

        installer = new DefaultInstaller(
                new DefaultPathProcessor() {
                    @Override
                    public long copy(Path src, Path target, ProgressListener listener) throws IOException {
                        throw new IOException("copy called");
                    }
                },
                new StubRepositoryEventDispatcher(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                new StubSyncContextFactory());

        request = new InstallRequest();
        request.addArtifact(artifact);
        installer.install(session, request);
    }

    @Test
    void testSetArtifactTimestamps() throws InstallationException {
        artifact.getFile().setLastModified(artifact.getFile().lastModified() - 60000);

        request.addArtifact(artifact);

        installer.install(session, request);

        assertEquals(
                artifact.getFile().lastModified(),
                localArtifactFile.lastModified(),
                "artifact timestamp was not set to src file");

        request = new InstallRequest();

        request.addArtifact(artifact);

        artifact.getFile().setLastModified(artifact.getFile().lastModified() - 60000);

        installer.install(session, request);

        assertEquals(
                artifact.getFile().lastModified(),
                localArtifactFile.lastModified(),
                "artifact timestamp was not set to src file");
    }
}
