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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.LocalArtifactRegistration;
import org.eclipse.aether.repository.LocalArtifactRequest;
import org.eclipse.aether.repository.LocalArtifactResult;
import org.eclipse.aether.repository.LocalMetadataRegistration;
import org.eclipse.aether.repository.LocalMetadataRequest;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.localrepo.LocalRepositoryManagerFactory;
import org.eclipse.aether.util.StringDigestUtil;
import org.eclipse.aether.util.repository.ChainedLocalRepositoryManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
        Path file = manager.getAbsolutePathForRemoteArtifact(artifact, repository, context);
        Files.createDirectories(file.getParent());
        Files.write(file, "artifact".getBytes(StandardCharsets.UTF_8));
        return artifact;
    }

    @Test
    void defaultTrackingKeysAreUrlQualified() throws Exception {
        session.setConfigProperty(EnhancedLocalRepositoryManagerFactory.CONFIG_PROP_IGNORE_AVAILABILITY, false);
        LocalRepositoryManager manager = newManager();
        Artifact artifact = addTrackedRemoteArtifact(manager);

        // requested from the repository the artifact really came from: available
        LocalArtifactRequest fromReal =
                new LocalArtifactRequest(artifact, Collections.singletonList(repository), context);
        assertTrue(manager.find(session, fromReal).isAvailable());

        // requested from a repository sharing the trusted ID but pointing at another URL:
        // accepted via the same-id prefix fallback (avoiding forced re-downloads when the URL
        // of a well-known repository changes, e.g. ITs overriding central to file:target/null)
        // Note: this may happen ONLY if local repository is shared with 3.9/Resolver 1.9
        LocalArtifactRequest fromSameId =
                new LocalArtifactRequest(artifact, Collections.singletonList(impostor), context);
        assertTrue(manager.find(session, fromSameId).isAvailable());
    }

    // asserts on the composed relative path, which only the deprecated getPathFor* methods expose
    @SuppressWarnings("deprecation")
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
        session.setConfigProperty(EnhancedLocalRepositoryManagerFactory.CONFIG_PROP_IGNORE_AVAILABILITY, false);
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
        session.setConfigProperty(EnhancedLocalRepositoryManagerFactory.CONFIG_PROP_IGNORE_AVAILABILITY, false);
        session.setConfigProperty(ConfigurationProperties.REPOSITORY_SYSTEM_REPOSITORY_KEY_FUNCTION, "nid");
        session.setConfigProperty(ConfigurationProperties.REPOSITORY_TRACKING_REPOSITORY_KEY_FUNCTION, "nid_hurl");
        LocalRepositoryManager manager = newManager();
        Artifact artifact = addTrackedRemoteArtifact(manager);

        // With nid_hurl tracking and nid as system-wide function, the exact nid_hurl key matches
        // the entry written by addTrackedRemoteArtifact; but even with a different URL (impostor),
        // the prefix-based fallback still accepts the artifact because repo IDs match
        // Note: this may happen ONLY if local repository is shared with 3.9/Resolver 1.9
        LocalArtifactRequest fromImpostor =
                new LocalArtifactRequest(artifact, Collections.singletonList(impostor), context);
        assertTrue(manager.find(session, fromImpostor).isAvailable());
    }

    private Path trackingFileFor(LocalRepositoryManager manager, Artifact artifact, String trackingFilename) {
        return basedir.resolve(manager.getPathForRemoteArtifact(artifact, repository, context))
                .getParent()
                .resolve(trackingFilename);
    }

    @Test
    void trackingFilenameFromCurrentKey() throws Exception {
        session.setConfigProperty(EnhancedLocalRepositoryManagerFactory.CONFIG_PROP_TRACKING_FILENAME, "_current.txt");
        LocalRepositoryManager manager = newManager();
        Artifact artifact = addTrackedRemoteArtifact(manager);

        assertTrue(Files.exists(trackingFileFor(manager, artifact, "_current.txt")));
        assertFalse(Files.exists(
                trackingFileFor(manager, artifact, EnhancedLocalRepositoryManagerFactory.DEFAULT_TRACKING_FILENAME)));
    }

    @Test
    void trackingFilenameFromLegacyKey() throws Exception {
        // the Resolver 1.9.x name of the key is read as a fallback, like the other enhanced LRM keys
        session.setConfigProperty("aether.enhancedLocalRepository.trackingFilename", "_legacy.txt");
        LocalRepositoryManager manager = newManager();
        Artifact artifact = addTrackedRemoteArtifact(manager);

        assertTrue(Files.exists(trackingFileFor(manager, artifact, "_legacy.txt")));
        assertFalse(Files.exists(
                trackingFileFor(manager, artifact, EnhancedLocalRepositoryManagerFactory.DEFAULT_TRACKING_FILENAME)));
    }

    @Test
    void currentKeyWinsOverLegacyKey() throws Exception {
        session.setConfigProperty(EnhancedLocalRepositoryManagerFactory.CONFIG_PROP_TRACKING_FILENAME, "_current.txt");
        session.setConfigProperty("aether.enhancedLocalRepository.trackingFilename", "_legacy.txt");
        LocalRepositoryManager manager = newManager();
        Artifact artifact = addTrackedRemoteArtifact(manager);

        assertTrue(Files.exists(trackingFileFor(manager, artifact, "_current.txt")));
        assertFalse(Files.exists(trackingFileFor(manager, artifact, "_legacy.txt")));
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

    private static Map<String, Object> properties(final Object... entries) {
        final Map<String, Object> result = new HashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            result.put((String) entries[i], entries[i + 1]);
        }
        return result;
    }

    private static Stream<Arguments> availabilityPolicies() {
        final String ignore = EnhancedLocalRepositoryManagerFactory.CONFIG_PROP_IGNORE_AVAILABILITY;
        final String alias = "aether.enhancedLocalRepository.ignoreAvailability";
        final String split = LocalPathPrefixComposerFactorySupport.CONFIG_PROP_SPLIT;
        final String legacySplit = "aether.enhancedLocalRepository.split";
        final String tail = ChainedLocalRepositoryManager.CONFIG_PROP_IGNORE_TAIL_AVAILABILITY;
        return Stream.of(
                Arguments.of("automatic", properties(), false, true),
                Arguments.of("explicit false", properties(ignore, false), false, false),
                Arguments.of("string false", properties(ignore, "false"), false, false),
                Arguments.of("explicit true", properties(ignore, true), false, true),
                Arguments.of("string true", properties(ignore, "true"), false, true),
                Arguments.of("legacy false", properties(alias, false), false, false),
                Arguments.of("legacy string true", properties(alias, "true"), false, true),
                Arguments.of("current false wins", properties(ignore, false, alias, true), false, false),
                Arguments.of("current true wins", properties(ignore, "true", alias, "false"), false, true),
                Arguments.of("split", properties(split, true), false, false),
                Arguments.of("legacy split", properties(legacySplit, "true"), false, false),
                Arguments.of("current unsplit wins", properties(split, false, legacySplit, true), false, true),
                Arguments.of("current split wins", properties(split, true, legacySplit, false), false, false),
                Arguments.of("explicit split override", properties(split, true, ignore, true), false, true),
                Arguments.of("legacy split override", properties(legacySplit, true, alias, true), false, true),
                Arguments.of("strict tail", properties(tail, false), false, false),
                Arguments.of("string strict tail", properties(tail, "false"), false, false),
                Arguments.of("permissive tail", properties(tail, true), false, true),
                Arguments.of("strict tail override", properties(tail, false, ignore, "true"), false, true),
                Arguments.of("permissive tail override", properties(tail, true, ignore, false), false, false),
                Arguments.of("custom composer", properties(), true, false),
                Arguments.of("custom composer false", properties(ignore, false), true, false),
                Arguments.of("custom composer override", properties(ignore, true), true, true),
                Arguments.of("custom composer legacy override", properties(alias, "true"), true, true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("availabilityPolicies")
    void releaseAvailabilityPolicy(
            final String name,
            final Map<String, Object> properties,
            final boolean customComposer,
            final boolean available)
            throws Exception {
        properties.forEach(session::setConfigProperty);
        session.setConfigProperty(EnhancedLocalRepositoryManagerFactory.CONFIG_PROP_LEGACY_LOCAL_REPOSITORY, false);
        if (customComposer) {
            final DefaultLocalPathPrefixComposerFactory delegate =
                    new DefaultLocalPathPrefixComposerFactory(new DefaultRepositoryKeyFunctionFactory());
            factory = new EnhancedLocalRepositoryManagerFactory(
                    new DefaultLocalPathComposer(),
                    new TrackingFileManagerSupplier().get(),
                    s -> delegate.createComposer(s),
                    new DefaultRepositoryKeyFunctionFactory());
        }
        final LocalRepositoryManager writer = newManager();
        final Artifact artifact = addTrackedRemoteArtifact(writer);
        final Path path = writer.getAbsolutePathForRemoteArtifact(artifact, repository, context);
        final Path tracking = path.getParent().resolve(EnhancedLocalRepositoryManagerFactory.DEFAULT_TRACKING_FILENAME);
        final byte[] provenance = Files.readAllBytes(tracking);
        final RemoteRepository changed =
                new RemoteRepository.Builder("replacement", "default", "https://replacement.invalid/").build();

        // A new session and manager must observe persisted provenance, not a warmed tracking cache.
        session = new DefaultRepositorySystemSession(session);
        final LocalRepositoryManager reader = newManager();
        for (final RemoteRepository requested : Arrays.asList(changed, impostor)) {
            final LocalArtifactResult result = reader.find(
                    session, new LocalArtifactRequest(artifact, Collections.singletonList(requested), context));
            assertEquals(available, result.isAvailable(), name + ": " + requested);
            assertEquals(path, result.getPath());
            assertNull(result.getRepository(), "An unmatched origin must not acquire repository attribution");
        }

        // Even when fallback is enabled, a matching origin later in the request retains attribution.
        final LocalArtifactResult matched =
                reader.find(session, new LocalArtifactRequest(artifact, Arrays.asList(changed, repository), context));
        assertTrue(matched.isAvailable());
        assertEquals(repository, matched.getRepository());
        assertEquals(path, matched.getPath());
        assertArrayEquals(provenance, Files.readAllBytes(tracking), "Lookup must not rewrite provenance");

        session.setConfigProperty(EnhancedLocalRepositoryManagerFactory.CONFIG_PROP_IGNORE_AVAILABILITY, false);
        final LocalRepositoryManager strict = newManager();
        assertFalse(
                strict.find(session, new LocalArtifactRequest(artifact, Collections.singletonList(impostor), context))
                        .isAvailable(),
                "A fallback lookup must not record the requested origin");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.0-SNAPSHOT", "1.0-20260101.123456-1"})
    void snapshotsStillRequireMatchingOrigin(final String version) throws Exception {
        session.setConfigProperty(EnhancedLocalRepositoryManagerFactory.CONFIG_PROP_IGNORE_AVAILABILITY, true);
        session.setConfigProperty(EnhancedLocalRepositoryManagerFactory.CONFIG_PROP_LEGACY_LOCAL_REPOSITORY, false);
        final LocalRepositoryManager writer = newManager();
        final Artifact artifact = new DefaultArtifact("gid:aid:" + version);
        assertTrue(artifact.isSnapshot());
        final Path path = writer.getAbsolutePathForRemoteArtifact(artifact, repository, context);
        Files.createDirectories(path.getParent());
        Files.write(path, "snapshot".getBytes(StandardCharsets.UTF_8));
        writer.add(session, new LocalArtifactRegistration(artifact, repository, Collections.singleton(context)));
        session = new DefaultRepositorySystemSession(session);
        final LocalRepositoryManager reader = newManager();

        final LocalArtifactResult unmatched =
                reader.find(session, new LocalArtifactRequest(artifact, Collections.singletonList(impostor), context));
        assertFalse(unmatched.isAvailable());
        assertEquals(path, unmatched.getPath());
        assertNull(unmatched.getRepository());
        final LocalArtifactResult matched = reader.find(
                session, new LocalArtifactRequest(artifact, Collections.singletonList(repository), context));
        assertTrue(matched.isAvailable());
        assertEquals(repository, matched.getRepository());
    }

    @Test
    void availabilityFallbackDoesNotResurrectMissingFiles() throws Exception {
        final LocalRepositoryManager writer = newManager();
        final Artifact artifact = addTrackedRemoteArtifact(writer);
        Files.delete(writer.getAbsolutePathForRemoteArtifact(artifact, repository, context));
        session = new DefaultRepositorySystemSession(session);
        final LocalArtifactResult result = newManager()
                .find(session, new LocalArtifactRequest(artifact, Collections.singletonList(impostor), context));
        assertFalse(result.isAvailable());
        assertNull(result.getPath());
        assertNull(result.getRepository());
    }

    @Test
    void availabilityFallbackDoesNotBypassRealPathVerification() throws Exception {
        session.setConfigProperty(EnhancedLocalRepositoryManagerFactory.CONFIG_PROP_LEGACY_LOCAL_REPOSITORY, false);
        final LocalRepositoryManager writer = newManager();
        final Artifact artifact = addTrackedRemoteArtifact(writer);
        final Path path = writer.getAbsolutePathForRemoteArtifact(artifact, repository, context);
        final Path target = path.resolveSibling("different-artifact.jar");
        Files.move(path, target);
        try {
            Files.createSymbolicLink(path, target.getFileName());
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "Symbolic links unavailable: " + e);
        }
        session = new DefaultRepositorySystemSession(session);
        final LocalArtifactRequest request =
                new LocalArtifactRequest(artifact, Collections.singletonList(impostor), context);
        final LocalArtifactResult rejected = newManager().find(session, request);
        assertFalse(rejected.isAvailable());
        assertNull(rejected.getPath());

        // Prove rejection was the real-path guard, not absence or origin tracking.
        session.setConfigProperty(EnhancedLocalRepositoryManagerFactory.CONFIG_PROP_VERIFY_REAL_PATH, false);
        final LocalArtifactResult accepted = newManager().find(session, request);
        assertTrue(accepted.isAvailable());
        assertEquals(path, accepted.getPath());
        assertNull(accepted.getRepository());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void metadataRemainsOriginScoped(final boolean ignoreAvailability) throws Exception {
        session.setConfigProperty(
                EnhancedLocalRepositoryManagerFactory.CONFIG_PROP_IGNORE_AVAILABILITY, ignoreAvailability);
        session.setConfigProperty(EnhancedLocalRepositoryManagerFactory.CONFIG_PROP_LEGACY_LOCAL_REPOSITORY, false);
        final LocalRepositoryManager writer = newManager();
        final Metadata metadata =
                new DefaultMetadata("gid", "aid", "1.0", "maven-metadata.xml", Metadata.Nature.RELEASE);
        final Path path = writer.getAbsolutePathForRemoteMetadata(metadata, repository, context);
        Files.createDirectories(path.getParent());
        Files.write(path, "metadata".getBytes(StandardCharsets.UTF_8));
        writer.add(session, new LocalMetadataRegistration(metadata, repository, Collections.singleton(context)));
        final byte[] bytes = Files.readAllBytes(path);
        session = new DefaultRepositorySystemSession(session);
        final LocalRepositoryManager reader = newManager();
        assertEquals(
                path,
                reader.find(session, new LocalMetadataRequest(metadata, repository, context))
                        .getPath());
        assertNull(reader.find(session, new LocalMetadataRequest(metadata, impostor, context))
                .getPath());
        assertNull(reader.find(session, new LocalMetadataRequest(metadata, null, context))
                .getPath());
        assertArrayEquals(bytes, Files.readAllBytes(path));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void chainMembersApplyTheirOwnPolicyWithoutTailBypass(final boolean strict) throws Exception {
        session.setConfigProperty(EnhancedLocalRepositoryManagerFactory.CONFIG_PROP_LEGACY_LOCAL_REPOSITORY, false);
        if (strict) {
            session.setConfigProperty(EnhancedLocalRepositoryManagerFactory.CONFIG_PROP_IGNORE_AVAILABILITY, false);
        }
        final LocalRepositoryManager head = factory.newInstance(session, new LocalRepository(basedir.resolve("head")));
        final LocalRepositoryManager tail = factory.newInstance(session, new LocalRepository(basedir.resolve("tail")));
        final Artifact artifact = addTrackedRemoteArtifact(tail);
        final Path tailPath = tail.getAbsolutePathForRemoteArtifact(artifact, repository, context);
        // Do not set the session's tail-availability property: that would suppress automatic member policy.
        final ChainedLocalRepositoryManager chain =
                new ChainedLocalRepositoryManager(head, Collections.singletonList(tail), false);
        final LocalArtifactRequest request =
                new LocalArtifactRequest(artifact, Collections.singletonList(impostor), context);
        final LocalArtifactResult fromTail = chain.find(session, request);
        assertEquals(!strict, fromTail.isAvailable());
        assertEquals(strict ? null : tailPath, fromTail.getPath());
        assertNull(fromTail.getRepository());

        addTrackedRemoteArtifact(head);
        final LocalArtifactResult fromHead = chain.find(session, request);
        assertEquals(!strict, fromHead.isAvailable());
        assertEquals(
                strict ? null : head.getAbsolutePathForRemoteArtifact(artifact, repository, context),
                fromHead.getPath());
        assertNull(fromHead.getRepository());

        final LocalArtifactResult matched =
                chain.find(session, new LocalArtifactRequest(artifact, Collections.singletonList(repository), context));
        assertTrue(matched.isAvailable());
        assertEquals(head.getAbsolutePathForRemoteArtifact(artifact, repository, context), matched.getPath());
        assertEquals(repository, matched.getRepository());
    }

    @Test
    void chainTailBypassIsIndependentOfExplicitStrictMemberPolicy() throws Exception {
        session.setConfigProperty(EnhancedLocalRepositoryManagerFactory.CONFIG_PROP_IGNORE_AVAILABILITY, false);
        session.setConfigProperty(EnhancedLocalRepositoryManagerFactory.CONFIG_PROP_LEGACY_LOCAL_REPOSITORY, false);
        final LocalRepositoryManager head = factory.newInstance(session, new LocalRepository(basedir.resolve("head")));
        final LocalRepositoryManager tail = factory.newInstance(session, new LocalRepository(basedir.resolve("tail")));
        final Artifact artifact = addTrackedRemoteArtifact(tail);
        final LocalArtifactRequest request =
                new LocalArtifactRequest(artifact, Collections.singletonList(impostor), context);
        assertFalse(tail.find(session, request).isAvailable());
        final ChainedLocalRepositoryManager chain =
                new ChainedLocalRepositoryManager(head, Collections.singletonList(tail), true);
        final LocalArtifactResult result = chain.find(session, request);
        assertTrue(result.isAvailable());
        assertEquals(tail.getAbsolutePathForRemoteArtifact(artifact, repository, context), result.getPath());
        assertNull(result.getRepository());
    }

    @Test
    void chainWritesOnlyToHead() throws Exception {
        final LocalRepositoryManager head = factory.newInstance(session, new LocalRepository(basedir.resolve("head")));
        final LocalRepositoryManager tail = factory.newInstance(session, new LocalRepository(basedir.resolve("tail")));
        final ChainedLocalRepositoryManager chain =
                new ChainedLocalRepositoryManager(head, Collections.singletonList(tail), false);
        final Artifact artifact = addTrackedRemoteArtifact(tail);
        final Path tailPath = tail.getAbsolutePathForRemoteArtifact(artifact, repository, context);
        final Path tracking =
                tailPath.getParent().resolve(EnhancedLocalRepositoryManagerFactory.DEFAULT_TRACKING_FILENAME);
        final byte[] provenance = Files.readAllBytes(tracking);
        final Path headPath = chain.getAbsolutePathForRemoteArtifact(artifact, impostor, context);
        assertEquals(head.getAbsolutePathForRemoteArtifact(artifact, impostor, context), headPath);
        Files.createDirectories(headPath.getParent());
        Files.write(headPath, "head artifact".getBytes(StandardCharsets.UTF_8));
        chain.add(session, new LocalArtifactRegistration(artifact, impostor, Collections.singleton(context)));

        final DefaultRepositorySystemSession strictSession = new DefaultRepositorySystemSession(session);
        strictSession.setConfigProperty(EnhancedLocalRepositoryManagerFactory.CONFIG_PROP_IGNORE_AVAILABILITY, false);
        strictSession.setConfigProperty(
                EnhancedLocalRepositoryManagerFactory.CONFIG_PROP_LEGACY_LOCAL_REPOSITORY, false);
        final LocalArtifactRequest request =
                new LocalArtifactRequest(artifact, Collections.singletonList(impostor), context);
        assertTrue(factory.newInstance(
                        strictSession, new LocalRepository(head.getRepository().getBasePath()))
                .find(strictSession, request)
                .isAvailable());
        assertFalse(factory.newInstance(
                        strictSession, new LocalRepository(tail.getRepository().getBasePath()))
                .find(strictSession, request)
                .isAvailable());
        assertArrayEquals(provenance, Files.readAllBytes(tracking));
        assertEquals("artifact", new String(Files.readAllBytes(tailPath), StandardCharsets.UTF_8));
    }
}
