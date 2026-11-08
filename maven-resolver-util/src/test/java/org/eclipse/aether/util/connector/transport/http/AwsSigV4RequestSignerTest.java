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
package org.eclipse.aether.util.connector.transport.http;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The expected values were produced with the AWS SDK for Java 2.57.11 ({@code AwsV4HttpSigner}, service
 * {@code s3}, payload signing off, double URL encoding off, path normalization off, clock fixed at
 * 2015-08-30T12:36:00Z) for the same credentials, so a signature this test accepts is one S3 accepts.
 */
class AwsSigV4RequestSignerTest {
    private static final String ACCESS_KEY = "AKIDEXAMPLE";

    private static final String SECRET_KEY = "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY";

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2015-08-30T12:36:00Z"), ZoneOffset.UTC);

    private static AwsSigV4RequestSigner signer(String region, String sessionToken) {
        return new AwsSigV4RequestSigner(ACCESS_KEY, SECRET_KEY.toCharArray(), sessionToken, region, "s3", CLOCK);
    }

    private static void assertSignature(Map<String, String> headers, String signedHeaders, String signature) {
        assertEquals("20150830T123600Z", headers.get("x-amz-date"));
        assertEquals(AwsSigV4RequestSigner.UNSIGNED_PAYLOAD, headers.get("x-amz-content-sha256"));
        assertTrue(headers.get("Authorization").startsWith("AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20150830/"));
        assertTrue(
                headers.get("Authorization").contains("SignedHeaders=" + signedHeaders + ","),
                headers.get("Authorization"));
        assertTrue(headers.get("Authorization").endsWith("Signature=" + signature), headers.get("Authorization"));
    }

    @Test
    void rootOfDefaultPortHost() {
        Map<String, String> headers =
                signer("us-east-1", null).sign("GET", URI.create("https://example.amazonaws.com/"));
        assertSignature(
                headers,
                "host;x-amz-content-sha256;x-amz-date",
                "f8815cdb11f23ac0476d526ba474c2f76561dcc4405465cd093a230e86a592d6");
        assertFalse(headers.containsKey("x-amz-security-token"));
    }

    @Test
    void pathOnExplicitPort() {
        Map<String, String> headers = signer("us-east-1", null)
                .sign(
                        "GET",
                        URI.create(
                                "http://localhost:9000/maven/repo/org/apache/maven/maven-core/4.0.0/maven-core-4.0.0.pom"));
        assertSignature(
                headers,
                "host;x-amz-content-sha256;x-amz-date",
                "566c920857809939750397bb82758e7c29ad64630b854651a874293e050693f3");
    }

    @Test
    void encodedPathIsSignedAsSent() {
        Map<String, String> headers = signer("eu-west-1", null)
                .sign("HEAD", URI.create("https://s3.eu-west-1.amazonaws.com/bucket/dir/file%20name%2Bplus.txt"));
        assertSignature(
                headers,
                "host;x-amz-content-sha256;x-amz-date",
                "83b36d4172632691414b1cd57b89fd07c86e6e4edd6333cbf85b825c883d63ee");
        assertTrue(headers.get("Authorization").contains("/20150830/eu-west-1/s3/aws4_request"));
    }

    @Test
    void clientEncodingVariantsSignAlike() {
        // the Apache client sends '+' literally, others send %2B, and lower-case hex is legal: S3 treats them all as
        // the same path, so the signature must be the one of the canonical form
        String canonical = signer("eu-west-1", null)
                .sign("HEAD", URI.create("https://s3.eu-west-1.amazonaws.com/bucket/dir/file%20name%2Bplus.txt"))
                .get("Authorization");
        for (String variant : new String[] {
            "https://s3.eu-west-1.amazonaws.com/bucket/dir/file%20name+plus.txt",
            "https://s3.eu-west-1.amazonaws.com/bucket/dir/file%20name%2bplus.txt"
        }) {
            assertEquals(
                    canonical,
                    signer("eu-west-1", null).sign("HEAD", URI.create(variant)).get("Authorization"),
                    variant);
        }
        assertEquals(
                "/bucket/dir/file%20name%2Bplus.txt",
                AwsSigV4RequestSigner.canonicalPath(URI.create("https://h/bucket/dir/file%20name+plus.txt")));
        assertEquals("/a~b/%C3%A9/", AwsSigV4RequestSigner.canonicalPath(URI.create("https://h/a~b/%c3%a9/")));
        assertEquals("/", AwsSigV4RequestSigner.canonicalPath(URI.create("https://h")));
    }

    @Test
    void putOnNonDefaultHttpsPort() {
        Map<String, String> headers =
                signer("us-east-1", null).sign("PUT", URI.create("https://host.example:8443/bucket/prefix/a.jar"));
        assertSignature(
                headers,
                "host;x-amz-content-sha256;x-amz-date",
                "cd83b1f1caefe4637d3a3647ed0f512cfa936028f32e05ea73076ec890399945");
    }

    @Test
    void sessionTokenIsSentAndSigned() {
        Map<String, String> headers = signer("us-east-1", "AQoDYXdzEJr...EXAMPLETOKEN")
                .sign("GET", URI.create("https://example.amazonaws.com/bucket/key"));
        assertEquals("AQoDYXdzEJr...EXAMPLETOKEN", headers.get("x-amz-security-token"));
        assertSignature(
                headers,
                "host;x-amz-content-sha256;x-amz-date;x-amz-security-token",
                "33fac62041f664e6197323612436f0cb0c8a3d843ced2be76c5cda0559ed9b59");
    }

    @Test
    void hostHeaderDropsOnlyTheDefaultPort() {
        assertEquals("example.com", AwsSigV4RequestSigner.hostHeader(URI.create("https://example.com:443/x")));
        assertEquals("example.com", AwsSigV4RequestSigner.hostHeader(URI.create("http://example.com:80/x")));
        assertEquals("example.com:443", AwsSigV4RequestSigner.hostHeader(URI.create("http://example.com:443/x")));
        assertEquals("[::1]:9000", AwsSigV4RequestSigner.hostHeader(URI.create("http://[::1]:9000/x")));
    }

    @Test
    void signedHeaderNamesAreCaseInsensitive() {
        assertTrue(signer("us-east-1", null).signedHeaderNames().contains("AUTHORIZATION"));
        assertTrue(signer("us-east-1", null).signedHeaderNames().contains("X-Amz-Date"));
        assertFalse(signer("us-east-1", null).signedHeaderNames().contains("Host"));
    }

    @Test
    void closedSignerRefusesToSign() {
        AwsSigV4RequestSigner signer = signer("us-east-1", null);
        signer.close();
        assertThrows(IllegalStateException.class, () -> signer.sign("GET", URI.create("https://s3.example/a")));
    }

    /**
     * Races {@code close()} against {@code sign()}: each signature that is produced must equal the one of an
     * untouched signer, since the fixed clock makes it deterministic; a signer closed first must refuse instead.
     */
    @Test
    void closeDuringSignNeverYieldsAWrongSignature() throws Exception {
        URI uri = URI.create("https://s3.example/bucket/a");
        Map<String, String> expected = signer("us-east-1", null).sign("GET", uri);
        for (int i = 0; i < 500; i++) {
            AwsSigV4RequestSigner signer = signer("us-east-1", null);
            CountDownLatch go = new CountDownLatch(1);
            Thread closer = new Thread(() -> {
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                signer.close();
            });
            closer.start();
            go.countDown();
            try {
                assertEquals(expected, signer.sign("GET", uri));
            } catch (IllegalStateException closedFirst) {
                // refusing after close is the correct outcome
            }
            closer.join(2000);
        }
    }
}
