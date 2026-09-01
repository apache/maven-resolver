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
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.metadata.Metadata.Nature;
import org.eclipse.aether.repository.LocalArtifactRegistration;
import org.eclipse.aether.repository.LocalArtifactRequest;
import org.eclipse.aether.repository.LocalArtifactResult;
import org.eclipse.aether.repository.LocalMetadataRequest;
import org.eclipse.aether.repository.LocalMetadataResult;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.repository.RepositoryIdHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class EnhancedLocalRepositoryManagerTest {

    private Artifact artifact;

    private Artifact snapshot;

    @TempDir
    protected File basedir;

    protected EnhancedLocalRepositoryManager manager;

    private File artifactFile;

    private RemoteRepository repository;

    private final String testContext = "project/compile";

    protected TrackingFileManager trackingFileManager;

    protected DefaultRepositorySystemSession session;

    private Metadata metadata;

    private Metadata noVerMetadata;

    @BeforeEach
    void setup(@TempDir File dir) throws Exception {
        String url = dir.toURI().toURL().toString();
        repository = new RemoteRepository.Builder("enhanced-remote-repo", "default", url)
                .setRepositoryManager(true)
                .build();

        artifact = new DefaultArtifact(
                "gid", "aid", "", "jar", "1-test", Collections.emptyMap(), TestFileUtils.createTempFile("artifact"));

        snapshot = new DefaultArtifact(
                "gid",
                "aid",
                "",
                "jar",
                "1.0-20120710.231549-9",
                Collections.emptyMap(),
                TestFileUtils.createTempFile("artifact"));

        metadata = new DefaultMetadata(
                "gid", "aid", "1-test", "maven-metadata.xml", Nature.RELEASE, TestFileUtils.createTempFile("metadata"));

        noVerMetadata = new DefaultMetadata(
                "gid", "aid", null, "maven-metadata.xml", Nature.RELEASE, TestFileUtils.createTempFile("metadata"));

        session = TestUtils.newSession();
        trackingFileManager = new TrackingFileManagerSupplier().get();
        manager = getManager();

        artifactFile = new File(basedir, manager.getPathForLocalArtifact(artifact));
    }

    protected EnhancedLocalRepositoryManager getManager() throws IOException {
        return new EnhancedLocalRepositoryManager(
                basedir.toPath(),
                new DefaultLocalPathComposer(),
                RepositoryIdHelper::simpleRepositoryKey,
                "_remote.repositories",
                trackingFileManager,
                new DefaultLocalPathPrefixComposerFactory(new DefaultRepositoryKeyFunctionFactory())
                        .createComposer(session));
    }

    @AfterEach
    void tearDown() throws Exception {
        TestFileUtils.deleteFile(basedir);
        TestFileUtils.deleteFile(new File(new URI(repository.getUrl())));

        session = null;
        manager = null;
        repository = null;
        artifact = null;
    }

    private long addLocalArtifact(Artifact artifact) throws IOException {
        manager.add(session, new LocalArtifactRegistration(artifact));
        String path = manager.getPathForLocalArtifact(artifact);

        return copy(artifact, path);
    }

    private long addRemoteArtifact(Artifact artifact) throws IOException {
        Collection<String> contexts = Arrays.asList(testContext);
        manager.add(session, new LocalArtifactRegistration(artifact, repository, contexts));
        String path = manager.getPathForRemoteArtifact(artifact, repository, testContext);
        return copy(artifact, path);
    }

    private long copy(Metadata metadata, String path) throws IOException {
        if (metadata.getFile() == null) {
            return -1L;
        }
        return TestFileUtils.copyFile(metadata.getFile(), new File(basedir, path));
    }

    private long copy(Artifact artifact, String path) throws IOException {
        if (artifact.getFile() == null) {
            return -1L;
        }
        File artifactFile = new File(basedir, path);
        return TestFileUtils.copyFile(artifact.getFile(), artifactFile);
    }

    @Test
    void testGetPathForLocalArtifact() {
        Artifact artifact = new DefaultArtifact("g.i.d:a.i.d:1.0-SNAPSHOT");
        assertEquals("1.0-SNAPSHOT", artifact.getBaseVersion());
        assertEquals("g/i/d/a.i.d/1.0-SNAPSHOT/a.i.d-1.0-SNAPSHOT.jar", manager.getPathForLocalArtifact(artifact));

        artifact = new DefaultArtifact("g.i.d:a.i.d:1.0-20110329.221805-4");
        assertEquals("1.0-SNAPSHOT", artifact.getBaseVersion());
        assertEquals("g/i/d/a.i.d/1.0-SNAPSHOT/a.i.d-1.0-SNAPSHOT.jar", manager.getPathForLocalArtifact(artifact));
    }

    @Test
    void testGetPathForRemoteArtifact() {
        RemoteRepository remoteRepo = new RemoteRepository.Builder("repo", "default", "ram:/void").build();

        Artifact artifact = new DefaultArtifact("g.i.d:a.i.d:1.0-SNAPSHOT");
        assertEquals("1.0-SNAPSHOT", artifact.getBaseVersion());
        assertEquals(
                "g/i/d/a.i.d/1.0-SNAPSHOT/a.i.d-1.0-SNAPSHOT.jar",
                manager.getPathForRemoteArtifact(artifact, remoteRepo, ""));

        artifact = new DefaultArtifact("g.i.d:a.i.d:1.0-20110329.221805-4");
        assertEquals("1.0-SNAPSHOT", artifact.getBaseVersion());
        assertEquals(
                "g/i/d/a.i.d/1.0-SNAPSHOT/a.i.d-1.0-20110329.221805-4.jar",
                manager.getPathForRemoteArtifact(artifact, remoteRepo, ""));
    }

    @Test
    void testFindLocalArtifact() throws Exception {
        addLocalArtifact(artifact);

        LocalArtifactRequest request = new LocalArtifactRequest(artifact, null, null);
        LocalArtifactResult result = manager.find(session, request);
        assertTrue(result.isAvailable());
        assertNull(result.getRepository());

        snapshot = snapshot.setVersion(snapshot.getBaseVersion());
        addLocalArtifact(snapshot);

        request = new LocalArtifactRequest(snapshot, null, null);
        result = manager.find(session, request);
        assertTrue(result.isAvailable());
        assertNull(result.getRepository());
    }

    @Test
    void testFindRemoteArtifact() throws Exception {
        addRemoteArtifact(artifact);

        LocalArtifactRequest request = new LocalArtifactRequest(artifact, Arrays.asList(repository), testContext);
        LocalArtifactResult result = manager.find(session, request);
        assertTrue(result.isAvailable());
        assertEquals(repository, result.getRepository());

        addRemoteArtifact(snapshot);

        request = new LocalArtifactRequest(snapshot, Arrays.asList(repository), testContext);
        result = manager.find(session, request);
        assertTrue(result.isAvailable());
        assertEquals(repository, result.getRepository());
    }

    @Test
    void testDoNotFindDifferentContext() throws Exception {
        addRemoteArtifact(artifact);

        LocalArtifactRequest request = new LocalArtifactRequest(artifact, Arrays.asList(repository), "different");
        LocalArtifactResult result = manager.find(session, request);
        assertFalse(result.isAvailable());
    }

    @Test
    void testDoNotFindNullFile() throws Exception {
        artifact = artifact.setFile(null);
        addLocalArtifact(artifact);

        LocalArtifactRequest request = new LocalArtifactRequest(artifact, Arrays.asList(repository), testContext);
        LocalArtifactResult result = manager.find(session, request);
        assertFalse(result.isAvailable());
    }

    @Test
    void testDoNotFindDeletedFile() throws Exception {
        addLocalArtifact(artifact);
        assertTrue(artifactFile.delete(), "could not delete artifact file");

        LocalArtifactRequest request = new LocalArtifactRequest(artifact, Arrays.asList(repository), testContext);
        LocalArtifactResult result = manager.find(session, request);
        assertFalse(result.isAvailable());
    }

    /**
     * Simulates a case-aliasing filesystem (the macOS and Windows defaults) on any filesystem by creating a
     * symbolic link whose name differs from the on-disk file only by case, and returns the aliased artifact.
     */
    private Artifact createCaseAliasedArtifact() throws Exception {
        addLocalArtifact(artifact);

        Artifact aliased = new DefaultArtifact("gid", "aid", "", "JAR", "1-test");
        Path aliasPath = new File(basedir, manager.getPathForLocalArtifact(aliased)).toPath();
        Path realPath = new File(basedir, manager.getPathForLocalArtifact(artifact)).toPath();
        try {
            Files.createSymbolicLink(aliasPath, realPath.getFileName());
        } catch (IOException | UnsupportedOperationException e) {
            assumeTrue(false, "filesystem does not support symbolic links");
        }
        return aliased;
    }

    @Test
    void testFindDoesNotAcceptFileWhoseRealPathSpellingDiffers() throws Exception {
        Artifact aliased = createCaseAliasedArtifact();

        // the aliased file is present-but-untracked for the requested coordinates: it must not be accepted,
        // otherwise case-colliding coordinates poison distinct GAVs on case-insensitive filesystems
        LocalArtifactRequest request = new LocalArtifactRequest(aliased, null, null);
        LocalArtifactResult result = manager.find(session, request);
        assertFalse(result.isAvailable());
    }

    @Test
    void testFindAcceptsAliasedFileWhenRealPathVerificationDisabled() throws Exception {
        session.setConfigProperty(EnhancedLocalRepositoryManagerFactory.CONFIG_PROP_VERIFY_REAL_PATH, false);
        Artifact aliased = createCaseAliasedArtifact();

        LocalArtifactRequest request = new LocalArtifactRequest(aliased, null, null);
        LocalArtifactResult result = manager.find(session, request);
        assertTrue(result.isAvailable());
    }

    @Test
    void testFindUntrackedFile() throws Exception {
        copy(artifact, manager.getPathForLocalArtifact(artifact));

        LocalArtifactRequest request = new LocalArtifactRequest(artifact, Arrays.asList(repository), testContext);
        LocalArtifactResult result = manager.find(session, request);
        assertTrue(result.isAvailable());
    }

    private long addMetadata(Metadata metadata, RemoteRepository repo) throws IOException {
        String path;
        if (repo == null) {
            path = manager.getPathForLocalMetadata(metadata);
        } else {
            path = manager.getPathForRemoteMetadata(metadata, repo, testContext);
        }
        System.err.println(path);

        return copy(metadata, path);
    }

    @Test
    void testFindLocalMetadata() throws Exception {
        addMetadata(metadata, null);

        LocalMetadataRequest request = new LocalMetadataRequest(metadata, null, testContext);
        LocalMetadataResult result = manager.find(session, request);

        assertNotNull(result.getFile());
    }

    @Test
    void testFindLocalMetadataNoVersion() throws Exception {
        addMetadata(noVerMetadata, null);

        LocalMetadataRequest request = new LocalMetadataRequest(noVerMetadata, null, testContext);
        LocalMetadataResult result = manager.find(session, request);

        assertNotNull(result.getFile());
    }

    @Test
    void testDoNotFindRemoteMetadataDifferentContext() throws Exception {
        addMetadata(noVerMetadata, repository);
        addMetadata(metadata, repository);

        LocalMetadataRequest request = new LocalMetadataRequest(noVerMetadata, repository, "different");
        LocalMetadataResult result = manager.find(session, request);
        assertNull(result.getFile());

        request = new LocalMetadataRequest(metadata, repository, "different");
        result = manager.find(session, request);
        assertNull(result.getFile());
    }

    @Test
    void testFindArtifactUsesTimestampedVersion() throws Exception {
        Artifact artifact = new DefaultArtifact("g.i.d:a.i.d:1.0-SNAPSHOT");
        File file = new File(basedir, manager.getPathForLocalArtifact(artifact));
        TestFileUtils.writeString(file, "test");
        addLocalArtifact(artifact);

        artifact = artifact.setVersion("1.0-20110329.221805-4");
        LocalArtifactRequest request = new LocalArtifactRequest();
        request.setArtifact(artifact);
        LocalArtifactResult result = manager.find(session, request);
        assertNull(result.getFile(), result.toString());
        assertFalse(result.isAvailable(), result.toString());
    }

    @Test
    void testStaleUntrackedCacheDoesNotOverrideExternallyRecordedOrigin() throws Exception {
        // Artifact file exists but is untracked: find() accepts it (simple local repo inter-op) and
        // caches the "untracked" state of the shared tracking file.
        copy(artifact, manager.getPathForLocalArtifact(artifact));
        LocalArtifactRequest request = new LocalArtifactRequest(artifact, Arrays.asList(repository), testContext);
        assertTrue(manager.find(session, request).isAvailable());

        // Another process (bypassing this manager and hence its cache) records that the artifact
        // actually originates from a repository that is NOT among the request repositories.
        File artifactFile = new File(basedir, manager.getPathForLocalArtifact(artifact));
        File trackingFile = new File(artifactFile.getParentFile(), "_remote.repositories");
        trackingFileManager.update(
                trackingFile.toPath(), Collections.singletonMap(artifactFile.getName() + ">not-a-request-repo", ""));

        // The trust-increasing untracked branch must not rely on the stale cached state: the fresh
        // on-disk state says "downloaded from a foreign repository", so the artifact is rejected.
        LocalArtifactResult result = manager.find(session, request);
        assertFalse(result.isAvailable());
    }

    @Test
    void testFreshReadPicksUpExternallyRecordedRequestRepository() throws Exception {
        copy(artifact, manager.getPathForLocalArtifact(artifact));
        LocalArtifactRequest request = new LocalArtifactRequest(artifact, Arrays.asList(repository), testContext);
        assertTrue(manager.find(session, request).isAvailable());

        // Another process records the artifact as downloaded from the request repository: the fresh
        // re-read must pick that up and report the origin repository.
        File artifactFile = new File(basedir, manager.getPathForLocalArtifact(artifact));
        File trackingFile = new File(artifactFile.getParentFile(), "_remote.repositories");
        trackingFileManager.update(
                trackingFile.toPath(),
                Collections.singletonMap(
                        artifactFile.getName() + ">" + RepositoryIdHelper.simpleRepositoryKey(repository, testContext),
                        ""));

        LocalArtifactResult result = manager.find(session, request);
        assertTrue(result.isAvailable());
        assertEquals(repository, result.getRepository());
    }

    /**
     * Manager wired like production defaults: path composition on the (unchanged) system-wide key function,
     * tracking entries on the URL-qualified {@code nid_hurl} function.
     */
    private EnhancedLocalRepositoryManager newUrlQualifiedTrackingManager() throws IOException {
        return new EnhancedLocalRepositoryManager(
                basedir.toPath(),
                new DefaultLocalPathComposer(),
                RepositoryIdHelper.getRepositoryKeyFunction("nid_hurl"),
                "_remote.repositories",
                trackingFileManager,
                new DefaultLocalPathPrefixComposerFactory(new DefaultRepositoryKeyFunctionFactory())
                        .createComposer(session));
    }

    @Test
    void testUrlQualifiedTrackingDistinguishesSameIdDifferentUrl() throws Exception {
        manager = newUrlQualifiedTrackingManager();
        addRemoteArtifact(artifact);

        // the repository the artifact really came from still satisfies the lookup
        LocalArtifactRequest request = new LocalArtifactRequest(artifact, Arrays.asList(repository), testContext);
        LocalArtifactResult result = manager.find(session, request);
        assertTrue(result.isAvailable());
        assertEquals(repository, result.getRepository());

        // an impostor sharing the trusted id but pointing at a different URL is a different origin:
        // the cached bytes must not be accepted as "came from" it (and vice versa, bytes cached from
        // the impostor would not satisfy the trusted repository)
        RemoteRepository impostor =
                new RemoteRepository.Builder(repository.getId(), "default", "https://impostor.invalid/repo").build();
        request = new LocalArtifactRequest(artifact, Arrays.asList(impostor), testContext);
        result = manager.find(session, request);
        assertFalse(result.isAvailable());
    }

    @Test
    void testUrlQualifiedTrackingTreatsLegacyIdOnlyEntriesAsStale() throws Exception {
        manager = newUrlQualifiedTrackingManager();

        // artifact file present, tracked under a legacy ID-only key as written by an older resolver
        copy(artifact, manager.getPathForLocalArtifact(artifact));
        File file = new File(basedir, manager.getPathForLocalArtifact(artifact));
        trackingFileManager.update(
                new File(file.getParentFile(), "_remote.repositories").toPath(),
                Collections.singletonMap(
                        file.getName() + ">" + RepositoryIdHelper.simpleRepositoryKey(repository, testContext), ""));

        // fail-safe stale-key semantics: the legacy key matches no request repository, and because the
        // file IS tracked it must not fall through to the untracked inter-op acceptance either — the
        // artifact is simply unavailable locally and gets re-fetched (with checksum validation)
        LocalArtifactRequest request = new LocalArtifactRequest(artifact, Arrays.asList(repository), testContext);
        assertFalse(manager.find(session, request).isAvailable());
    }
}
