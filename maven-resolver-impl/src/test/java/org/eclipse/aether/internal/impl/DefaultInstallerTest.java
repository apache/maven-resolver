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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import org.eclipse.aether.ConfigurationProperties;
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
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

public class DefaultInstallerTest {

    @TempDir
    private Path tempDir;

    private Artifact artifact;

    private Metadata metadata;

    private DefaultRepositorySystemSession session;

    private DefaultInstaller installer;

    private InstallRequest request;

    private RecordingRepositoryListener listener;

    private File localArtifactFile;

    private TestLocalRepositoryManager lrm;

    private Path createTempFile(String contents) throws IOException {
        Path file = Files.createTempFile(tempDir, "tmpfile-", ".data");
        Files.write(file, contents.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private static void writeString(File file, String contents) throws IOException {
        Files.createDirectories(file.toPath().getParent());
        Files.write(file.toPath(), contents.getBytes(StandardCharsets.UTF_8));
    }

    @BeforeEach
    void setup() throws IOException {
        artifact = new DefaultArtifact("gid", "aid", "jar", "ver");
        artifact = artifact.setPath(createTempFile("artifact"));
        metadata = new DefaultMetadata(
                "gid", "aid", "ver", "type", Nature.RELEASE_OR_SNAPSHOT, createTempFile("metadata"));

        session = TestUtils.newSession();
        localArtifactFile = session.getLocalRepositoryManager()
                .getAbsolutePathForLocalArtifact(artifact)
                .toFile();

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

        TestFileUtils.deleteFile(session.getLocalRepository().getBasePath().toFile());
    }

    @AfterEach
    void teardown() throws Exception {
        TestFileUtils.deleteFile(session.getLocalRepository().getBasePath().toFile());
    }

    @Test
    void testSuccessfulInstall() throws InstallationException, IOException {
        File artifactFile = session.getLocalRepositoryManager()
                .getAbsolutePathForLocalArtifact(artifact)
                .toFile();
        File metadataFile = session.getLocalRepositoryManager()
                .getAbsolutePathForLocalMetadata(metadata)
                .toFile();

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
        request.addArtifact(artifact.setPath(null));

        assertThrows(InstallationException.class, () -> installer.install(session, request));
    }

    @Test
    void testNullMetadataFile() {
        InstallRequest request = new InstallRequest();
        request.addMetadata(metadata.setPath(null));

        assertThrows(InstallationException.class, () -> installer.install(session, request));
    }

    @Test
    void testNonExistentArtifactFile() {
        InstallRequest request = new InstallRequest();
        request.addArtifact(artifact.setPath(Paths.get("missing.txt")));

        assertThrows(InstallationException.class, () -> installer.install(session, request));
    }

    @Test
    void testNonExistentMetadataFile() {
        InstallRequest request = new InstallRequest();
        request.addMetadata(metadata.setPath(Paths.get("missing.xml")));

        assertThrows(InstallationException.class, () -> installer.install(session, request));
    }

    @Test
    void testArtifactExistsAsDir() {
        File file = session.getLocalRepositoryManager()
                .getAbsolutePathForLocalArtifact(artifact)
                .toFile();
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
        Path path = session.getLocalRepositoryManager().getAbsolutePathForLocalMetadata(metadata);
        assertTrue(path.toFile().mkdirs(), "failed to setup test: could not create " + path);

        request.addMetadata(metadata);
        assertThrows(InstallationException.class, () -> installer.install(session, request));
    }

    @Test
    void testArtifactDestinationEqualsSource() throws IOException {
        File file = session.getLocalRepositoryManager()
                .getAbsolutePathForLocalArtifact(artifact)
                .toFile();
        artifact = artifact.setPath(file.toPath());
        writeString(file, "test");

        request.addArtifact(artifact);
        assertThrows(InstallationException.class, () -> installer.install(session, request));
    }

    @Test
    void testMetadataDestinationEqualsSource() throws IOException {
        File file = session.getLocalRepositoryManager()
                .getAbsolutePathForLocalMetadata(metadata)
                .toFile();
        metadata = metadata.setPath(file.toPath());
        writeString(file, "test");

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
        checkFailedEvents("null artifact file", this.artifact.setPath(null));
    }

    @Test
    void testFailingEventsNullMetadataFile() {
        checkFailedEvents("null metadata file", this.metadata.setPath(null));
    }

    @Test
    void testFailingEventsArtifactExistsAsDir() {
        Path path = session.getLocalRepositoryManager().getAbsolutePathForLocalArtifact(artifact);
        assertTrue(path.toFile().mkdirs(), "failed to setup test: could not create " + path);
        checkFailedEvents("target exists as dir", artifact);
    }

    @Test
    void testFailingEventsMetadataExistsAsDir() {
        Path path = session.getLocalRepositoryManager().getAbsolutePathForLocalMetadata(metadata);
        assertTrue(path.toFile().mkdirs(), "failed to setup test: could not create " + path);
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
        artifact.getPath().toFile().setLastModified(artifact.getPath().toFile().lastModified() - 60000);

        request.addArtifact(artifact);

        installer.install(session, request);

        assertEquals(
                artifact.getPath().toFile().lastModified(),
                localArtifactFile.lastModified(),
                "artifact timestamp was not set to src file");

        request = new InstallRequest();

        request.addArtifact(artifact);

        artifact.getPath().toFile().setLastModified(artifact.getPath().toFile().lastModified() - 60000);

        installer.install(session, request);

        assertEquals(
                artifact.getPath().toFile().lastModified(),
                localArtifactFile.lastModified(),
                "artifact timestamp was not set to src file");
    }

    @Test
    void testHardLinkInstallCreatesHardLink() throws InstallationException, IOException {
        // default: hardLink=true — installer attempts a hard link, falls back to copy on cross-device
        //
        // Use a counting PathProcessor to detect whether the fallback copy path was taken.
        // On same-device filesystems: hard link is created (copyWithTimestamp NOT called).
        // On cross-device (common in CI containers): Files.createLink throws, copy IS taken.
        CountingPathProcessor countingProcessor = new CountingPathProcessor();
        installer = new DefaultInstaller(
                countingProcessor,
                new StubRepositoryEventDispatcher(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                new StubSyncContextFactory());

        request.addArtifact(artifact);
        installer.install(session, request);

        assertTrue(localArtifactFile.exists());
        assertEquals("artifact", TestFileUtils.readString(localArtifactFile));

        // On same-device filesystems, verify a hard link was created (same inode, no copy).
        // On cross-device (common in CI containers where /tmp and project dir are on different mounts),
        // Files.createLink throws IOException and the installer falls back to copy — both are correct.
        Path srcPath = artifact.getPath();
        Path dstPath = localArtifactFile.toPath();
        try {
            boolean sameDevice =
                    Files.getAttribute(srcPath, "unix:dev").equals(Files.getAttribute(dstPath, "unix:dev"));
            if (sameDevice) {
                // hard link must have been created — same inode expected, copy NOT taken
                assertEquals(
                        Files.getAttribute(srcPath, "unix:ino"),
                        Files.getAttribute(dstPath, "unix:ino"),
                        "installed artifact should be a hard link to the source on same-device FS");
                assertEquals(
                        0,
                        countingProcessor.copyWithTimestampCount,
                        "copyWithTimestamp must NOT be called on same-device FS");
            } else {
                // cross-device: fallback to copy must have been taken
                assertEquals(
                        1,
                        countingProcessor.copyWithTimestampCount,
                        "copyWithTimestamp must be called as fallback on cross-device FS");
            }
        } catch (UnsupportedOperationException | IllegalArgumentException ignored) {
            // non-POSIX filesystem — inode/device check not available
            // but we can still assert that copyWithTimestamp was called at most once:
            // either the hard link succeeded (count=0) or the fallback fired exactly once (count=1)
            assertTrue(
                    countingProcessor.copyWithTimestampCount <= 1,
                    "copyWithTimestamp must be called at most once on non-POSIX FS (got "
                            + countingProcessor.copyWithTimestampCount + ")");
        }
    }

    @Test
    void testHardLinkDisabledFallsThroughToCopy() throws InstallationException, IOException {
        // set hardLink=false — installer must copy, not hard-link
        CountingPathProcessor countingProcessor = new CountingPathProcessor();
        installer = new DefaultInstaller(
                countingProcessor,
                new StubRepositoryEventDispatcher(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                new StubSyncContextFactory());

        session.setConfigProperty(ConfigurationProperties.INSTALLER_HARD_LINK, false);
        request.addArtifact(artifact);
        installer.install(session, request);

        assertTrue(localArtifactFile.exists());
        assertEquals("artifact", TestFileUtils.readString(localArtifactFile));
        // Verify copyWithTimestamp was called (hardLink=false must NOT attempt Files.createLink)
        assertEquals(
                1, countingProcessor.copyWithTimestampCount, "copyWithTimestamp must be called when hardLink=false");
    }

    /** PathProcessor wrapper that counts copyWithTimestamp invocations. */
    private static class CountingPathProcessor extends PathProcessorSupport {
        int copyWithTimestampCount = 0;

        @Override
        public void copyWithTimestamp(Path src, Path dst) throws IOException {
            copyWithTimestampCount++;
            super.copyWithTimestamp(src, dst);
        }
    }
}
