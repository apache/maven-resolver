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
package org.eclipse.aether.generator.gnupg;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;

import org.bouncycastle.util.encoders.DecoderException;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.generator.gnupg.loaders.*;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.spi.artifact.ArtifactPredicate;
import org.eclipse.aether.spi.artifact.ArtifactPredicateFactory;
import org.eclipse.aether.spi.artifact.generator.ArtifactGenerator;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GpgSignerFactoryTest {
    private GnupgSignatureArtifactGeneratorFactory createFactory() throws Exception {
        Files.createDirectories(Paths.get(System.getProperty("java.io.tmpdir"))); // hack for Surefire

        ArtifactPredicate artifactPredicate = mock(ArtifactPredicate.class);
        when(artifactPredicate.hasChecksums(any(Artifact.class))).thenAnswer(a -> {
            Artifact artifact = (Artifact) a.getArguments()[0];
            return artifact != null
                    && !artifact.getExtension().endsWith(".sha1")
                    && !artifact.getExtension().endsWith(".md5");
        });
        ArtifactPredicateFactory artifactPredicateFactory = mock(ArtifactPredicateFactory.class);
        when(artifactPredicateFactory.newInstance(any(RepositorySystemSession.class)))
                .thenReturn(artifactPredicate);

        // order matters
        LinkedHashMap<String, GnupgSignatureArtifactGeneratorFactory.Loader> loaders = new LinkedHashMap<>();
        loaders.put(GpgEnvLoader.NAME, new GpgEnvLoader());
        loaders.put(GpgConfLoader.NAME, new GpgConfLoader());
        loaders.put(GpgAgentPasswordLoader.NAME, new GpgAgentPasswordLoader());

        return new GnupgSignatureArtifactGeneratorFactory(artifactPredicateFactory, loaders);
    }

    private RepositorySystemSession createSession(Path keyFilePath, String keyPass, boolean interactive) {
        return createSession(keyFilePath, keyPass, null, interactive);
    }

    private RepositorySystemSession createSession(
            Path keyFilePath, String keyPass, String keyFingerprint, boolean interactive) {
        DefaultRepositorySystemSession session = TestUtils.newSession();
        session.setConfigProperty(GnupgConfigurationKeys.CONFIG_PROP_ENABLED, Boolean.TRUE);
        session.setConfigProperty(GnupgConfigurationKeys.CONFIG_PROP_KEY_FILE_PATH, keyFilePath.toString());
        if (keyPass != null) {
            session.setConfigProperty("env." + GnupgConfigurationKeys.RESOLVER_GPG_KEY_PASS, keyPass);
        }
        if (keyFingerprint != null) {
            session.setConfigProperty("env." + GnupgConfigurationKeys.RESOLVER_GPG_KEY_FINGERPRINT, keyFingerprint);
        }
        session.setConfigProperty(ConfigurationProperties.INTERACTIVE, interactive);
        return session;
    }

    @Test
    void doNotSignNonRelevantArtifacts() throws Exception {
        Path keyFile =
                Paths.get("src/test/resources/gpg-signing/gpg-secret.key").toAbsolutePath();
        String keyPass = "TheBigSecret";
        GnupgSignatureArtifactGeneratorFactory factory = createFactory();
        try (ArtifactGenerator signer =
                factory.newInstance(createSession(keyFile, keyPass, false), new DeployRequest())) {
            assertNotNull(signer);

            Collection<? extends Artifact> signatures = signer.generate(Arrays.asList(
                    new DefaultArtifact("org.apache.maven.resolver:test:jar.sha1:1.0"),
                    new DefaultArtifact("org.apache.maven.resolver:test:jar.md5:1.0")));

            assertEquals(0, signatures.size());
        }
    }

    @Test
    void doSignAllRelevantArtifacts() throws Exception {
        Path keyFile =
                Paths.get("src/test/resources/gpg-signing/gpg-secret.key").toAbsolutePath();
        String keyPass = "TheBigSecret";
        GnupgSignatureArtifactGeneratorFactory factory = createFactory();
        try (ArtifactGenerator signer =
                factory.newInstance(createSession(keyFile, keyPass, false), new DeployRequest())) {
            assertNotNull(signer);
            Path irrelevant = Paths.get("src/test/resources/gpg-signing/artifact.txt");

            Collection<? extends Artifact> signatures = signer.generate(Arrays.asList(
                    new DefaultArtifact("org.apache.maven.resolver:test:jar:1.0").setPath(irrelevant),
                    new DefaultArtifact("org.apache.maven.resolver:test:jar.sha1:1.0").setPath(irrelevant),
                    new DefaultArtifact("org.apache.maven.resolver:test:jar:source:1.0").setPath(irrelevant),
                    new DefaultArtifact("org.apache.maven.resolver:test:jar.md5:source:1.0").setPath(irrelevant),
                    new DefaultArtifact("org.apache.maven.resolver:test:foo:1.0").setPath(irrelevant)));

            assertEquals(3, signatures.size());

            assertTrue(signatures.stream()
                    .anyMatch(a -> "".equals(a.getClassifier()) && "jar.asc".equals(a.getExtension())));
            assertTrue(signatures.stream()
                    .anyMatch(a -> "source".equals(a.getClassifier()) && "jar.asc".equals(a.getExtension())));
            assertTrue(signatures.stream()
                    .anyMatch(a -> "".equals(a.getClassifier()) && "foo.asc".equals(a.getExtension())));
        }
    }

    @Test
    void doNotSignIfGpgSignatureAlreadyPresent() throws Exception {
        Path keyFile =
                Paths.get("src/test/resources/gpg-signing/gpg-secret.key").toAbsolutePath();
        String keyPass = "TheBigSecret";
        GnupgSignatureArtifactGeneratorFactory factory = createFactory();
        try (ArtifactGenerator signer =
                factory.newInstance(createSession(keyFile, keyPass, false), new DeployRequest())) {
            assertNotNull(signer);

            Collection<? extends Artifact> signatures = signer.generate(Arrays.asList(
                    new DefaultArtifact("org.apache.maven.resolver:test:jar:1.0"),
                    new DefaultArtifact("org.apache.maven.resolver:test:jar.asc:1.0")));

            assertEquals(0, signatures.size());
        }
    }

    @Test
    void signNonInteractive() throws Exception {
        Path keyFile =
                Paths.get("src/test/resources/gpg-signing/gpg-secret.key").toAbsolutePath();
        String keyPass = "TheBigSecret";
        GnupgSignatureArtifactGeneratorFactory factory = createFactory();
        try (ArtifactGenerator signer =
                factory.newInstance(createSession(keyFile, keyPass, false), new DeployRequest())) {
            assertNotNull(signer);
            Path artifactPath = Paths.get("src/test/resources/gpg-signing/artifact.txt");
            Collection<? extends Artifact> signatures = signer.generate(Collections.singleton(
                    new DefaultArtifact("org.apache.maven.resolver:test:1.0").setPath(artifactPath)));

            // one signature expected for one relevant artifact
            assertEquals(1, signatures.size());
            Path signaturePath = signatures.iterator().next().getPath();

            // cannot assert file size due OS differences, so just count the lines instead: those should be same
            assertEquals(8, Files.lines(signaturePath).count());

            // TODO: validate the signature
        }
    }

    @Test
    void signNonInteractiveWithSelectedKey() throws Exception {
        Path keyFile =
                Paths.get("src/test/resources/gpg-signing/gpg-secret.key").toAbsolutePath();
        String keyPass = "TheBigSecret";
        GnupgSignatureArtifactGeneratorFactory factory = createFactory();
        try (ArtifactGenerator signer = factory.newInstance(
                createSession(keyFile, keyPass, "6D27BDA430672EC700BA7DBD0A32C01AE8785B6E", false),
                new DeployRequest())) {
            assertNotNull(signer);
            Path artifactPath = Paths.get("src/test/resources/gpg-signing/artifact.txt");
            Collection<? extends Artifact> signatures = signer.generate(Collections.singleton(
                    new DefaultArtifact("org.apache.maven.resolver:test:1.0").setPath(artifactPath)));

            // one signature expected for one relevant artifact
            assertEquals(1, signatures.size());
            Path signaturePath = signatures.iterator().next().getPath();

            // cannot assert file size due OS differences, so just count the lines instead: those should be same
            assertEquals(8, Files.lines(signaturePath).count());

            // TODO: validate the signature
        }
    }

    @Test
    void signNonInteractiveWithSelectedKeyWrongFingerprint() throws Exception {
        Path keyFile =
                Paths.get("src/test/resources/gpg-signing/gpg-secret.key").toAbsolutePath();
        String keyPass = "TheBigSecret";
        GnupgSignatureArtifactGeneratorFactory factory = createFactory();

        assertThrows(
                IllegalArgumentException.class,
                () -> factory.newInstance(
                        createSession(keyFile, keyPass, "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF", false),
                        new DeployRequest()));
    }

    @Test
    void signNonInteractiveWithSelectedKeyMalformedFingerprint1() throws Exception {
        Path keyFile =
                Paths.get("src/test/resources/gpg-signing/gpg-secret.key").toAbsolutePath();
        String keyPass = "TheBigSecret";
        GnupgSignatureArtifactGeneratorFactory factory = createFactory();

        assertThrows(
                IllegalArgumentException.class,
                () -> factory.newInstance(createSession(keyFile, keyPass, "abcd", false), new DeployRequest()));
    }

    @Test
    void signNonInteractiveWithSelectedKeyMalformedFingerprint2() throws Exception {
        Path keyFile =
                Paths.get("src/test/resources/gpg-signing/gpg-secret.key").toAbsolutePath();
        String keyPass = "TheBigSecret";
        GnupgSignatureArtifactGeneratorFactory factory = createFactory();

        assertThrows(
                DecoderException.class,
                () -> factory.newInstance(
                        createSession(keyFile, keyPass, "ZAZUFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF", false),
                        new DeployRequest()));
    }

    /**
     * This test is disabled by default as it is interactive and would use Gpg Agent.
     * <p>
     * To try it out: remove the {@code Disabled} annotation and run it from IDE. When agent asks for passkey,
     * it will be "TheBigSecret". Subsequent runs will NOT ASK ANYTHING as agent will cache the passkey.
     */
    @Test
    @Disabled
    void signInteractive() throws Exception {
        Path keyFile =
                Paths.get("src/test/resources/gpg-signing/gpg-secret.key").toAbsolutePath();
        GnupgSignatureArtifactGeneratorFactory factory = createFactory();
        try (ArtifactGenerator signer = factory.newInstance(createSession(keyFile, null, true), new DeployRequest())) {
            assertNotNull(signer);
            Path artifactPath = Paths.get("src/test/resources/gpg-signing/artifact.txt");
            Collection<? extends Artifact> signatures = signer.generate(Collections.singleton(
                    new DefaultArtifact("org.apache.maven.resolver:test:1.0").setPath(artifactPath)));

            // one signature expected for one relevant artifact
            assertEquals(1, signatures.size());
            Path signaturePath = signatures.iterator().next().getPath();

            // cannot assert file size due OS differences, so just count the lines instead: those should be same
            assertEquals(8, Files.lines(signaturePath).count());

            // TODO: validate the signature
        }
    }
}
