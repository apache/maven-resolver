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
import java.net.URI;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.impl.UpdateCheck;
import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.transfer.ArtifactNotFoundException;
import org.eclipse.aether.transfer.ArtifactTransferException;
import org.eclipse.aether.transfer.MetadataNotFoundException;
import org.eclipse.aether.transfer.MetadataTransferException;
import org.eclipse.aether.util.repository.SimpleResolutionErrorPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 */
public class DefaultUpdateCheckManagerTest {

    private static final long HOUR = 60L * 60L * 1000L;

    private DefaultUpdateCheckManager manager;

    private DefaultRepositorySystemSession session;

    private Metadata metadata;

    private RemoteRepository repository;

    private Artifact artifact;

    @BeforeEach
    public void setup() throws Exception {
        File dir = TestFileUtils.createTempFile("");
        TestFileUtils.deleteFile(dir);

        File metadataFile = new File(dir, "metadata.txt");
        TestFileUtils.writeString(metadataFile, "metadata");
        File artifactFile = new File(dir, "artifact.txt");
        TestFileUtils.writeString(artifactFile, "artifact");

        session = TestUtils.newSession();
        repository = new RemoteRepository.Builder(
                        "id",
                        "default",
                        TestFileUtils.createTempDir().toURI().toURL().toString())
                .build();
        manager = new DefaultUpdateCheckManager(new DefaultTrackingFileManager(), new DefaultUpdatePolicyAnalyzer());
        metadata = new DefaultMetadata(
                "gid", "aid", "ver", "maven-metadata.xml", Metadata.Nature.RELEASE_OR_SNAPSHOT, metadataFile);
        artifact = new DefaultArtifact("gid", "aid", "", "ext", "ver").setFile(artifactFile);
    }

    @AfterEach
    public void teardown() throws Exception {
        new File(metadata.getFile().getParent(), "resolver-status.properties").delete();
        new File(artifact.getFile().getPath() + ".lastUpdated").delete();
        metadata.getFile().delete();
        artifact.getFile().delete();
        TestFileUtils.deleteFile(new File(new URI(repository.getUrl())));
    }

    static void resetSessionData(RepositorySystemSession session) {
        session.getData().set(DefaultUpdateCheckManager.SESSION_CHECKS, null);
    }

    private UpdateCheck<Metadata, MetadataTransferException> newMetadataCheck() {
        UpdateCheck<Metadata, MetadataTransferException> check = new UpdateCheck<>();
        check.setItem(metadata);
        check.setFile(metadata.getFile());
        check.setRepository(repository);
        check.setAuthoritativeRepository(repository);
        check.setArtifactPolicy(RepositoryPolicy.UPDATE_POLICY_INTERVAL + ":10");
        check.setMetadataPolicy(RepositoryPolicy.UPDATE_POLICY_INTERVAL + ":10");
        return check;
    }

    private UpdateCheck<Artifact, ArtifactTransferException> newArtifactCheck() {
        UpdateCheck<Artifact, ArtifactTransferException> check = new UpdateCheck<>();
        check.setItem(artifact);
        check.setFile(artifact.getFile());
        check.setRepository(repository);
        check.setArtifactPolicy(RepositoryPolicy.UPDATE_POLICY_INTERVAL + ":10");
        return check;
    }

    @Test(expected = Exception.class)
    public void testCheckMetadataFailOnNoFile() {
        UpdateCheck<Metadata, MetadataTransferException> check = newMetadataCheck();
        check.setItem(metadata.setFile(null));
        check.setFile(null);

        manager.checkMetadata(session, check);
    }

    @Test
    public void testCheckMetadataUpdatePolicyRequired() {
        UpdateCheck<Metadata, MetadataTransferException> check = newMetadataCheck();

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -1);
        check.setLocalLastUpdated(cal.getTimeInMillis());

        check.setArtifactPolicy(RepositoryPolicy.UPDATE_POLICY_ALWAYS);
        manager.checkMetadata(session, check);
        assertNull(check.getException());
        assertTrue(check.isRequired());

