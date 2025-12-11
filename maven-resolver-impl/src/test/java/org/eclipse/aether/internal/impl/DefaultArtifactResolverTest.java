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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositoryEvent.EventType;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactProperties;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.impl.UpdateCheckManager;
import org.eclipse.aether.impl.VersionResolver;
import org.eclipse.aether.internal.impl.filter.DefaultRemoteRepositoryFilterManager;
import org.eclipse.aether.internal.impl.filter.Filters;
import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.eclipse.aether.internal.test.util.TestLocalRepositoryManager;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.LocalArtifactRegistration;
import org.eclipse.aether.repository.LocalArtifactRequest;
import org.eclipse.aether.repository.LocalArtifactResult;
import org.eclipse.aether.repository.LocalMetadataRegistration;
import org.eclipse.aether.repository.LocalMetadataRequest;
import org.eclipse.aether.repository.LocalMetadataResult;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.repository.WorkspaceRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.VersionRequest;
import org.eclipse.aether.resolution.VersionResolutionException;
import org.eclipse.aether.resolution.VersionResult;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.eclipse.aether.spi.connector.MetadataDownload;
import org.eclipse.aether.spi.connector.filter.RemoteRepositoryFilterSource;
import org.eclipse.aether.spi.io.PathProcessorSupport;
import org.eclipse.aether.transfer.ArtifactNotFoundException;
import org.eclipse.aether.transfer.ArtifactTransferException;
import org.eclipse.aether.util.repository.SimpleResolutionErrorPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 */
public class DefaultArtifactResolverTest {
    private DefaultArtifactResolver resolver;

    private DefaultRepositorySystemSession session;

    private TestLocalRepositoryManager lrm;

    private StubRepositoryConnectorProvider repositoryConnectorProvider;

    private Artifact artifact;

    private RecordingRepositoryConnector connector;

    private HashMap<String, RemoteRepositoryFilterSource> remoteRepositoryFilterSources;

    private DefaultRemoteRepositoryFilterManager remoteRepositoryFilterManager;

    @BeforeEach
    void setup() {
        remoteRepositoryFilterSources = new HashMap<>();
        remoteRepositoryFilterManager = new DefaultRemoteRepositoryFilterManager(remoteRepositoryFilterSources);

        UpdateCheckManager updateCheckManager = new StaticUpdateCheckManager(true);
        repositoryConnectorProvider = new StubRepositoryConnectorProvider();
        VersionResolver versionResolver = new StubVersionResolver();
        session = TestUtils.newSession();
        lrm = (TestLocalRepositoryManager) session.getLocalRepositoryManager();
        resolver = setupArtifactResolver(versionResolver, updateCheckManager);

        artifact = new DefaultArtifact("gid", "aid", "", "ext", "ver");

        connector = new RecordingRepositoryConnector();
        repositoryConnectorProvider.setConnector(connector);
    }

    private DefaultArtifactResolver setupArtifactResolver(
            VersionResolver versionResolver, UpdateCheckManager updateCheckManager) {
        return new DefaultArtifactResolver(
                new PathProcessorSupport(),
                new StubRepositoryEventDispatcher(),
                versionResolver,
                updateCheckManager,
                repositoryConnectorProvider,
                new StubRemoteRepositoryManager(),
                new StubSyncContextFactory(),
                new DefaultOfflineController(),
                Collections.emptyMap(),
                remoteRepositoryFilterManager);
    }

    @AfterEach
    void teardown() throws Exception {
        if (session.getLocalRepository() != null) {
            TestFileUtils.deleteFile(session.getLocalRepository().getBasedir());
        }
    }

    @Test
    void testResolveLocalArtifactSuccessful() throws IOException, ArtifactResolutionException {
        File tmpFile = TestFileUtils.createTempFile("tmp");
        Map<String, String> properties = new HashMap<>();
        properties.put(ArtifactProperties.LOCAL_PATH, tmpFile.getAbsolutePath());
        artifact = artifact.setProperties(properties);

        ArtifactRequest request = new ArtifactRequest(artifact, null, "");
        ArtifactResult result = resolver.resolveArtifact(session, request);

        assertTrue(result.getExceptions().isEmpty());

        Artifact resolved = result.getArtifact();
        assertNotNull(resolved.getFile());
        resolved = resolved.setFile(null);

        assertEquals(artifact, resolved);
    }

