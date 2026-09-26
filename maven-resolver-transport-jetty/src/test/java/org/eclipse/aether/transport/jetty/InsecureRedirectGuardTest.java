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
package org.eclipse.aether.transport.jetty;

import java.io.File;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Request;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UT for {@link InsecureRedirectGuard}: for a repository configured with an https URL, a request over plain http
 * (which can only arise from following a protocol-downgrading redirect) must be aborted before it is sent.
 */
class InsecureRedirectGuardTest {
    private static HttpClient httpClient;

    @BeforeAll
    static void startClient() throws Exception {
        // Ensure java.io.tmpdir exists before starting HttpClient. Jetty's HttpClient loads
        // compression providers (including Brotli) via ServiceLoader during start(). Brotli4j
        // extracts a native library to a temp file; if the tmpdir does not exist yet (Surefire
        // sets it to target/surefire-tmp which is deleted by mvn clean), extraction fails and
        // Brotli is permanently disabled for the JVM lifetime, breaking later compression tests.
        new File(System.getProperty("java.io.tmpdir")).mkdirs();
        httpClient = new HttpClient();
        httpClient.start();
    }

    @AfterAll
    static void stopClient() throws Exception {
        httpClient.stop();
    }

    @Test
    void plainHttpRequestIsAborted() {
        Request request = httpClient.newRequest("http://repo.example.com/g/a/1.0/a-1.0.jar");
        new InsecureRedirectGuard().onQueued(request);
        Throwable cause = request.getAbortCause();
        assertNotNull(cause, "expected the plain http request to be aborted");
        assertTrue(
                cause.getMessage().contains(JettyTransporterConfigurationKeys.CONFIG_PROP_FOLLOW_INSECURE_REDIRECTS));
    }

    @Test
    void httpsRequestIsNotAborted() {
        Request request = httpClient.newRequest("https://repo.example.com/g/a/1.0/a-1.0.jar");
        new InsecureRedirectGuard().onQueued(request);
        assertNull(request.getAbortCause());
    }

    @Test
    void httpsRequestToAnotherHostIsNotAborted() {
        // cross-host redirects that keep https are still followed (parity with the other transports)
        Request request = httpClient.newRequest("https://mirror.example.org/g/a/1.0/a-1.0.jar");
        new InsecureRedirectGuard().onQueued(request);
        assertNull(request.getAbortCause());
    }
}
