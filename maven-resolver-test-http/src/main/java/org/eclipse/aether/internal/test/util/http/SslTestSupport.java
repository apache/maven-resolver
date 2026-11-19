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
package org.eclipse.aether.internal.test.util.http;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.KeyStore;
import java.util.Enumeration;

/**
 * Key and trust material shared by {@link HttpTransporterTest} and {@link HttpServer}: the stores are copied from the
 * test resources into the build directory, where the server reads them, and the client side gets an
 * {@link SSLContext} that trusts them.
 */
public final class SslTestSupport {
    public static final Path SERVER_STORE_PATH = Paths.get("target/server-store");
    public static final Path CLIENT_STORE_PATH = Paths.get("target/client-store");
    public static final Path PEM_QUICHE_SERVER_PATH = Paths.get("target/pems");

    private SslTestSupport() {}

    /**
     * Copies the server and client stores from the test resources to {@link #SERVER_STORE_PATH} and
     * {@link #CLIENT_STORE_PATH}, and creates {@link #PEM_QUICHE_SERVER_PATH}.
     */
    public static void installKeyStores() {
        try {
            try (InputStream keyStoreStream =
                            SslTestSupport.class.getClassLoader().getResourceAsStream("ssl/server-store");
                    InputStream trustStoreStream =
                            SslTestSupport.class.getClassLoader().getResourceAsStream("ssl/client-store"); ) {
                Files.copy(keyStoreStream, SERVER_STORE_PATH, StandardCopyOption.REPLACE_EXISTING);
                Files.copy(trustStoreStream, CLIENT_STORE_PATH, StandardCopyOption.REPLACE_EXISTING);
                Files.createDirectories(PEM_QUICHE_SERVER_PATH);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Creates an {@link SSLContext} for the client that extends the default keystore and truststore with the entries
     * from {@link #SERVER_STORE_PATH} (password {@code "server-pwd"}) and {@link #CLIENT_STORE_PATH}
     * (password {@code "client-pwd"}).
     *
     * @return an {@link SSLContext} combining default and custom key/trust material
     */
    public static SSLContext createClientSSLContext() {
        try {
            // Load custom key store (KEY_STORE_PATH acts as truststore in "cross connected" setup)
            KeyStore customTrustStore = KeyStore.getInstance("pkcs12");
            try (InputStream is = Files.newInputStream(SERVER_STORE_PATH)) {
                customTrustStore.load(is, "server-pwd".toCharArray());
            }

            // Load custom trust store (TRUST_STORE_PATH acts as keystore in "cross connected" setup)
            KeyStore customKeyStore = KeyStore.getInstance("pkcs12");
            try (InputStream is = Files.newInputStream(CLIENT_STORE_PATH)) {
                customKeyStore.load(is, "client-pwd".toCharArray());
            }

            // Load default truststore and merge custom entries
            KeyStore defaultTrustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            Path defaultTrustStorePath = Path.of(System.getProperty("java.home"), "lib", "security", "cacerts");
            if (Files.exists(defaultTrustStorePath)) {
                try (InputStream is = Files.newInputStream(defaultTrustStorePath)) {
                    defaultTrustStore.load(is, "changeit".toCharArray());
                }
            } else {
                defaultTrustStore.load(null, null);
            }
            Enumeration<String> aliases = customTrustStore.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                defaultTrustStore.setCertificateEntry("custom-trust-" + alias, customTrustStore.getCertificate(alias));
            }

            // Load default keystore and merge custom entries
            KeyStore mergedKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            mergedKeyStore.load(null, null);
            Enumeration<String> keyAliases = customKeyStore.aliases();
            while (keyAliases.hasMoreElements()) {
                String alias = keyAliases.nextElement();
                if (customKeyStore.isKeyEntry(alias)) {
                    mergedKeyStore.setKeyEntry(
                            alias,
                            customKeyStore.getKey(alias, "client-pwd".toCharArray()),
                            "client-pwd".toCharArray(),
                            customKeyStore.getCertificateChain(alias));
                } else {
                    mergedKeyStore.setCertificateEntry(alias, customKeyStore.getCertificate(alias));
                }
            }

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(defaultTrustStore);

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(mergedKeyStore, "client-pwd".toCharArray());

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            return sslContext;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create SSLContext", e);
        }
    }
}