    @Test
    void testResolveLocalArtifactUnsuccessful() throws IOException {
        File tmpFile = TestFileUtils.createTempFile("tmp");
        Map<String, String> properties = new HashMap<>();
        properties.put(ArtifactProperties.LOCAL_PATH, tmpFile.getAbsolutePath());
        artifact = artifact.setProperties(properties);

        tmpFile.delete();

        ArtifactRequest request = new ArtifactRequest(artifact, null, "");

        try {
            resolver.resolveArtifact(session, request);
            fail("expected exception");
        } catch (ArtifactResolutionException e) {
            assertNotNull(e.getResults());
            assertEquals(1, e.getResults().size());

            ArtifactResult result = e.getResults().get(0);

            assertSame(request, result.getRequest());

            assertFalse(result.getExceptions().isEmpty());
            assertInstanceOf(
                    ArtifactNotFoundException.class, result.getExceptions().get(0));

            Artifact resolved = result.getArtifact();
            assertNull(resolved);
        }
    }

    @Test
    void testResolveRemoteArtifact() throws ArtifactResolutionException {
        connector.setExpectGet(artifact);

        ArtifactRequest request = new ArtifactRequest(artifact, null, "");
        request.addRepository(new RemoteRepository.Builder("id", "default", "file:///").build());

        ArtifactResult result = resolver.resolveArtifact(session, request);

        assertTrue(result.getExceptions().isEmpty());

        Artifact resolved = result.getArtifact();
        assertNotNull(resolved.getFile());

        resolved = resolved.setFile(null);
        assertEquals(artifact, resolved);

        connector.assertSeenExpected();
    }

    @Test
    void testResolveRemoteArtifactUnsuccessful() {
        RecordingRepositoryConnector connector = new RecordingRepositoryConnector() {

            @Override
            public void get(
                    Collection<? extends ArtifactDownload> artifactDownloads,
                    Collection<? extends MetadataDownload> metadataDownloads) {
                super.get(artifactDownloads, metadataDownloads);
                ArtifactDownload download = artifactDownloads.iterator().next();
                ArtifactTransferException exception =
                        new ArtifactNotFoundException(download.getArtifact(), null, "not found");
                download.setException(exception);
            }
        };

        connector.setExpectGet(artifact);
        repositoryConnectorProvider.setConnector(connector);

        ArtifactRequest request = new ArtifactRequest(artifact, null, "");
        request.addRepository(new RemoteRepository.Builder("id", "default", "file:///").build());

        try {
            resolver.resolveArtifact(session, request);
            fail("expected exception");
        } catch (ArtifactResolutionException e) {
            connector.assertSeenExpected();
            assertNotNull(e.getResults());
            assertEquals(1, e.getResults().size());

            ArtifactResult result = e.getResults().get(0);

            assertSame(request, result.getRequest());

            assertFalse(result.getExceptions().isEmpty());
            assertInstanceOf(
                    ArtifactNotFoundException.class, result.getExceptions().get(0));

            Artifact resolved = result.getArtifact();
            assertNull(resolved);
        }
    }

    @Test
    void testResolveRemoteArtifactAlwaysAcceptFilter() throws ArtifactResolutionException {
        remoteRepositoryFilterSources.put("filter1", Filters.neverAcceptFrom("invalid repo id"));
        remoteRepositoryFilterSources.put("filter2", Filters.alwaysAccept());
        connector.setExpectGet(artifact);

        ArtifactRequest request = new ArtifactRequest(artifact, null, "");
        request.addRepository(new RemoteRepository.Builder("id", "default", "file:///").build());

        ArtifactResult result = resolver.resolveArtifact(session, request);

        assertTrue(result.getExceptions().isEmpty());

        Artifact resolved = result.getArtifact();
        assertNotNull(resolved.getFile());

        resolved = resolved.setFile(null);
        assertEquals(artifact, resolved);

        connector.assertSeenExpected();
    }

