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
package org.eclipse.aether.generator.sigstore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.spi.artifact.ArtifactPredicate;
import org.eclipse.aether.spi.artifact.ArtifactPredicateFactory;
import org.eclipse.aether.spi.artifact.generator.ArtifactGenerator;
import org.eclipse.aether.spi.io.PathProcessorSupport;
import org.eclipse.aether.util.artifact.ArtifactIdUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SigstoreSignerFactoryTest {
    private SigstoreSignatureArtifactGeneratorFactory createFactory() throws Exception {
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

        return new SigstoreSignatureArtifactGeneratorFactory(artifactPredicateFactory, new PathProcessorSupport());
    }

    private RepositorySystemSession createSession() {
        DefaultRepositorySystemSession session = TestUtils.newSession();
        session.setConfigProperty(SigstoreConfigurationKeys.CONFIG_PROP_ENABLED, Boolean.TRUE);
        session.setConfigProperty(SigstoreConfigurationKeys.CONFIG_PROP_PUBLIC_STAGING, Boolean.TRUE);
        return session;
    }

    @Test
    void doNotSignNonRelevantArtifacts() throws Exception {
        SigstoreSignatureArtifactGeneratorFactory factory = createFactory();
        try (ArtifactGenerator signer = factory.newInstance(createSession(), new DeployRequest())) {
            assertNotNull(signer);

            Collection<? extends Artifact> signatures = signer.generate(Arrays.asList(
                    new DefaultArtifact("org.apache.maven.resolver:test:jar.sha1:1.0"),
                    new DefaultArtifact("org.apache.maven.resolver:test:jar.md5:1.0")));

            assertEquals(0, signatures.size());
        }
    }

    @Disabled("needs login! Remove this and run it to try out")
    @Test
    void doSignAllRelevantArtifacts() throws Exception {
        SigstoreSignatureArtifactGeneratorFactory factory = createFactory();
        try (ArtifactGenerator signer = factory.newInstance(createSession(), new DeployRequest())) {
            assertNotNull(signer);
            Path irrelevant = Paths.get("src/test/resources/artifact.txt");

            Collection<? extends Artifact> signatures = signer.generate(Arrays.asList(
                    new DefaultArtifact("org.apache.maven.resolver:test:jar:1.0").setPath(irrelevant),
                    new DefaultArtifact("org.apache.maven.resolver:test:jar.sha1:1.0").setPath(irrelevant),
                    new DefaultArtifact("org.apache.maven.resolver:test:jar:source:1.0").setPath(irrelevant),
                    new DefaultArtifact("org.apache.maven.resolver:test:jar.md5:source:1.0").setPath(irrelevant),
                    new DefaultArtifact("org.apache.maven.resolver:test:foo:1.0").setPath(irrelevant)));

            assertEquals(3, signatures.size());

            assertTrue(signatures.stream()
                    .anyMatch(a -> "".equals(a.getClassifier()) && "jar.sigstore.json".equals(a.getExtension())));
            assertTrue(signatures.stream()
                    .anyMatch(a -> "source".equals(a.getClassifier()) && "jar.sigstore.json".equals(a.getExtension())));
            assertTrue(signatures.stream()
                    .anyMatch(a -> "".equals(a.getClassifier()) && "foo.sigstore.json".equals(a.getExtension())));
        }
    }

    @Test
    void doNotSignIfSignatureAlreadyPresent() throws Exception {
        SigstoreSignatureArtifactGeneratorFactory factory = createFactory();
        try (ArtifactGenerator signer = factory.newInstance(createSession(), new DeployRequest())) {
            assertNotNull(signer);

            Collection<? extends Artifact> signatures = signer.generate(Arrays.asList(
                    new DefaultArtifact("org.apache.maven.resolver:test:jar:1.0"),
                    new DefaultArtifact("org.apache.maven.resolver:test:jar.sigstore.json:1.0")));

            assertEquals(0, signatures.size());
        }
    }

    @Disabled("needs login! Remove this and run it to try out")
    @Test
    void doSign() throws Exception {
        SigstoreSignatureArtifactGeneratorFactory factory = createFactory();
        try (ArtifactGenerator signer = factory.newInstance(createSession(), new DeployRequest())) {
            assertNotNull(signer);
            Path artifactPath = Paths.get("src/test/resources/artifact.txt");
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
