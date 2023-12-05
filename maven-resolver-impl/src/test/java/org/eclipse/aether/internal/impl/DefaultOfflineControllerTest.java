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
package org.eclipse.aether.internal.impl;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.transfer.RepositoryOfflineException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DefaultOfflineControllerTest {

    private DefaultOfflineController controller;

    private RepositorySystemSession newSession(boolean offline, String protocols, String hosts) {
        DefaultRepositorySystemSession session = TestUtils.newSession();
        session.setOffline(offline);
        session.setConfigProperty(DefaultOfflineController.CONFIG_PROP_OFFLINE_PROTOCOLS, protocols);
        session.setConfigProperty(DefaultOfflineController.CONFIG_PROP_OFFLINE_HOSTS, hosts);
        return session;
    }

    private RemoteRepository newRepo(String url) {
        return new RemoteRepository.Builder("central", "default", url).build();
    }

    @BeforeEach
    void setup() {
        controller = new DefaultOfflineController();
    }

    @Test
    void testCheckOffline_Online() {
        assertThrows(
                RepositoryOfflineException.class,
                () -> controller.checkOffline(newSession(false, null, null), newRepo("http://eclipse.org")));
    }

    @Test
    void testCheckOffline_Offline() {
        assertThrows(
                RepositoryOfflineException.class,
                () -> controller.checkOffline(newSession(true, null, null), newRepo("http://eclipse.org")));
    }

    @Test
    void testCheckOffline_Offline_OfflineProtocol() throws Exception {
        controller.checkOffline(newSession(true, "file", null), newRepo("file://repo"));
        controller.checkOffline(newSession(true, "file", null), newRepo("FILE://repo"));
        controller.checkOffline(newSession(true, "  file  ,  classpath  ", null), newRepo("file://repo"));
        controller.checkOffline(newSession(true, "  file  ,  classpath  ", null), newRepo("classpath://repo"));
    }

    @Test
    void testCheckOffline_Offline_OnlineProtocol() {
        assertThrows(
                RepositoryOfflineException.class,
                () -> controller.checkOffline(newSession(true, "file", null), newRepo("http://eclipse.org")));
    }

    @Test
    void testCheckOffline_Offline_OfflineHost() throws Exception {
        controller.checkOffline(newSession(true, null, "localhost"), newRepo("http://localhost"));
        controller.checkOffline(newSession(true, null, "localhost"), newRepo("http://LOCALHOST"));
        controller.checkOffline(newSession(true, null, "  localhost  ,  127.0.0.1  "), newRepo("http://localhost"));
        controller.checkOffline(newSession(true, null, "  localhost  ,  127.0.0.1  "), newRepo("http://127.0.0.1"));
    }

    @Test
    void testCheckOffline_Offline_OnlineHost() {
        assertThrows(
                RepositoryOfflineException.class,
                () -> controller.checkOffline(newSession(true, null, "localhost"), newRepo("http://eclipse.org")));
    }
}