        check.setArtifactPolicy(RepositoryPolicy.UPDATE_POLICY_DAILY);
        manager.checkMetadata(session, check);
        assertNull(check.getException());
        assertTrue(check.isRequired());

        check.setArtifactPolicy(RepositoryPolicy.UPDATE_POLICY_INTERVAL + ":60");
        manager.checkMetadata(session, check);
        assertNull(check.getException());
        assertTrue(check.isRequired());
    }

    @Test
    public void testCheckMetadataUpdatePolicyNotRequired() {
        UpdateCheck<Metadata, MetadataTransferException> check = newMetadataCheck();

        check.setLocalLastUpdated(System.currentTimeMillis());

        check.setArtifactPolicy(RepositoryPolicy.UPDATE_POLICY_NEVER);
        manager.checkMetadata(session, check);
        assertFalse(check.isRequired());

        check.setArtifactPolicy(RepositoryPolicy.UPDATE_POLICY_DAILY);
        manager.checkMetadata(session, check);
        assertFalse(check.isRequired());

        check.setArtifactPolicy(RepositoryPolicy.UPDATE_POLICY_INTERVAL + ":61");
        manager.checkMetadata(session, check);
        assertFalse(check.isRequired());

        check.setArtifactPolicy("no particular policy");
        manager.checkMetadata(session, check);
        assertFalse(check.isRequired());
    }

    @Test
    public void testCheckMetadata() {
        UpdateCheck<Metadata, MetadataTransferException> check = newMetadataCheck();
        check.setArtifactPolicy(RepositoryPolicy.UPDATE_POLICY_DAILY);

        // existing file, never checked before
        manager.checkMetadata(session, check);
        assertTrue(check.isRequired());

        // just checked
        manager.touchMetadata(session, check);
        resetSessionData(session);

        check = newMetadataCheck();
        check.setArtifactPolicy(RepositoryPolicy.UPDATE_POLICY_INTERVAL + ":60");

        manager.checkMetadata(session, check);
        assertFalse(check.isRequired());

        // no local file
        check.getFile().delete();
        manager.checkMetadata(session, check);
        assertTrue(check.isRequired());
        // (! file.exists && ! repoKey) -> no timestamp
    }

    @Test
    public void testCheckMetadataNoLocalFile() {
        metadata.getFile().delete();

        UpdateCheck<Metadata, MetadataTransferException> check = newMetadataCheck();

        long lastUpdate = new Date().getTime() - HOUR;
        check.setLocalLastUpdated(lastUpdate);

        // ! file.exists && updateRequired -> check in remote repo
        check.setLocalLastUpdated(lastUpdate);
        manager.checkMetadata(session, check);
        assertTrue(check.isRequired());
    }

    @Test
    public void testCheckMetadataNotFoundInRepoCachingEnabled() {
        metadata.getFile().delete();
        session.setResolutionErrorPolicy(new SimpleResolutionErrorPolicy(true, false));

        UpdateCheck<Metadata, MetadataTransferException> check = newMetadataCheck();

        check.setException(new MetadataNotFoundException(metadata, repository, ""));
        manager.touchMetadata(session, check);
        resetSessionData(session);

        // ! file.exists && ! updateRequired -> artifact not found in remote repo
        check = newMetadataCheck().setArtifactPolicy(RepositoryPolicy.UPDATE_POLICY_DAILY);
        manager.checkMetadata(session, check);
        assertFalse(check.isRequired());
        assertTrue(check.getException() instanceof MetadataNotFoundException);
        assertTrue(check.getException().isFromCache());
    }

    @Test
    public void testCheckMetadataNotFoundInRepoCachingDisabled() {
        metadata.getFile().delete();
        session.setResolutionErrorPolicy(new SimpleResolutionErrorPolicy(false, false));

        UpdateCheck<Metadata, MetadataTransferException> check = newMetadataCheck();

        check.setException(new MetadataNotFoundException(metadata, repository, ""));
        manager.touchMetadata(session, check);
        resetSessionData(session);

        // ! file.exists && updateRequired -> check in remote repo
        check = newMetadataCheck().setArtifactPolicy(RepositoryPolicy.UPDATE_POLICY_DAILY);
        manager.checkMetadata(session, check);
        assertTrue(check.isRequired());
        assertNull(check.getException());
    }

    @Test
    public void testCheckMetadataErrorFromRepoCachingEnabled() {
        metadata.getFile().delete();

        UpdateCheck<Metadata, MetadataTransferException> check = newMetadataCheck();
        check.setArtifactPolicy(RepositoryPolicy.UPDATE_POLICY_DAILY);

        check.setException(new MetadataTransferException(metadata, repository, "some error"));
        manager.touchMetadata(session, check);
        resetSessionData(session);

        // ! file.exists && ! updateRequired && previousError -> depends on transfer error caching
        check = newMetadataCheck();
        session.setResolutionErrorPolicy(new SimpleResolutionErrorPolicy(false, true));
        manager.checkMetadata(session, check);
        assertFalse(check.isRequired());
        assertTrue(check.getException() instanceof MetadataTransferException);
        assertTrue(check.getException().getMessage().contains("some error"), String.valueOf(check.getException()));
        assertTrue(check.getException().isFromCache());
    }

    @Test
    public void testCheckMetadataErrorFromRepoCachingDisabled() {
        metadata.getFile().delete();

        UpdateCheck<Metadata, MetadataTransferException> check = newMetadataCheck();
        check.setArtifactPolicy(RepositoryPolicy.UPDATE_POLICY_DAILY);

        check.setException(new MetadataTransferException(metadata, repository, "some error"));
        manager.touchMetadata(session, check);
        resetSessionData(session);

        // ! file.exists && ! updateRequired && previousError -> depends on transfer error caching
        check = newMetadataCheck();
        session.setResolutionErrorPolicy(new SimpleResolutionErrorPolicy(false, false));
        manager.checkMetadata(session, check);
        assertTrue(check.isRequired());
        assertNull(check.getException());
    }

    @Test
    public void testCheckMetadataAtMostOnceDuringSessionEvenIfUpdatePolicyAlways() {
        UpdateCheck<Metadata, MetadataTransferException> check = newMetadataCheck();
        check.setArtifactPolicy(RepositoryPolicy.UPDATE_POLICY_ALWAYS);
        check.setMetadataPolicy(RepositoryPolicy.UPDATE_POLICY_ALWAYS);

        // first check
        manager.checkMetadata(session, check);
        assertTrue(check.isRequired());

        manager.touchMetadata(session, check);

        // second check in same session
        manager.checkMetadata(session, check);
        assertFalse(check.isRequired());
    }

    @Test
    public void testCheckMetadataSessionStateModes() {
        UpdateCheck<Metadata, MetadataTransferException> check = newMetadataCheck();
        check.setArtifactPolicy(RepositoryPolicy.UPDATE_POLICY_ALWAYS);
        check.setMetadataPolicy(RepositoryPolicy.UPDATE_POLICY_ALWAYS);
        manager.touchMetadata(session, check);

        session.setConfigProperty(DefaultUpdateCheckManager.CONFIG_PROP_SESSION_STATE, "bypass");
        manager.checkMetadata(session, check);
        assertTrue(check.isRequired());

        resetSessionData(session);
        manager.touchMetadata(session, check);

        // TODO This will be changed to 'enabled' in a future version
        session.setConfigProperty(DefaultUpdateCheckManager.CONFIG_PROP_SESSION_STATE, "true");
        manager.checkMetadata(session, check);
        assertFalse(check.isRequired());

        // TODO This will be changed to 'disabled' in a future version
        session.setConfigProperty(DefaultUpdateCheckManager.CONFIG_PROP_SESSION_STATE, "false");
        manager.checkMetadata(session, check);
        assertTrue(check.isRequired());
    }

    @Test
    public void testCheckMetadataAtMostOnceDuringSessionEvenIfUpdatePolicyAlways_InvalidFile() {
        UpdateCheck<Metadata, MetadataTransferException> check = newMetadataCheck();
        check.setArtifactPolicy(RepositoryPolicy.UPDATE_POLICY_ALWAYS);
        check.setMetadataPolicy(RepositoryPolicy.UPDATE_POLICY_ALWAYS);
        check.setFileValid(false);

        // first check
        manager.checkMetadata(session, check);
        assertTrue(check.isRequired());

        // first touch, without exception
        manager.touchMetadata(session, check);

        // another check in same session
        manager.checkMetadata(session, check);
        assertTrue(check.isRequired());

        // another touch, with exception
        check.setException(new MetadataNotFoundException(check.getItem(), check.getRepository()));
        manager.touchMetadata(session, check);

        // another check in same session
        manager.checkMetadata(session, check);
        assertFalse(check.isRequired());
    }

    @Test
    public void testCheckMetadataAtMostOnceDuringSessionEvenIfUpdatePolicyAlways_DifferentRepoIdSameUrl() {
        UpdateCheck<Metadata, MetadataTransferException> check = newMetadataCheck();
        check.setArtifactPolicy(RepositoryPolicy.UPDATE_POLICY_ALWAYS);
        check.setMetadataPolicy(RepositoryPolicy.UPDATE_POLICY_ALWAYS);
        check.setFileValid(false);

        // first check
        manager.checkMetadata(session, check);
        assertTrue(check.isRequired());

        manager.touchMetadata(session, check);

        // second check in same session but for repo with different id
        check.setRepository(new RemoteRepository.Builder(check.getRepository())
                .setId("check")
                .build());
        manager.checkMetadata(session, check);
        assertTrue(check.isRequired());
    }

    @Test
    public void testCheckMetadataWhenLocallyMissingEvenIfUpdatePolicyIsNever() {
        UpdateCheck<Metadata, MetadataTransferException> check = newMetadataCheck();
        check.setArtifactPolicy(RepositoryPolicy.UPDATE_POLICY_NEVER);
        session.setResolutionErrorPolicy(new SimpleResolutionErrorPolicy(true, false));

        check.getFile().delete();
        assertFalse(check.getFile().exists(), check.getFile().getAbsolutePath());

        manager.checkMetadata(session, check);
        assertTrue(check.isRequired());
    }

    @Test
    public void testCheckMetadataWhenLocallyPresentButInvalidEvenIfUpdatePolicyIsNever() {
        UpdateCheck<Metadata, MetadataTransferException> check = newMetadataCheck();
        check.setArtifactPolicy(RepositoryPolicy.UPDATE_POLICY_NEVER);
        session.setResolutionErrorPolicy(new SimpleResolutionErrorPolicy(true, false));

        manager.touchMetadata(session, check);
        resetSessionData(session);

        check.setFileValid(false);

        manager.checkMetadata(session, check);
        assertTrue(check.isRequired());
    }

    @Test
    public void testCheckMetadataWhenLocallyDeletedEvenIfTimestampUpToDate() {
        UpdateCheck<Metadata, MetadataTransferException> check = newMetadataCheck();
        session.setResolutionErrorPolicy(new SimpleResolutionErrorPolicy(true, false));

        manager.touchMetadata(session, check);
        resetSessionData(session);

        check.getFile().delete();
        assertFalse(check.getFile().exists(), check.getFile().getAbsolutePath());

        manager.checkMetadata(session, check);
        assertTrue(check.isRequired());
    }

    @Test
    public void testCheckMetadataNotWhenUpdatePolicyIsNeverAndTimestampIsUnavailable() {
        UpdateCheck<Metadata, MetadataTransferException> check = newMetadataCheck();
        check.setArtifactPolicy(RepositoryPolicy.UPDATE_POLICY_NEVER);
        check.setMetadataPolicy(RepositoryPolicy.UPDATE_POLICY_NEVER);
        session.setResolutionErrorPolicy(new SimpleResolutionErrorPolicy(true, false));

        manager.checkMetadata(session, check);
        assertFalse(check.isRequired());
    }

    @Test(expected = NullPointerException.class)
    public void testCheckArtifactFailOnNoFile() {
        UpdateCheck<Artifact, ArtifactTransferException> check = newArtifactCheck();
        check.setItem(artifact.setFile(null));
        check.setFile(null);

        manager.checkArtifact(session, check);
        assertNotNull(check.getException());
    }

    @Test
    public void testCheckArtifactUpdatePolicyRequired() {
        UpdateCheck<Artifact, ArtifactTransferException> check = newArtifactCheck();
        check.setItem(artifact);
        check.setFile(artifact.getFile());

        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.add(Calendar.DATE, -1);
        long lastUpdate = cal.getTimeInMillis();
        artifact.getFile().setLastModified(lastUpdate);
        check.setLocalLastUpdated(lastUpdate);

        check.setArtifactPolicy(RepositoryPolicy.UPDATE_POLICY_ALWAYS);
        manager.checkArtifact(session, check);
        assertNull(check.getException());
        assertTrue(check.isRequired());

        check.setArtifactPolicy(RepositoryPolicy.UPDATE_POLICY_DAILY);
        manager.checkArtifact(session, check);
        assertNull(check.getException());
        assertTrue(check.isRequired());

        check.setArtifactPolicy(RepositoryPolicy.UPDATE_POLICY_INTERVAL + ":60");
        manager.checkArtifact(session, check);
        assertNull(check.getException());
        assertTrue(check.isRequired());
    }

    @Test
    public void testCheckArtifactUpdatePolicyNotRequired() {
        UpdateCheck<Artifact, ArtifactTransferException> check = newArtifactCheck();
        check.setItem(artifact);
        check.setFile(artifact.getFile());

        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.add(Calendar.HOUR_OF_DAY, -1);
        check.setLocalLastUpdated(cal.getTimeInMillis());

        check.setArtifactPolicy(RepositoryPolicy.UPDATE_POLICY_NEVER);
        manager.checkArtifact(session, check);
        assertFalse(check.isRequired());

        check.setArtifactPolicy(RepositoryPolicy.UPDATE_POLICY_DAILY);
        manager.checkArtifact(session, check);
        assertFalse(check.isRequired());

        check.setArtifactPolicy(RepositoryPolicy.UPDATE_POLICY_INTERVAL + ":61");
        manager.checkArtifact(session, check);
        assertFalse(check.isRequired());

        check.setArtifactPolicy("no particular policy");
        manager.checkArtifact(session, check);
        assertFalse(check.isRequired());
    }

    @Test
    public void testCheckArtifact() {
        UpdateCheck<Artifact, ArtifactTransferException> check = newArtifactCheck();
        long fifteenMinutes = new Date().getTime() - (15L * 60L * 1000L);
        check.getFile().setLastModified(fifteenMinutes);
        // time is truncated on setLastModfied
        fifteenMinutes = check.getFile().lastModified();

        // never checked before
        manager.checkArtifact(session, check);
        assertTrue(check.isRequired());

        // just checked
        check.setLocalLastUpdated(0L);
        long lastUpdate = new Date().getTime();
        check.getFile().setLastModified(lastUpdate);
        lastUpdate = check.getFile().lastModified();

        manager.checkArtifact(session, check);
        assertFalse(check.isRequired());

        // no local file, no repo timestamp
        check.setLocalLastUpdated(0L);
        check.getFile().delete();
        manager.checkArtifact(session, check);
        assertTrue(check.isRequired());
    }

    @Test
    public void testCheckArtifactNoLocalFile() {
        artifact.getFile().delete();
        UpdateCheck<Artifact, ArtifactTransferException> check = newArtifactCheck();

        long lastUpdate = new Date().getTime() - HOUR;

        // ! file.exists && updateRequired -> check in remote repo
        check.setLocalLastUpdated(lastUpdate);
        manager.checkArtifact(session, check);
        assertTrue(check.isRequired());
    }

    @Test
    public void testCheckArtifactNotFoundInRepoCachingEnabled() {
        artifact.getFile().delete();
        session.setResolutionErrorPolicy(new SimpleResolutionErrorPolicy(true, false));

        UpdateCheck<Artifact, ArtifactTransferException> check = newArtifactCheck();
        check.setException(new ArtifactNotFoundException(artifact, repository));
        manager.touchArtifact(session, check);
        resetSessionData(session);

        // ! file.exists && ! updateRequired -> artifact not found in remote repo
        check = newArtifactCheck().setArtifactPolicy(RepositoryPolicy.UPDATE_POLICY_DAILY);
        manager.checkArtifact(session, check);
        assertFalse(check.isRequired());
        assertTrue(check.getException() instanceof ArtifactNotFoundException);
        assertTrue(check.getException().isFromCache());
    }

    @Test
    public void testCheckArtifactNotFoundInRepoCachingDisabled() {
        artifact.getFile().delete();
        session.setResolutionErrorPolicy(new SimpleResolutionErrorPolicy(false, false));

        UpdateCheck<Artifact, ArtifactTransferException> check = newArtifactCheck();
        check.setException(new ArtifactNotFoundException(artifact, repository));
        manager.touchArtifact(session, check);
        resetSessionData(session);

        // ! file.exists && updateRequired -> check in remote repo
        check = newArtifactCheck().setArtifactPolicy(RepositoryPolicy.UPDATE_POLICY_DAILY);
        manager.checkArtifact(session, check);
        assertTrue(check.isRequired());
        assertNull(check.getException());
    }

    @Test
    public void testCheckArtifactErrorFromRepoCachingEnabled() {
        artifact.getFile().delete();

        UpdateCheck<Artifact, ArtifactTransferException> check = newArtifactCheck();
        check.setArtifactPolicy(RepositoryPolicy.UPDATE_POLICY_DAILY);
        check.setException(new ArtifactTransferException(artifact, repository, "some error"));
        manager.touchArtifact(session, check);
        resetSessionData(session);

        // ! file.exists && ! updateRequired && previousError -> depends on transfer error caching
        check = newArtifactCheck();
        session.setResolutionErrorPolicy(new SimpleResolutionErrorPolicy(false, true));
        manager.checkArtifact(session, check);
        assertFalse(check.isRequired());
        assertTrue(check.getException() instanceof ArtifactTransferException);
        assertTrue(check.getException().isFromCache());
    }

    @Test
    public void testCheckArtifactErrorFromRepoCachingDisabled() {
        artifact.getFile().delete();

        UpdateCheck<Artifact, ArtifactTransferException> check = newArtifactCheck();
        check.setArtifactPolicy(RepositoryPolicy.UPDATE_POLICY_DAILY);
        check.setException(new ArtifactTransferException(artifact, repository, "some error"));
        manager.touchArtifact(session, check);
        resetSessionData(session);

        // ! file.exists && ! updateRequired && previousError -> depends on transfer error caching
        check = newArtifactCheck();
        session.setResolutionErrorPolicy(new SimpleResolutionErrorPolicy(false, false));
        manager.checkArtifact(session, check);
        assertTrue(check.isRequired());
        assertNull(check.getException());
    }

    @Test
    public void testCheckArtifactAtMostOnceDuringSessionEvenIfUpdatePolicyAlways() {
        UpdateCheck<Artifact, ArtifactTransferException> check = newArtifactCheck();
        check.setArtifactPolicy(RepositoryPolicy.UPDATE_POLICY_ALWAYS);

        // first check
        manager.checkArtifact(session, check);
        assertTrue(check.isRequired());

        manager.touchArtifact(session, check);

        // second check in same session
        manager.checkArtifact(session, check);
        assertFalse(check.isRequired());
    }

    @Test
    public void testCheckArtifactSessionStateModes() {
        UpdateCheck<Artifact, ArtifactTransferException> check = newArtifactCheck();
        check.setArtifactPolicy(RepositoryPolicy.UPDATE_POLICY_ALWAYS);
        manager.touchArtifact(session, check);

        session.setConfigProperty(DefaultUpdateCheckManager.CONFIG_PROP_SESSION_STATE, "bypass");
        manager.checkArtifact(session, check);
        assertTrue(check.isRequired());

        resetSessionData(session);
        manager.touchArtifact(session, check);

        session.setConfigProperty(DefaultUpdateCheckManager.CONFIG_PROP_SESSION_STATE, "true");
        manager.checkArtifact(session, check);
        assertFalse(check.isRequired());

        session.setConfigProperty(DefaultUpdateCheckManager.CONFIG_PROP_SESSION_STATE, "false");
        manager.checkArtifact(session, check);
        assertTrue(check.isRequired());
    }

    @Test
    public void testCheckArtifactAtMostOnceDuringSessionEvenIfUpdatePolicyAlways_InvalidFile() {
        UpdateCheck<Artifact, ArtifactTransferException> check = newArtifactCheck();
        check.setArtifactPolicy(RepositoryPolicy.UPDATE_POLICY_ALWAYS);
        check.setFileValid(false);

        // first check
        manager.checkArtifact(session, check);
        assertTrue(check.isRequired());

        // first touch, without exception
        manager.touchArtifact(session, check);

        // another check in same session
        manager.checkArtifact(session, check);
        assertTrue(check.isRequired());

        // another touch, with exception
        check.setException(new ArtifactNotFoundException(check.getItem(), check.getRepository()));
        manager.touchArtifact(session, check);

        // another check in same session
        manager.checkArtifact(session, check);
        assertFalse(check.isRequired());
    }

    @Test
    public void testCheckArtifactAtMostOnceDuringSessionEvenIfUpdatePolicyAlways_DifferentRepoIdSameUrl() {
        UpdateCheck<Artifact, ArtifactTransferException> check = newArtifactCheck();
        check.setArtifactPolicy(RepositoryPolicy.UPDATE_POLICY_ALWAYS);

        // first check
        manager.checkArtifact(session, check);
        assertTrue(check.isRequired());

        manager.touchArtifact(session, check);

        // second check in same session but for repo with different id
        check.setRepository(new RemoteRepository.Builder(check.getRepository())
                .setId("check")
                .build());
        manager.checkArtifact(session, check);
        assertTrue(check.isRequired());
    }

    @Test
    public void testCheckArtifactWhenLocallyMissingEvenIfUpdatePolicyIsNever() {
        UpdateCheck<Artifact, ArtifactTransferException> check = newArtifactCheck();
        check.setArtifactPolicy(RepositoryPolicy.UPDATE_POLICY_NEVER);
        session.setResolutionErrorPolicy(new SimpleResolutionErrorPolicy(true, false));

        check.getFile().delete();
        assertFalse(check.getFile().exists(), check.getFile().getAbsolutePath());

        manager.checkArtifact(session, check);
        assertTrue(check.isRequired());
    }

    @Test
    public void testCheckArtifactWhenLocallyPresentButInvalidEvenIfUpdatePolicyIsNever() {
        UpdateCheck<Artifact, ArtifactTransferException> check = newArtifactCheck();
        check.setArtifactPolicy(RepositoryPolicy.UPDATE_POLICY_NEVER);
        session.setResolutionErrorPolicy(new SimpleResolutionErrorPolicy(true, false));

        manager.touchArtifact(session, check);
        resetSessionData(session);

        check.setFileValid(false);

        manager.checkArtifact(session, check);
        assertTrue(check.isRequired());
    }

    @Test
    public void testCheckArtifactWhenLocallyDeletedEvenIfTimestampUpToDate() {
        UpdateCheck<Artifact, ArtifactTransferException> check = newArtifactCheck();
        session.setResolutionErrorPolicy(new SimpleResolutionErrorPolicy(true, false));

        manager.touchArtifact(session, check);
        resetSessionData(session);

        check.getFile().delete();
        assertFalse(check.getFile().exists(), check.getFile().getAbsolutePath());

        manager.checkArtifact(session, check);
        assertTrue(check.isRequired());
    }

    @Test
    public void testCheckArtifactNotWhenUpdatePolicyIsNeverAndTimestampIsUnavailable() {
        UpdateCheck<Artifact, ArtifactTransferException> check = newArtifactCheck();
        check.setArtifactPolicy(RepositoryPolicy.UPDATE_POLICY_NEVER);
        session.setResolutionErrorPolicy(new SimpleResolutionErrorPolicy(true, false));

        manager.checkArtifact(session, check);
        assertFalse(check.isRequired());
    }
}
