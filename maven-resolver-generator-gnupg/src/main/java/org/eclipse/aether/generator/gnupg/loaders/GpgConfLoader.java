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
package org.eclipse.aether.generator.gnupg.loaders;

import javax.inject.Named;
import javax.inject.Singleton;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.bouncycastle.util.encoders.Hex;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.generator.gnupg.GnupgConfigurationKeys;
import org.eclipse.aether.generator.gnupg.GnupgSignatureArtifactGeneratorFactory;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.sisu.Priority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.eclipse.aether.generator.gnupg.GnupgConfigurationKeys.CONFIG_PROP_KEY_FINGERPRINT;

/**
 * Loader that looks for configuration.
 */
@Singleton
@Named(GpgConfLoader.NAME)
@Priority(20)
@SuppressWarnings("checkstyle:magicnumber")
public final class GpgConfLoader implements GnupgSignatureArtifactGeneratorFactory.Loader {
    public static final String NAME = "conf";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * Maximum file size allowed to load (as we load it into heap).
     * <p>
     * This barrier exists to prevent us to load big/huge files, if this code is pointed at one
     * (by mistake or by malicious intent).
     *
     * @see <a href="https://wiki.gnupg.org/LargeKeys">Large Keys</a>
     */
    private static final long MAX_SIZE = 64 * 1024 + 1L;

    @Override
    public byte[] loadKeyRingMaterial(RepositorySystemSession session) throws IOException {
        Path keyPath = Paths.get(ConfigUtils.getString(
                session,
                GnupgConfigurationKeys.DEFAULT_KEY_FILE_PATH,
                GnupgConfigurationKeys.CONFIG_PROP_KEY_FILE_PATH));
        if (!keyPath.isAbsolute()) {
            keyPath =
                    Paths.get(System.getProperty("user.home")).resolve(keyPath).toAbsolutePath();
        }
        if (Files.isRegularFile(keyPath)) {
            if (Files.size(keyPath) < MAX_SIZE) {
                return Files.readAllBytes(keyPath);
            } else {
                logger.warn("Refusing to load file {}; is larger than 64KB", keyPath);
            }
        }
        return null;
    }

    @Override
    public byte[] loadKeyFingerprint(RepositorySystemSession session) {
        String keyFingerprint = ConfigUtils.getString(session, null, CONFIG_PROP_KEY_FINGERPRINT);
        if (keyFingerprint != null) {
            if (keyFingerprint.trim().length() == 40) {
                return Hex.decode(keyFingerprint);
            } else {
                throw new IllegalArgumentException(
                        "Key fingerprint configuration is wrong (hex encoded, 40 characters)");
            }
        }
        return null;
    }
}
