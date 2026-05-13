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
import java.net.URISyntaxException;

import org.eclipse.aether.repository.RemoteRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class HttpTransporterUtilsTest {
    @Test
    void goodUris() throws URISyntaxException {
        URI uri;

        // URI gets:
        // * appended / (slash) if there is no trailing slash

        uri = HttpTransporterUtils.getBaseUri(
                new RemoteRepository.Builder("repo", "", "https://host.com/base/").build());
        assertEquals("https://host.com/base/", uri.toASCIIString());

        uri = HttpTransporterUtils.getBaseUri(
                new RemoteRepository.Builder("repo", "", "https://host.com/base").build());
        assertEquals("https://host.com/base/", uri.toASCIIString());

        uri = HttpTransporterUtils.getBaseUri(
                new RemoteRepository.Builder("fragment", "", "https://host.com").build());
        assertEquals("https://host.com/", uri.toASCIIString());
    }

    @Test
    void badUris() {
        assertThrows(
                URISyntaxException.class,
                () -> HttpTransporterUtils.getBaseUri(new RemoteRepository.Builder("opaque", "", "is:opaque").build()));
        assertThrows(
                URISyntaxException.class,
                () -> HttpTransporterUtils.getBaseUri(
                        new RemoteRepository.Builder("query", "", "https://host.com/path?query").build()));
        assertThrows(
                URISyntaxException.class,
                () -> HttpTransporterUtils.getBaseUri(
                        new RemoteRepository.Builder("fragment", "", "https://host.com/path#fragment").build()));
    }

    @Test
    void getBaseUriWithNonAscii() throws URISyntaxException {
        URI uri;

        uri = HttpTransporterUtils.getBaseUri(
                new RemoteRepository.Builder("repø", "default", "https://host.dk/repø").build());
        assertNotNull(uri);
        assertEquals("https", uri.getScheme());
        assertEquals("host.dk", uri.getHost());
        assertEquals("/repø/", uri.getPath());

        uri = HttpTransporterUtils.getBaseUri(
                new RemoteRepository.Builder("repø", "default", "https://host.dk/rep%C3%B8").build());
        assertNotNull(uri);
        assertEquals("https", uri.getScheme());
        assertEquals("host.dk", uri.getHost());
        assertEquals("/repø/", uri.getPath());

        uri = HttpTransporterUtils.getBaseUri(
                new RemoteRepository.Builder("räpo", "default", "https://host.de/räpo").build());
        assertNotNull(uri);
        assertEquals("https", uri.getScheme());
        assertEquals("host.de", uri.getHost());
        assertEquals("/räpo/", uri.getPath());

        uri = HttpTransporterUtils.getBaseUri(
                new RemoteRepository.Builder("räpo", "default", "https://host.de/r%C3%A4po").build());
        assertNotNull(uri);
        assertEquals("https", uri.getScheme());
        assertEquals("host.de", uri.getHost());
        assertEquals("/räpo/", uri.getPath());
    }
}
