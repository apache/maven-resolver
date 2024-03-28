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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Predicate;

import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.BCPGOutputStream;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.PGPSignatureSubpacketVector;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentSignerBuilder;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.spi.artifact.generator.ArtifactGenerator;
import org.eclipse.aether.util.artifact.SubArtifact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class GnupgSignatureArtifactGenerator implements ArtifactGenerator {
    private static final String ARTIFACT_EXTENSION = ".asc";
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ArrayList<Artifact> artifacts;
    private final Predicate<Artifact> signableArtifactPredicate;
    private final PGPSecretKey secretKey;
    private final PGPPrivateKey privateKey;
    private final PGPSignatureSubpacketVector hashSubPackets;
    private final String keyInfo;
    private final ArrayList<Path> signatureTempFiles;

    GnupgSignatureArtifactGenerator(
            Collection<Artifact> artifacts,
            Predicate<Artifact> signableArtifactPredicate,
            PGPSecretKey secretKey,
            PGPPrivateKey privateKey,
            PGPSignatureSubpacketVector hashSubPackets,
            String keyInfo) {
        this.artifacts = new ArrayList<>(artifacts);
        this.signableArtifactPredicate = signableArtifactPredicate;
        this.secretKey = secretKey;
        this.privateKey = privateKey;
        this.hashSubPackets = hashSubPackets;
        this.keyInfo = keyInfo;
        this.signatureTempFiles = new ArrayList<>();
        logger.debug("Created generator using key {}", keyInfo);
    }

    @Override
    public String generatorId() {
        return GnupgSignatureArtifactGeneratorFactory.NAME;
    }

    @Override
    public Collection<? extends Artifact> generate(Collection<? extends Artifact> generatedArtifacts) {
        try {
            artifacts.addAll(generatedArtifacts);

            // back out if PGP signatures found among artifacts
            if (artifacts.stream().anyMatch(a -> a.getExtension().endsWith(ARTIFACT_EXTENSION))) {
                logger.debug("GPG signatures are present among artifacts, bailing out");
                return Collections.emptyList();
            }

            // sign relevant artifacts
            ArrayList<Artifact> result = new ArrayList<>();
            for (Artifact artifact : artifacts) {
                if (signableArtifactPredicate.test(artifact)) {
                    Path signatureTempFile = Files.createTempFile("signer-pgp", "tmp");
                    signatureTempFiles.add(signatureTempFile);
                    try (InputStream artifactContent = Files.newInputStream(artifact.getPath());
                            OutputStream signatureContent = Files.newOutputStream(signatureTempFile)) {
                        sign(artifactContent, signatureContent);
                    }
                    result.add(new SubArtifact(
                            artifact,
                            artifact.getClassifier(),
                            artifact.getExtension() + ARTIFACT_EXTENSION,
                            signatureTempFile.toFile()));
                }
            }
            logger.debug("Signed {} artifacts with key {}", result.size(), keyInfo);
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
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

    private void sign(InputStream content, OutputStream signature) throws IOException {
        PGPSignatureGenerator sGen = new PGPSignatureGenerator(
                new BcPGPContentSignerBuilder(secretKey.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA512));
        try {
            sGen.init(PGPSignature.BINARY_DOCUMENT, privateKey);
            sGen.setHashedSubpackets(hashSubPackets);
            int len;
            byte[] buffer = new byte[8 * 1024];
            while ((len = content.read(buffer)) >= 0) {
                sGen.update(buffer, 0, len);
            }
            try (BCPGOutputStream bcpgOutputStream = new BCPGOutputStream(new ArmoredOutputStream(signature))) {
                sGen.generate().encode(bcpgOutputStream);
            }
        } catch (PGPException e) {
            throw new IllegalStateException(e);
        }
    }
}
