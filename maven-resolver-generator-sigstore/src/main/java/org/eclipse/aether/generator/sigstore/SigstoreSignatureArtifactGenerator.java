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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Predicate;

import dev.sigstore.KeylessSigner;
import dev.sigstore.KeylessSignerException;
import dev.sigstore.bundle.Bundle;
import dev.sigstore.encryption.certificates.Certificates;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.generator.sigstore.internal.FulcioOidHelper;
import org.eclipse.aether.spi.artifact.generator.ArtifactGenerator;
import org.eclipse.aether.util.FileUtils;
import org.eclipse.aether.util.artifact.SubArtifact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class SigstoreSignatureArtifactGenerator implements ArtifactGenerator {
    private static final String ARTIFACT_EXTENSION = ".sigstore.json";
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ArrayList<Artifact> artifacts;
    private final Predicate<Artifact> signableArtifactPredicate;
    private final boolean publicStaging;
    private final ArrayList<Path> signatureTempFiles;

    SigstoreSignatureArtifactGenerator(
            Collection<Artifact> artifacts, Predicate<Artifact> signableArtifactPredicate, boolean publicStaging) {
        this.artifacts = new ArrayList<>(artifacts);
        this.signableArtifactPredicate = signableArtifactPredicate;
        this.publicStaging = publicStaging;
        this.signatureTempFiles = new ArrayList<>();
        logger.debug("Created sigstore generator (publicStaging={})", publicStaging);
    }

    @Override
    public String generatorId() {
        return SigstoreSignatureArtifactGeneratorFactory.NAME;
    }

    @Override
    public Collection<? extends Artifact> generate(Collection<? extends Artifact> generatedArtifacts) {
        try {
            artifacts.addAll(generatedArtifacts);

            // back out if Sigstore signatures found among artifacts
            if (artifacts.stream().anyMatch(a -> a.getExtension().endsWith(ARTIFACT_EXTENSION))) {
                logger.debug("Sigstore signatures are present among artifacts, bailing out");
                return Collections.emptyList();
            }

            // sign relevant artifacts
            ArrayList<Artifact> result = new ArrayList<>();
            try (KeylessSigner signer = publicStaging
                    ? KeylessSigner.builder().sigstoreStagingDefaults().build()
                    : KeylessSigner.builder().sigstorePublicDefaults().build()) {
                for (Artifact artifact : artifacts) {
                    if (signableArtifactPredicate.test(artifact)) {
                        Path fileToSign = artifact.getPath();
                        Path signatureTempFile = Files.createTempFile("signer-sigstore", "tmp");
                        signatureTempFiles.add(signatureTempFile);

                        logger.debug("Signing " + artifact);
                        long start = System.currentTimeMillis();
                        Bundle bundle = signer.signFile(fileToSign);

                        X509Certificate cert = (X509Certificate)
                                bundle.getCertPath().getCertificates().get(0);
                        long durationMinutes = Certificates.validity(cert, ChronoUnit.MINUTES);

                        logger.debug("  Fulcio certificate (valid for "
                                + durationMinutes
                                + " m) obtained for "
                                + cert.getSubjectAlternativeNames()
                                        .iterator()
                                        .next()
                                        .get(1)
                                + " (by "
                                + FulcioOidHelper.getIssuerV2(cert)
                                + " IdP)");

                        FileUtils.writeFile(signatureTempFile, p -> Files.writeString(p, bundle.toJson()));

                        long duration = System.currentTimeMillis() - start;
                        logger.debug("  > Rekor entry "
                                + bundle.getEntries().get(0).getLogIndex()
                                + " obtained in "
                                + duration
                                + " ms, saved to "
                                + signatureTempFile);

                        result.add(new SubArtifact(
                                artifact,
                                artifact.getClassifier(),
                                artifact.getExtension() + ARTIFACT_EXTENSION,
                                signatureTempFile.toFile()));
                    }
                }
            }
            logger.info("Signed {} artifacts with Sigstore", result.size());
            return result;
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Preparation problem", e);
        } catch (KeylessSignerException e) {
            throw new IllegalStateException("Processing problem", e);
        } catch (IOException e) {
            throw new UncheckedIOException("IO problem", e);
        }
    }

    @Override
    public void close() {
        signatureTempFiles.forEach(p -> {
            try {
                Files.deleteIfExists(p);
            } catch (IOException e) {
                p.toFile().deleteOnExit();
            }
        });
    }
}
