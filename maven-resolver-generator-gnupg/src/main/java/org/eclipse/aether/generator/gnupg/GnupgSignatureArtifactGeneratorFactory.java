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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.openpgp.*;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyDecryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.spi.artifact.ArtifactPredicateFactory;
import org.eclipse.aether.spi.artifact.generator.ArtifactGenerator;
import org.eclipse.aether.spi.artifact.generator.ArtifactGeneratorFactory;
import org.eclipse.aether.util.ConfigUtils;

@Singleton
@Named(GnupgSignatureArtifactGeneratorFactory.NAME)
public final class GnupgSignatureArtifactGeneratorFactory implements ArtifactGeneratorFactory {

    public interface Loader {
        /**
         * Returns {@code true} if this loader requires user interactivity.
         */
        boolean isInteractive();

        /**
         * Returns the key ring material, or {@code null}.
         */
        default byte[] loadKeyRingMaterial(RepositorySystemSession session) throws IOException {
            return null;
        }

        /**
         * Returns the key fingerprint, or {@code null}.
         */
        default byte[] loadKeyFingerprint(RepositorySystemSession session) throws IOException {
            return null;
        }

        /**
         * Returns the key password, or {@code null}.
         */
        default char[] loadPassword(RepositorySystemSession session, long keyId) throws IOException {
            return null;
        }
    }

    public static final String NAME = "gnupg";

    private final ArtifactPredicateFactory artifactPredicateFactory;
    private final Map<String, Loader> loaders;

    @Inject
    public GnupgSignatureArtifactGeneratorFactory(
            ArtifactPredicateFactory artifactPredicateFactory, Map<String, Loader> loaders) {
        this.artifactPredicateFactory = artifactPredicateFactory;
        this.loaders = loaders;
    }

    @Override
    public ArtifactGenerator newInstance(RepositorySystemSession session, InstallRequest request) {
        return null;
    }

    @Override
    public ArtifactGenerator newInstance(RepositorySystemSession session, DeployRequest request) {
        final boolean enabled = ConfigUtils.getBoolean(
                session, GnupgConfigurationKeys.DEFAULT_ENABLED, GnupgConfigurationKeys.CONFIG_PROP_ENABLED);
        if (!enabled) {
            return null;
        }

        try {
            return doCreateArtifactGenerator(
                    session, request.getArtifacts(), artifactPredicateFactory.newInstance(session)::hasChecksums);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public float getPriority() {
        return 100;
    }

    private GnupgSignatureArtifactGenerator doCreateArtifactGenerator(
            RepositorySystemSession session, Collection<Artifact> artifacts, Predicate<Artifact> artifactPredicate)
            throws IOException {
        boolean interactive = ConfigUtils.getBoolean(
                session, ConfigurationProperties.DEFAULT_INTERACTIVE, ConfigurationProperties.INTERACTIVE);
        List<Loader> loaders = this.loaders.values().stream()
                .filter(l -> interactive || !l.isInteractive())
                .toList();

        byte[] keyRingMaterial = null;
        for (Loader loader : loaders) {
            keyRingMaterial = loader.loadKeyRingMaterial(session);
            if (keyRingMaterial != null) {
                break;
            }
        }
        if (keyRingMaterial == null) {
            throw new IllegalArgumentException("Key ring material not found");
        }

        byte[] fingerprint = null;
        for (Loader loader : loaders) {
            fingerprint = loader.loadKeyFingerprint(session);
            if (fingerprint != null) {
                break;
            }
        }

        try {
            PGPSecretKeyRingCollection pgpSecretKeyRingCollection = new PGPSecretKeyRingCollection(
                    PGPUtil.getDecoderStream(new ByteArrayInputStream(keyRingMaterial)),
                    new BcKeyFingerprintCalculator());

            PGPSecretKey secretKey = null;
            for (PGPSecretKeyRing ring : pgpSecretKeyRingCollection) {
                for (PGPSecretKey key : ring) {
                    if (!key.isPrivateKeyEmpty()) {
                        if (fingerprint == null || Arrays.equals(fingerprint, key.getFingerprint())) {
                            secretKey = key;
                            break;
                        }
                    }
                }
            }
            if (secretKey == null) {
                throw new IllegalArgumentException("Secret key not found");
            }
            if (secretKey.isPrivateKeyEmpty()) {
                throw new IllegalArgumentException("Private key not found in Secret key");
            }

            long validSeconds = secretKey.getPublicKey().getValidSeconds();
            if (validSeconds > 0) {
                LocalDateTime expireDateTime = secretKey
                        .getPublicKey()
                        .getCreationTime()
                        .toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime()
                        .plusSeconds(validSeconds);
                if (LocalDateTime.now().isAfter(expireDateTime)) {
                    throw new IllegalArgumentException("Secret key expired at: " + expireDateTime);
                }
            }

            char[] keyPassword = null;
            final boolean keyPassNeeded = secretKey.getKeyEncryptionAlgorithm() != SymmetricKeyAlgorithmTags.NULL;
            if (keyPassNeeded) {
                for (Loader loader : loaders) {
                    keyPassword = loader.loadPassword(session, secretKey.getKeyID());
                    if (keyPassword != null) {
                        break;
                    }
                }
                if (keyPassword == null) {
                    throw new IllegalArgumentException("Secret key is encrypted but no key password provided");
                }
            }

            PGPPrivateKey privateKey = secretKey.extractPrivateKey(
                    new BcPBESecretKeyDecryptorBuilder(new BcPGPDigestCalculatorProvider()).build(keyPassword));
            PGPSignatureSubpacketGenerator subPacketGenerator = new PGPSignatureSubpacketGenerator();
            subPacketGenerator.setIssuerFingerprint(false, secretKey);
            PGPSignatureSubpacketVector hashSubPackets = subPacketGenerator.generate();

            return new GnupgSignatureArtifactGenerator(
                    artifacts, artifactPredicate, secretKey, privateKey, hashSubPackets);
        } catch (PGPException | IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
