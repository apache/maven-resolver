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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositoryEvent.EventType;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallResult;
import org.eclipse.aether.installation.InstallationException;
import org.eclipse.aether.internal.test.util.TestFileProcessor;
import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.eclipse.aether.internal.test.util.TestLocalRepositoryManager;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.metadata.Metadata.Nature;
import org.eclipse.aether.transform.FileTransformer;
import org.eclipse.aether.util.artifact.SubArtifact;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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
    public void setup() throws IOException {
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

        installer = new DefaultInstaller();
        installer.setFileProcessor(new TestFileProcessor());
        installer.setRepositoryEventDispatcher(new StubRepositoryEventDispatcher());
        installer.setSyncContextFactory(new StubSyncContextFactory());
        request = new InstallRequest();
        listener = new RecordingRepositoryListener();
        session.setRepositoryListener(listener);

        lrm = (TestLocalRepositoryManager) session.getLocalRepositoryManager();

        TestFileUtils.deleteFile(session.getLocalRepository().getBasedir());
    }

    @AfterEach
    public void teardown() throws Exception {
        TestFileUtils.deleteFile(session.getLocalRepository().getBasedir());
    }

    @Test
    public void testSuccessfulInstall() throws InstallationException, IOException {
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

        assertEquals(result.getArtifacts().size(), 1);
        assertTrue(result.getArtifacts().contains(artifact));

        assertEquals(result.getMetadata().size(), 1);
        assertTrue(result.getMetadata().contains(metadata));

        assertEquals(1, lrm.getMetadataRegistration().size());
        assertTrue(lrm.getMetadataRegistration().contains(metadata));
        assertEquals(1, lrm.getArtifactRegistration().size());
        assertTrue(lrm.getArtifactRegistration().contains(artifact));
    }

    @Test
    public void testNullArtifactFile() {
        assertThrows(InstallationException.class, () -> {
            InstallRequest request = new InstallRequest();
            request.addArtifact(artifact.setFile(null));

            installer.install(session, request);
        });
    }

    @Test
    public void testNullMetadataFile() {
        assertThrows(InstallationException.class, () -> {
            InstallRequest request = new InstallRequest();
            request.addMetadata(metadata.setFile(null));

            installer.install(session, request);
        });
    }

    @Test
    public void testNonExistentArtifactFile() {
        assertThrows(InstallationException.class, () -> {
            InstallRequest request = new InstallRequest();
            request.addArtifact(artifact.setFile(new File("missing.txt")));

            installer.install(session, request);
        });
    }

    @Test
    public void testNonExistentMetadataFile() {
        assertThrows(InstallationException.class, () -> {
            InstallRequest request = new InstallRequest();
            request.addMetadata(metadata.setFile(new File("missing.xml")));

            installer.install(session, request);
        });
    }

    @Test
    public void testArtifactExistsAsDir() {
        assertThrows(InstallationException.class, () -> {
            String path = session.getLocalRepositoryManager().getPathForLocalArtifact(artifact);
            File file = new File(session.getLocalRepository().getBasedir(), path);
            assertFalse(file.isFile(), file.getAbsolutePath() + " is a file, not directory");
            assertFalse(file.exists(), file.getAbsolutePath() + " already exists");
            assertTrue(
                    file.mkdirs() || file.isDirectory(),
                    "failed to setup test: could not create " + file.getAbsolutePath());

            request.addArtifact(artifact);
            installer.install(session, request);
        });
    }

    @Test
    public void testMetadataExistsAsDir() {
        assertThrows(InstallationException.class, () -> {
            String path = session.getLocalRepositoryManager().getPathForLocalMetadata(metadata);
            assertTrue(
                    new File(session.getLocalRepository().getBasedir(), path).mkdirs(),
                    "failed to setup test: could not create " + path);

            request.addMetadata(metadata);
            installer.install(session, request);
        });
    }

    @Test
    public void testArtifactDestinationEqualsSource() throws Exception {
        assertThrows(InstallationException.class, () -> {
            String path = session.getLocalRepositoryManager().getPathForLocalArtifact(artifact);
            File file = new File(session.getLocalRepository().getBasedir(), path);
            artifact = artifact.setFile(file);
            TestFileUtils.writeString(file, "test");

            request.addArtifact(artifact);
            installer.install(session, request);
        });
    }

    @Test
    public void testMetadataDestinationEqualsSource() throws Exception {
        assertThrows(InstallationException.class, () -> {
            String path = session.getLocalRepositoryManager().getPathForLocalMetadata(metadata);
            File file = new File(session.getLocalRepository().getBasedir(), path);
            metadata = metadata.setFile(file);
            TestFileUtils.writeString(file, "test");

            request.addMetadata(metadata);
            installer.install(session, request);
        });
    }

    @Test
    public void testSuccessfulArtifactEvents() throws InstallationException {
        InstallRequest request = new InstallRequest();
        request.addArtifact(artifact);

        installer.install(session, request);
        checkEvents("Repository Event problem", artifact, false);
    }

    @Test
    public void testSuccessfulMetadataEvents() throws InstallationException {
        InstallRequest request = new InstallRequest();
        request.addMetadata(metadata);

        installer.install(session, request);
        checkEvents("Repository Event problem", metadata, false);
    }

    @Test
    public void testFailingEventsNullArtifactFile() {
        checkFailedEvents("null artifact file", this.artifact.setFile(null));
    }

    @Test
    public void testFailingEventsNullMetadataFile() {
        checkFailedEvents("null metadata file", this.metadata.setFile(null));
    }

    @Test
    public void testFailingEventsArtifactExistsAsDir() {
        String path = session.getLocalRepositoryManager().getPathForLocalArtifact(artifact);
        assertTrue(
                new File(session.getLocalRepository().getBasedir(), path).mkdirs(),
                "failed to setup test: could not create " + path);
        checkFailedEvents("target exists as dir", artifact);
    }

    @Test
    public void testFailingEventsMetadataExistsAsDir() {
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
    public void testDoNotUpdateUnchangedArtifact() throws InstallationException {
        request.addArtifact(artifact);
        installer.install(session, request);

        installer.setFileProcessor(new DefaultFileProcessor() {
            @Override
            public long copy(File src, File target, ProgressListener listener) throws IOException {
                throw new IOException("copy called");
            }
        });

        request = new InstallRequest();
        request.addArtifact(artifact);
        installer.install(session, request);
    }

    @Test
    public void testSetArtifactTimestamps() throws InstallationException {
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

    @Test
    public void testFileTransformer() throws Exception {
        final Artifact transformedArtifact = new SubArtifact(artifact, null, "raj");
        FileTransformer transformer = new FileTransformer() {
            @Override
            public InputStream transformData(File file) {
                return new ByteArrayInputStream("transformed data".getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public Artifact transformArtifact(Artifact artifact) {
                return transformedArtifact;
            }
        };

        StubFileTransformerManager fileTransformerManager = new StubFileTransformerManager();
        fileTransformerManager.addFileTransformer("jar", transformer);
        session.setFileTransformerManager(fileTransformerManager);

        request = new InstallRequest();
        request.addArtifact(artifact);
        installer.install(session, request);

        assertFalse(localArtifactFile.exists());

        String transformedArtifactPath =
                session.getLocalRepositoryManager().getPathForLocalArtifact(transformedArtifact);
        File transformedArtifactFile = new File(session.getLocalRepository().getBasedir(), transformedArtifactPath);
        assertTrue(transformedArtifactFile.exists());

        try (BufferedReader r = new BufferedReader(new FileReader(transformedArtifactFile))) {
            assertEquals("transformed data", r.readLine());
        }
    }
}
