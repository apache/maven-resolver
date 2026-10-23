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
package org.eclipse.aether.util.connector.transport;

import java.net.URI;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TransporterUtilsTest {
    @Test
    void sameOriginUsesSchemeHostAndEffectivePort() {
        assertTrue(TransporterUtils.isSameOrigin(
                URI.create("https://repo.example.com/maven2/"), URI.create("HTTPS://REPO.EXAMPLE.COM:443/other")));
        assertTrue(TransporterUtils.isSameOrigin(
                URI.create("http://repo.example.com/"), URI.create("http://repo.example.com:80/other")));
        assertFalse(TransporterUtils.isSameOrigin(
                URI.create("https://repo.example.com/"), URI.create("http://repo.example.com/")));
        assertFalse(TransporterUtils.isSameOrigin(
                URI.create("https://repo.example.com/"), URI.create("https://repo.example.com:8443/")));
        assertFalse(TransporterUtils.isSameOrigin(
                URI.create("https://repo.example.com/"), URI.create("https://cdn.example.com/")));
        assertFalse(TransporterUtils.isSameOrigin(
                URI.create("ftp://repo.example.com/"), URI.create("ftp://repo.example.com:80/")));
        assertFalse(TransporterUtils.isSameOrigin(URI.create("https://repo.example.com/"), URI.create("relative")));
    }

    @Test
    void sameAuthorityUsesUserHostAndEffectivePort() {
        assertTrue(TransporterUtils.isSameAuthority(
                URI.create("https://user@repo.example.com/maven2/"),
                URI.create("https://USER@REPO.EXAMPLE.COM:443/other")));
        assertTrue(TransporterUtils.isSameAuthority(
                URI.create("http://repo.example.com/"), URI.create("https://repo.example.com:80/other")));
        assertFalse(TransporterUtils.isSameAuthority(
                URI.create("https://user@repo.example.com/"), URI.create("https://other@repo.example.com/")));
        assertFalse(TransporterUtils.isSameAuthority(
                URI.create("https://repo.example.com/"), URI.create("https://repo.example.com:8443/")));
        assertFalse(TransporterUtils.isSameAuthority(
                URI.create("https://repo.example.com/"), URI.create("https://cdn.example.com/")));
        assertFalse(TransporterUtils.isSameAuthority(URI.create("https://repo.example.com/"), URI.create("relative")));
    }
}
