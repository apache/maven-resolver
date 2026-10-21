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

import java.net.URI;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Request;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SameAuthorityRedirectGuardTest {
    private static final URI REPOSITORY_URI = URI.create("https://repo.example.com/maven2/");

    @Test
    void sameAuthorityRequestIsNotAborted() {
        Request request = new HttpClient().newRequest("https://repo.example.com/other/a.jar");
        new SameAuthorityRedirectGuard(REPOSITORY_URI).onQueued(request);
        assertNull(request.getAbortCause());
    }

    @Test
    void differentAuthorityRequestIsAborted() {
        Request request = new HttpClient().newRequest("https://mirror.example.net/a.jar");
        new SameAuthorityRedirectGuard(REPOSITORY_URI).onQueued(request);

        assertNotNull(request.getAbortCause());
        assertTrue(request.getAbortCause().getMessage().contains("different authority"));
    }
}
