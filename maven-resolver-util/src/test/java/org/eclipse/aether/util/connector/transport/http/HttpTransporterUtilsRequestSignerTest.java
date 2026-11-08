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

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class HttpTransporterUtilsRequestSignerTest {
    private final RemoteRepository repository = new RemoteRepository.Builder(
                    "s3", "default", "https://s3.example/bucket/prefix/")
            .setAuthentication(new AuthenticationBuilder()
                    .addUsername("AKIDEXAMPLE")
                    .addPassword("secret")
                    .build())
            .build();

    private static DefaultRepositorySystemSession session(String signer) {
        DefaultRepositorySystemSession session = new DefaultRepositorySystemSession(h -> true);
        if (signer != null) {
            session.setConfigProperty(ConfigurationProperties.HTTP_REQUEST_SIGNER, signer);
        }
        return session;
    }

    @Test
    void noSignerByDefault() {
        assertNull(HttpTransporterUtils.getHttpRequestSigner(session(null), repository));
        assertNull(HttpTransporterUtils.getHttpRequestSigner(
                session(ConfigurationProperties.HTTP_REQUEST_SIGNER_NONE), repository));
    }

    @Test
    void awsSigV4WithRepositoryCredentials() {
        HttpRequestSigner signer = HttpTransporterUtils.getHttpRequestSigner(
                session(ConfigurationProperties.HTTP_REQUEST_SIGNER_AWS_SIGV4), repository);
        assertInstanceOf(AwsSigV4RequestSigner.class, signer);
        String authorization = signer.sign("GET", java.net.URI.create("https://s3.example/bucket/prefix/a"))
                .get("Authorization");
        assertTrue(authorization.startsWith("AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/"), authorization);
    }

    @Test
    void repositoryIdSuffixedKeysWin() {
        DefaultRepositorySystemSession session = session(ConfigurationProperties.HTTP_REQUEST_SIGNER_NONE);
        session.setConfigProperty(
                ConfigurationProperties.HTTP_REQUEST_SIGNER + "." + repository.getId(),
                ConfigurationProperties.HTTP_REQUEST_SIGNER_AWS_SIGV4);
        session.setConfigProperty(ConfigurationProperties.HTTP_AWS_REGION + "." + repository.getId(), "eu-central-1");
        String authorization = HttpTransporterUtils.getHttpRequestSigner(session, repository)
                .sign("GET", java.net.URI.create("https://s3.example/bucket/prefix/a"))
                .get("Authorization");
        assertTrue(authorization.contains("/eu-central-1/s3/aws4_request"), authorization);
    }

    @Test
    void awsSigV4WithoutAnyCredentialsIsAnError() {
        assumeTrue(System.getenv("AWS_ACCESS_KEY_ID") == null, "environment carries AWS credentials");
        RemoteRepository anonymous =
                new RemoteRepository.Builder("s3", "default", "https://s3.example/bucket/prefix/").build();
        assertThrows(
                IllegalStateException.class,
                () -> HttpTransporterUtils.getHttpRequestSigner(
                        session(ConfigurationProperties.HTTP_REQUEST_SIGNER_AWS_SIGV4), anonymous));
    }

    @Test
    void awsSigV4WithOnlyAUsernameIsAnError() {
        RemoteRepository usernameOnly = new RemoteRepository.Builder(
                        "s3", "default", "https://s3.example/bucket/prefix/")
                .setAuthentication(
                        new AuthenticationBuilder().addUsername("AKIDEXAMPLE").build())
                .build();
        IllegalStateException e = assertThrows(
                IllegalStateException.class,
                () -> HttpTransporterUtils.getHttpRequestSigner(
                        session(ConfigurationProperties.HTTP_REQUEST_SIGNER_AWS_SIGV4), usernameOnly));
        assertTrue(e.getMessage().contains("only a username"), e.getMessage());
    }

    @Test
    void unknownSignerIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> HttpTransporterUtils.getHttpRequestSigner(session("hmac-sha1"), repository));
    }
}
