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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.LocalArtifactRegistration;
import org.eclipse.aether.repository.LocalArtifactRequest;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.localrepo.LocalRepositoryManagerFactory;
import org.eclipse.aether.util.StringDigestUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UT for {@link EnhancedLocalRepositoryManagerFactory}, focused on the wiring of the tracking-scoped repository
 * key function: provenance tracking entries must be URL-qualified by default, so a repository declared under a
 * trusted ID but pointing at a different URL is a different origin, while path composition and everything else
 * keeps following the (unchanged) system-wide key function.
 */
public class EnhancedLocalRepositoryManagerFactoryTest {

    @TempDir
    Path basedir;

    private DefaultRepositorySystemSession session;

    private EnhancedLocalRepositoryManagerFactory factory;

    private final RemoteRepository repository =
            new RemoteRepository.Builder("internal", "default", "https://repo.corp.example.com/maven2/").build();

    private final RemoteRepository impostor =
            new RemoteRepository.Builder("internal", "default", "https://impostor.invalid/maven2/").build();

    private final String context = "project/compile";

    @BeforeEach
    void setup() {
        session = TestUtils.newSession();
        factory = new EnhancedLocalRepositoryManagerFactory(
                new DefaultLocalPathComposer(),
                new TrackingFileManagerSupplier().get(),
                new DefaultLocalPathPrefixComposerFactory(new DefaultRepositoryKeyFunctionFactory()),
                new DefaultRepositoryKeyFunctionFactory());
    }

    private LocalRepositoryManager newManager() throws Exception {
        return factory.newInstance(session, new LocalRepository(basedir));
    }

    private Artifact addTrackedRemoteArtifact(LocalRepositoryManager manager) throws Exception {
        Artifact artifact = new DefaultArtifact("gid:aid:1.0");
        manager.add(session, new LocalArtifactRegistration(artifact, repository, Collections.singleton(context)));
        Path file = basedir.resolve(manager.getPathForRemoteArtifact(artifact, repository, context));
        TestFileUtils.writeString(file.toFile(), "artifact");
        return artifact;
    }

    @Test
    void defaultTrackingKeysAreUrlQualified() throws Exception {
        LocalRepositoryManager manager = newManager();
        Artifact artifact = addTrackedRemoteArtifact(manager);

        // requested from the repository the artifact really came from: available
        LocalArtifactRequest fromReal =
                new LocalArtifactRequest(artifact, Collections.singletonList(repository), context);
        assertTrue(manager.find(session, fromReal).isAvailable());

        // requested from an impostor sharing the trusted ID but pointing at another URL: a different
        // origin, so the cached bytes must not be accepted (they are re-fetched, checksum-validated)
        LocalArtifactRequest fromImpostor =
                new LocalArtifactRequest(artifact, Collections.singletonList(impostor), context);
        assertFalse(manager.find(session, fromImpostor).isAvailable());
    }

    @Test
    void trackingKeyFunctionDoesNotAffectPathComposition() throws Exception {
        LocalRepositoryManager manager = newManager();
        Artifact artifact = new DefaultArtifact("gid:aid:1.0");

        // the URL-qualified default is scoped to tracking entries: artifact and metadata paths keep
        // following the system-wide key function (default unchanged), so no local repository re-layout
        String urlHash = StringDigestUtil.sha1(repository.getUrl());
        assertFalse(
                manager.getPathForRemoteArtifact(artifact, repository, context).contains(urlHash));
        assertFalse(manager.getPathForRemoteMetadata(
                        new DefaultMetadata("gid", "aid", "1.0", "maven-metadata.xml", Metadata.Nature.RELEASE),
                        repository,
                        context)
                .contains(urlHash));
    }

    @Test
    void explicitSystemWideKeyFunctionIsFollowedByTracking() throws Exception {
        // an explicitly configured system-wide function keeps all consumers on one function; "nid" is
        // the documented legacy (ID-only) opt-out
        session.setConfigProperty(ConfigurationProperties.REPOSITORY_SYSTEM_REPOSITORY_KEY_FUNCTION, "nid");
        LocalRepositoryManager manager = newManager();
        Artifact artifact = addTrackedRemoteArtifact(manager);

        LocalArtifactRequest fromImpostor =
                new LocalArtifactRequest(artifact, Collections.singletonList(impostor), context);
        assertTrue(manager.find(session, fromImpostor).isAvailable());
    }

    @Test
    void trackingSpecificConfigurationOverridesSystemWideFunction() throws Exception {
        session.setConfigProperty(ConfigurationProperties.REPOSITORY_SYSTEM_REPOSITORY_KEY_FUNCTION, "nid");
        session.setConfigProperty(
                EnhancedLocalRepositoryManagerFactory.CONFIG_PROP_TRACKING_REPOSITORY_KEY_FUNCTION, "nid_hurl");
        LocalRepositoryManager manager = newManager();
        Artifact artifact = addTrackedRemoteArtifact(manager);

        LocalArtifactRequest fromImpostor =
                new LocalArtifactRequest(artifact, Collections.singletonList(impostor), context);
        assertFalse(manager.find(session, fromImpostor).isAvailable());
    }

    @Test
    void newInstanceForNotYetExistingBasedir() throws Exception {
        // a fresh local repository (first build on a machine, or a new -Dmaven.repo.local) does not exist yet
        Path missing = basedir.resolve("not-yet-created");
        assertFalse(Files.exists(missing));

        LocalRepositoryManager manager = factory.newInstance(session, new LocalRepository(missing));

        assertInstanceOf(EnhancedLocalRepositoryManager.class, manager);
    }

    @Test
    void providerSelectsEnhancedManagerForNotYetExistingBasedir() throws Exception {
        // the provider treats NoLocalRepositoryManagerException as "try the next factory", so a failing
        // enhanced factory would silently degrade the whole session to the untracked simple manager
        Path missing = basedir.resolve("not-yet-created");
        Map<String, LocalRepositoryManagerFactory> factories = new HashMap<>();
        factories.put(EnhancedLocalRepositoryManagerFactory.NAME, factory);
        factories.put(SimpleLocalRepositoryManagerFactory.NAME, new SimpleLocalRepositoryManagerFactory());
        DefaultLocalRepositoryProvider provider = new DefaultLocalRepositoryProvider(factories);

        LocalRepositoryManager manager = provider.newLocalRepositoryManager(session, new LocalRepository(missing));

        assertInstanceOf(EnhancedLocalRepositoryManager.class, manager);
    }
}