    @Test
    void testResolveRemoteArtifactNeverAcceptFilter() {
        remoteRepositoryFilterSources.put("filter1", Filters.neverAcceptFrom("invalid repo id"));
        remoteRepositoryFilterSources.put("filter2", Filters.neverAccept());
        // connector.setExpectGet( artifact ); // should not see it

        ArtifactRequest request = new ArtifactRequest(artifact, null, "project");
        request.addRepository(new RemoteRepository.Builder("id", "default", "file:///").build());

        try {
            resolver.resolveArtifact(session, request);
            fail("expected exception");
        } catch (ArtifactResolutionException e) {
            connector.assertSeenExpected();
            assertNotNull(e.getResults());
            assertEquals(1, e.getResults().size());

            ArtifactResult result = e.getResults().get(0);

            assertSame(request, result.getRequest());

            assertFalse(result.getExceptions().isEmpty());
            assertInstanceOf(
                    ArtifactNotFoundException.class, result.getExceptions().get(0));
            assertEquals(
                    "never-accept: never accept", result.getExceptions().get(0).getMessage());

            Artifact resolved = result.getArtifact();
            assertNull(resolved);
        }
    }

    @Test
    void testResolveRemoteArtifactAlwaysAcceptFromRepoFilter() throws ArtifactResolutionException {
        remoteRepositoryFilterSources.put("filter1", Filters.alwaysAcceptFrom("id"));
        connector.setExpectGet(artifact);

        ArtifactRequest request = new ArtifactRequest(artifact, null, "");
        request.addRepository(new RemoteRepository.Builder("id", "default", "file:///").build());

        ArtifactResult result = resolver.resolveArtifact(session, request);

        assertTrue(result.getExceptions().isEmpty());

        Artifact resolved = result.getArtifact();
        assertNotNull(resolved.getFile());

        resolved = resolved.setFile(null);
        assertEquals(artifact, resolved);

        connector.assertSeenExpected();
    }

    @Test
    void testResolveRemoteArtifactNeverAcceptFilterFromRepo() {
        remoteRepositoryFilterSources.put("filter1", Filters.neverAcceptFrom("id"));
        // connector.setExpectGet( artifact ); // should not see it

        ArtifactRequest request = new ArtifactRequest(artifact, null, "project");
        request.addRepository(new RemoteRepository.Builder("id", "default", "file:///").build());

        try {
            resolver.resolveArtifact(session, request);
            fail("expected exception");
        } catch (ArtifactResolutionException e) {
            connector.assertSeenExpected();
            assertNotNull(e.getResults());
            assertEquals(1, e.getResults().size());

            ArtifactResult result = e.getResults().get(0);

            assertSame(request, result.getRequest());

            assertFalse(result.getExceptions().isEmpty());
            assertInstanceOf(
                    ArtifactNotFoundException.class, result.getExceptions().get(0));
            assertEquals(
                    "never-accept-id: matched", result.getExceptions().get(0).getMessage());

            Artifact resolved = result.getArtifact();
            assertNull(resolved);
        }
    }

    @Test
    void testArtifactNotFoundCache() throws Exception {
        RecordingRepositoryConnector connector = new RecordingRepositoryConnector() {
            @Override
            public void get(
                    Collection<? extends ArtifactDownload> artifactDownloads,
                    Collection<? extends MetadataDownload> metadataDownloads) {
                super.get(artifactDownloads, metadataDownloads);
                for (ArtifactDownload download : artifactDownloads) {
                    download.getFile().delete();
                    ArtifactTransferException exception =
                            new ArtifactNotFoundException(download.getArtifact(), null, "not found");
                    download.setException(exception);
                }
            }
        };

        repositoryConnectorProvider.setConnector(connector);
        resolver = setupArtifactResolver(
                new StubVersionResolver(),
                new DefaultUpdateCheckManager(
                        new DefaultTrackingFileManager(),
                        new DefaultUpdatePolicyAnalyzer(),
                        new DefaultPathProcessor()));

        session.setResolutionErrorPolicy(new SimpleResolutionErrorPolicy(true, false));
        session.setUpdatePolicy(RepositoryPolicy.UPDATE_POLICY_NEVER);

        RemoteRepository remoteRepo = new RemoteRepository.Builder("id", "default", "file:///").build();

        Artifact artifact1 = artifact;
        Artifact artifact2 = artifact.setVersion("ver2");

        ArtifactRequest request1 = new ArtifactRequest(artifact1, Arrays.asList(remoteRepo), "");
        ArtifactRequest request2 = new ArtifactRequest(artifact2, Arrays.asList(remoteRepo), "");

        connector.setExpectGet(artifact1, artifact2);
        try {
            resolver.resolveArtifacts(session, Arrays.asList(request1, request2));
            fail("expected exception");
        } catch (ArtifactResolutionException e) {
            connector.assertSeenExpected();
        }

        TestFileUtils.writeString(
                new File(lrm.getRepository().getBasedir(), lrm.getPathForLocalArtifact(artifact2)), "artifact");
        lrm.setArtifactAvailability(artifact2, false);

        DefaultUpdateCheckManagerTest.resetSessionData(session);
        connector.resetActual();
        connector.setExpectGet(new Artifact[0]);
        try {
            resolver.resolveArtifacts(session, Arrays.asList(request1, request2));
            fail("expected exception");
        } catch (ArtifactResolutionException e) {
            connector.assertSeenExpected();
            for (ArtifactResult result : e.getResults()) {
                Exception ex = result.getExceptions().get(0);
                assertInstanceOf(ArtifactNotFoundException.class, ex, ex.toString());
                assertTrue(ex.getMessage().contains("cached"), ex.toString());
            }
        }
    }

