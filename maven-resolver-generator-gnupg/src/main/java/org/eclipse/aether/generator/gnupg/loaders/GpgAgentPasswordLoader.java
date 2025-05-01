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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.SocketException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.bouncycastle.util.encoders.Hex;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.generator.gnupg.GnupgSignatureArtifactGeneratorFactory;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.sisu.Priority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.eclipse.aether.generator.gnupg.GnupgConfigurationKeys.CONFIG_PROP_AGENT_SOCKET_LOCATIONS;
import static org.eclipse.aether.generator.gnupg.GnupgConfigurationKeys.CONFIG_PROP_USE_AGENT;
import static org.eclipse.aether.generator.gnupg.GnupgConfigurationKeys.DEFAULT_AGENT_SOCKET_LOCATIONS;
import static org.eclipse.aether.generator.gnupg.GnupgConfigurationKeys.DEFAULT_USE_AGENT;

/**
 * Password loader that uses GnuPG Agent. Is interactive.
 */
@Singleton
@Named(GpgAgentPasswordLoader.NAME)
@Priority(10)
public final class GpgAgentPasswordLoader implements GnupgSignatureArtifactGeneratorFactory.Loader {
    public static final String NAME = "agent";
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public char[] loadPassword(RepositorySystemSession session, byte[] fingerprint) throws IOException {
        if (!ConfigUtils.getBoolean(session, DEFAULT_USE_AGENT, CONFIG_PROP_USE_AGENT)) {
            return null;
        }
        String socketLocationsStr =
                ConfigUtils.getString(session, DEFAULT_AGENT_SOCKET_LOCATIONS, CONFIG_PROP_AGENT_SOCKET_LOCATIONS);
        boolean interactive = ConfigUtils.getBoolean(
                session, ConfigurationProperties.DEFAULT_INTERACTIVE, ConfigurationProperties.INTERACTIVE);
        List<String> socketLocations = Arrays.stream(socketLocationsStr.split(","))
                .filter(s -> s != null && !s.isEmpty())
                .collect(Collectors.toList());
        for (String socketLocation : socketLocations) {
            try {
                Path socketLocationPath = Paths.get(socketLocation);
                if (!socketLocationPath.isAbsolute()) {
                    socketLocationPath = Paths.get(System.getProperty("user.home"))
                            .resolve(socketLocationPath)
                            .toAbsolutePath();
                }
                return load(fingerprint, socketLocationPath, interactive);
            } catch (SocketException e) {
                // try next location
                logger.debug("Problem communicating with agent on socket: {}", socketLocation, e);
            }
        }
        logger.warn("Could not connect to agent on any of the configured sockets: {}", socketLocations);
        return null;
    }

    private char[] load(byte[] fingerprint, Path socketPath, boolean interactive) throws IOException {
        try (SocketChannel sock = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            sock.connect(UnixDomainSocketAddress.of(socketPath));
            try (BufferedReader in = new BufferedReader(new InputStreamReader(Channels.newInputStream(sock)));
                    OutputStream os = Channels.newOutputStream(sock)) {

                expectOK(in);
                String display = System.getenv("DISPLAY");
                if (display != null) {
                    os.write(("OPTION display=" + display + "\n").getBytes());
                    os.flush();
                    expectOK(in);
                }
                String term = System.getenv("TERM");
                if (term != null) {
                    os.write(("OPTION ttytype=" + term + "\n").getBytes());
                    os.flush();
                    expectOK(in);
                }
                String hexKeyFingerprint = Hex.toHexString(fingerprint);
                String displayFingerprint = hexKeyFingerprint.toUpperCase(Locale.ROOT);
                // https://unix.stackexchange.com/questions/71135/how-can-i-find-out-what-keys-gpg-agent-has-cached-like-how-ssh-add-l-shows-yo
                String instruction = "GET_PASSPHRASE "
                        + (!interactive ? "--no-ask " : "")
                        + hexKeyFingerprint
                        + " "
                        + "X "
                        + "GnuPG+Passphrase "
                        + "Please+enter+the+passphrase+to+unlock+the+OpenPGP+secret+key+with+fingerprint:+"
                        + displayFingerprint
                        + "+to+use+it+for+signing+Maven+Artifacts\n";
                os.write((instruction).getBytes());
                os.flush();
                return mayExpectOK(in);
            }
        }
    }

    private void expectOK(BufferedReader in) throws IOException {
        String response = in.readLine();
        if (!response.startsWith("OK")) {
            throw new IOException("Expected OK but got this instead: " + response);
        }
    }

    private char[] mayExpectOK(BufferedReader in) throws IOException {
        String response = in.readLine();
        if (response.startsWith("ERR")) {
            return null;
        } else if (!response.startsWith("OK")) {
            throw new IOException("Expected OK/ERR but got this instead: " + response);
        }
        return new String(Hex.decode(
                        response.substring(Math.min(response.length(), 3)).trim()))
                .toCharArray();
    }
}