    @Test
    void testResolveFromWorkspace() throws IOException, ArtifactResolutionException {
        WorkspaceReader workspace = new WorkspaceReader() {

            public WorkspaceRepository getRepository() {
                return new WorkspaceRepository("default");
            }

            public List<String> findVersions(Artifact artifact) {
                return Arrays.asList(artifact.getVersion());
            }

            public File findArtifact(Artifact artifact) {
                try {
                    return TestFileUtils.createTempFile(artifact.toString());
                } catch (IOException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            }
        };
        session.setWorkspaceReader(workspace);

        ArtifactRequest request = new ArtifactRequest(artifact, null, "");
        request.addRepository(new RemoteRepository.Builder("id", "default", "file:///").build());

        ArtifactResult result = resolver.resolveArtifact(session, request);

        assertTrue(result.getExceptions().isEmpty());

        Artifact resolved = result.getArtifact();
        assertNotNull(resolved.getFile());

        assertEquals(resolved.toString(), TestFileUtils.readString(resolved.getFile()));

        resolved = resolved.setFile(null);
        assertEquals(artifact, resolved);

        connector.assertSeenExpected();
    }

    @Test
    void testResolveFromWorkspaceFallbackToRepository() throws ArtifactResolutionException {
        WorkspaceReader workspace = new WorkspaceReader() {

            public WorkspaceRepository getRepository() {
                return new WorkspaceRepository("default");
            }

            public List<String> findVersions(Artifact artifact) {
                return Arrays.asList(artifact.getVersion());
            }

            public File findArtifact(Artifact artifact) {
                return null;
            }
        };
        session.setWorkspaceReader(workspace);

        connector.setExpectGet(artifact);
        repositoryConnectorProvider.setConnector(connector);

        ArtifactRequest request = new ArtifactRequest(artifact, null, "");
        request.addRepository(new RemoteRepository.Builder("id", "default", "file:///").build());

        ArtifactResult result = resolver.resolveArtifact(session, request);

        assertTrue(result.getExceptions().isEmpty(), "exception on resolveArtifact");

        Artifact resolved = result.getArtifact();
        assertNotNull(resolved.getFile());

        resolved = resolved.setFile(null);
        assertEquals(artifact, resolved);

        connector.assertSeenExpected();
    }

    @Test
    void testRepositoryEventsSuccessfulLocal() throws ArtifactResolutionException, IOException {
        RecordingRepositoryListener listener = new RecordingRepositoryListener();
        session.setRepositoryListener(listener);

        File tmpFile = TestFileUtils.createTempFile("tmp");
        Map<String, String> properties = new HashMap<>();
        properties.put(ArtifactProperties.LOCAL_PATH, tmpFile.getAbsolutePath());
        artifact = artifact.setProperties(properties);

        ArtifactRequest request = new ArtifactRequest(artifact, null, "");
        resolver.resolveArtifact(session, request);

        List<RepositoryEvent> events = listener.getEvents();
        assertEquals(2, events.size());
        RepositoryEvent event = events.get(0);
        assertEquals(EventType.ARTIFACT_RESOLVING, event.getType());
        assertNull(event.getException());
        assertEquals(artifact, event.getArtifact());

        event = events.get(1);
        assertEquals(EventType.ARTIFACT_RESOLVED, event.getType());
        assertNull(event.getException());
        assertEquals(artifact, event.getArtifact().setFile(null));
    }

    @Test
    void testRepositoryEventsUnsuccessfulLocal() {
        RecordingRepositoryListener listener = new RecordingRepositoryListener();
        session.setRepositoryListener(listener);

        Map<String, String> properties = new HashMap<>();
        properties.put(ArtifactProperties.LOCAL_PATH, "doesnotexist");
        artifact = artifact.setProperties(properties);

        ArtifactRequest request = new ArtifactRequest(artifact, null, "");
        try {
            resolver.resolveArtifact(session, request);
            fail("expected exception");
        } catch (ArtifactResolutionException ignored) {
        }

        List<RepositoryEvent> events = listener.getEvents();
        assertEquals(2, events.size());

        RepositoryEvent event = events.get(0);
        assertEquals(artifact, event.getArtifact());
        assertEquals(EventType.ARTIFACT_RESOLVING, event.getType());

        event = events.get(1);
        assertEquals(artifact, event.getArtifact());
        assertEquals(EventType.ARTIFACT_RESOLVED, event.getType());
        assertNotNull(event.getException());
        assertEquals(1, event.getExceptions().size());
    }

    @Test
    void testRepositoryEventsSuccessfulRemote() throws ArtifactResolutionException {
        RecordingRepositoryListener listener = new RecordingRepositoryListener();
        session.setRepositoryListener(listener);

        ArtifactRequest request = new ArtifactRequest(artifact, null, "");
        request.addRepository(new RemoteRepository.Builder("id", "default", "file:///").build());

        resolver.resolveArtifact(session, request);

        List<RepositoryEvent> events = listener.getEvents();
        assertEquals(4, events.size(), events.toString());
        RepositoryEvent event = events.get(0);
        assertEquals(EventType.ARTIFACT_RESOLVING, event.getType());
        assertNull(event.getException());
        assertEquals(artifact, event.getArtifact());

        event = events.get(1);
        assertEquals(EventType.ARTIFACT_DOWNLOADING, event.getType());
        assertNull(event.getException());
        assertEquals(artifact, event.getArtifact().setFile(null));

        event = events.get(2);
        assertEquals(EventType.ARTIFACT_DOWNLOADED, event.getType());
        assertNull(event.getException());
        assertEquals(artifact, event.getArtifact().setFile(null));

        event = events.get(3);
        assertEquals(EventType.ARTIFACT_RESOLVED, event.getType());
        assertNull(event.getException());
        assertEquals(artifact, event.getArtifact().setFile(null));
    }

    @Test
    void testRepositoryEventsUnsuccessfulRemote() {
        RecordingRepositoryConnector connector = new RecordingRepositoryConnector() {

            @Override
            public void get(
                    Collection<? extends ArtifactDownload> artifactDownloads,
                    Collection<? extends MetadataDownload> metadataDownloads) {
                super.get(artifactDownloads, metadataDownloads);
                ArtifactDownload download = artifactDownloads.iterator().next();
                ArtifactTransferException exception =
                        new ArtifactNotFoundException(download.getArtifact(), null, "not found");
                download.setException(exception);
            }
        };
        repositoryConnectorProvider.setConnector(connector);

        RecordingRepositoryListener listener = new RecordingRepositoryListener();
        session.setRepositoryListener(listener);

        ArtifactRequest request = new ArtifactRequest(artifact, null, "");
        request.addRepository(new RemoteRepository.Builder("id", "default", "file:///").build());

        try {
            resolver.resolveArtifact(session, request);
            fail("expected exception");
        } catch (ArtifactResolutionException ignored) {
        }

        List<RepositoryEvent> events = listener.getEvents();
        assertEquals(4, events.size(), events.toString());

        RepositoryEvent event = events.get(0);
        assertEquals(artifact, event.getArtifact());
        assertEquals(EventType.ARTIFACT_RESOLVING, event.getType());

        event = events.get(1);
        assertEquals(artifact, event.getArtifact());
        assertEquals(EventType.ARTIFACT_DOWNLOADING, event.getType());

        event = events.get(2);
        assertEquals(artifact, event.getArtifact());
        assertEquals(EventType.ARTIFACT_DOWNLOADED, event.getType());
        assertNotNull(event.getException());
        assertEquals(1, event.getExceptions().size());

        event = events.get(3);
        assertEquals(artifact, event.getArtifact());
        assertEquals(EventType.ARTIFACT_RESOLVED, event.getType());
        assertNotNull(event.getException());
        assertEquals(1, event.getExceptions().size());
    }

    @Test
    void testVersionResolverFails() {
        resolver = setupArtifactResolver(
                new VersionResolver() {
                    @Override
                    public VersionResult resolveVersion(RepositorySystemSession session, VersionRequest request)
                            throws VersionResolutionException {
                        throw new VersionResolutionException(new VersionResult(request));
                    }
                },
                new StaticUpdateCheckManager(true));

        ArtifactRequest request = new ArtifactRequest(artifact, null, "");
        try {
            resolver.resolveArtifact(session, request);
            fail("expected exception");
        } catch (ArtifactResolutionException e) {
            connector.assertSeenExpected();
            assertNotNull(e.getResults());
            assertEquals(1, e.getResults().size());

            ArtifactResult result = e.getResults().get(0);

            assertSame(request, result.getRequest());

            assertFalse(result.getExceptions().isEmpty());
            assertInstanceOf(
                    VersionResolutionException.class, result.getExceptions().get(0));

            Artifact resolved = result.getArtifact();
            assertNull(resolved);
        }
    }

    @Test
    void testRepositoryEventsOnVersionResolverFail() {
        resolver = setupArtifactResolver(
                new VersionResolver() {
                    @Override
                    public VersionResult resolveVersion(RepositorySystemSession session, VersionRequest request)
                            throws VersionResolutionException {
                        throw new VersionResolutionException(new VersionResult(request));
                    }
                },
                new StaticUpdateCheckManager(true));

        RecordingRepositoryListener listener = new RecordingRepositoryListener();
        session.setRepositoryListener(listener);

        ArtifactRequest request = new ArtifactRequest(artifact, null, "");
        try {
            resolver.resolveArtifact(session, request);
            fail("expected exception");
        } catch (ArtifactResolutionException ignored) {
        }

        List<RepositoryEvent> events = listener.getEvents();
        assertEquals(2, events.size());

        RepositoryEvent event = events.get(0);
        assertEquals(artifact, event.getArtifact());
        assertEquals(EventType.ARTIFACT_RESOLVING, event.getType());

        event = events.get(1);
        assertEquals(artifact, event.getArtifact());
        assertEquals(EventType.ARTIFACT_RESOLVED, event.getType());
        assertNotNull(event.getException());
        assertEquals(1, event.getExceptions().size());
    }

    @Test
    void testLocalArtifactAvailable() throws ArtifactResolutionException {
        session.setLocalRepositoryManager(new LocalRepositoryManager() {

            public LocalRepository getRepository() {
                return null;
            }

            public String getPathForRemoteMetadata(Metadata metadata, RemoteRepository repository, String context) {
                return null;
            }

            public String getPathForRemoteArtifact(Artifact artifact, RemoteRepository repository, String context) {
                return null;
            }

            public String getPathForLocalMetadata(Metadata metadata) {
                return null;
            }

            public String getPathForLocalArtifact(Artifact artifact) {
                return null;
            }

            public LocalArtifactResult find(RepositorySystemSession session, LocalArtifactRequest request) {

                LocalArtifactResult result = new LocalArtifactResult(request);
                result.setAvailable(true);
                try {
                    result.setFile(TestFileUtils.createTempFile(""));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return result;
            }

            public void add(RepositorySystemSession session, LocalArtifactRegistration request) {}

            public LocalMetadataResult find(RepositorySystemSession session, LocalMetadataRequest request) {
                LocalMetadataResult result = new LocalMetadataResult(request);
                try {
                    result.setFile(TestFileUtils.createTempFile(""));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return result;
            }

            public void add(RepositorySystemSession session, LocalMetadataRegistration request) {}
        });

        ArtifactRequest request = new ArtifactRequest(artifact, null, "");
        request.addRepository(new RemoteRepository.Builder("id", "default", "file:///").build());

        ArtifactResult result = resolver.resolveArtifact(session, request);

        assertTrue(result.getExceptions().isEmpty());

        Artifact resolved = result.getArtifact();
        assertNotNull(resolved.getFile());

        resolved = resolved.setFile(null);
        assertEquals(artifact, resolved);
    }

    @Test
    void testFindInLocalRepositoryWhenVersionWasFoundInLocalRepository() throws ArtifactResolutionException {
        session.setLocalRepositoryManager(new LocalRepositoryManager() {

            public LocalRepository getRepository() {
                return new LocalRepository(lrm.getRepository().getBasePath());
            }

            public String getPathForRemoteMetadata(Metadata metadata, RemoteRepository repository, String context) {
                return null;
            }

            public String getPathForRemoteArtifact(Artifact artifact, RemoteRepository repository, String context) {
                return null;
            }

            public String getPathForLocalMetadata(Metadata metadata) {
                return null;
            }

            public String getPathForLocalArtifact(Artifact artifact) {
                return null;
            }

            public LocalArtifactResult find(RepositorySystemSession session, LocalArtifactRequest request) {

                LocalArtifactResult result = new LocalArtifactResult(request);
                result.setAvailable(false);
                try {
                    result.setFile(TestFileUtils.createTempFile(""));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return result;
            }

            public void add(RepositorySystemSession session, LocalArtifactRegistration request) {}

            public LocalMetadataResult find(RepositorySystemSession session, LocalMetadataRequest request) {
                return new LocalMetadataResult(request);
            }

            public void add(RepositorySystemSession session, LocalMetadataRegistration request) {}
        });
        ArtifactRequest request = new ArtifactRequest(artifact, null, "");
        request.addRepository(new RemoteRepository.Builder("id", "default", "file:///").build());

        resolver = setupArtifactResolver(
                new VersionResolver() {
                    @Override
                    public VersionResult resolveVersion(RepositorySystemSession session, VersionRequest request) {
                        return new VersionResult(request)
                                .setRepository(new LocalRepository("id"))
                                .setVersion(request.getArtifact().getVersion());
                    }
                },
                new StaticUpdateCheckManager(true));

        ArtifactResult result = resolver.resolveArtifact(session, request);

        assertTrue(result.getExceptions().isEmpty());

        Artifact resolved = result.getArtifact();
        assertNotNull(resolved.getFile());

        resolved = resolved.setFile(null);
        assertEquals(artifact, resolved);
    }

    @Test
    void testFindInLocalRepositoryWhenVersionRangeWasResolvedFromLocalRepository() throws ArtifactResolutionException {
        session.setLocalRepositoryManager(new LocalRepositoryManager() {

            public LocalRepository getRepository() {
                return new LocalRepository(lrm.getRepository().getBasePath());
            }

            public String getPathForRemoteMetadata(Metadata metadata, RemoteRepository repository, String context) {
                return null;
            }

            public String getPathForRemoteArtifact(Artifact artifact, RemoteRepository repository, String context) {
                return null;
            }

            public String getPathForLocalMetadata(Metadata metadata) {
                return null;
            }

            public String getPathForLocalArtifact(Artifact artifact) {
                return null;
            }

            public LocalArtifactResult find(RepositorySystemSession session, LocalArtifactRequest request) {

                LocalArtifactResult result = new LocalArtifactResult(request);
                result.setAvailable(false);
                try {
                    result.setFile(TestFileUtils.createTempFile(""));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return result;
            }

            public void add(RepositorySystemSession session, LocalArtifactRegistration request) {}

            public LocalMetadataResult find(RepositorySystemSession session, LocalMetadataRequest request) {
                return new LocalMetadataResult(request);
            }

            public void add(RepositorySystemSession session, LocalMetadataRegistration request) {}
        });
        ArtifactRequest request = new ArtifactRequest(artifact, null, "");

        resolver = setupArtifactResolver(
                new VersionResolver() {
                    @Override
                    public VersionResult resolveVersion(RepositorySystemSession session, VersionRequest request) {
                        return new VersionResult(request)
                                .setVersion(request.getArtifact().getVersion());
                    }
                },
                new StaticUpdateCheckManager(true));

        ArtifactResult result = resolver.resolveArtifact(session, request);

        assertTrue(result.getExceptions().isEmpty());

        Artifact resolved = result.getArtifact();
        assertNotNull(resolved.getFile());

        resolved = resolved.setFile(null);
        assertEquals(artifact, resolved);
    }
}
