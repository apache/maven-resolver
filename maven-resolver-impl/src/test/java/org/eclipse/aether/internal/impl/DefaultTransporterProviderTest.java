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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transfer.NoTransporterException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class DefaultTransporterProviderTest {

    private static final class StubTransporterFactory implements TransporterFactory {

        private final String name;

        private final boolean canHandle;

        private final Transporter transporter;

        private int newInstanceCalls;

        private String lastProtocol;

        StubTransporterFactory(String name, boolean canHandle, Transporter transporter) {
            this.name = name;
            this.canHandle = canHandle;
            this.transporter = transporter;
        }

        @Override
        public boolean canHandle(String protocol) {
            lastProtocol = protocol;
            return canHandle;
        }

        @Override
        public String getDescription() {
            return name + " transporter";
        }

        @Override
        public Transporter newInstance(RepositorySystemSession session, RemoteRepository repository)
                throws NoTransporterException {
            newInstanceCalls++;
            if (transporter == null) {
                throw new NoTransporterException(repository);
            }
            return transporter;
        }

        @Override
        public float getPriority() {
            return 1;
        }
    }

    @Test
    void skipsFactoryThatCannotHandleProtocol() {
        Transporter usable = mock(Transporter.class);
        StubTransporterFactory declining = new StubTransporterFactory("declining", false, mock(Transporter.class));
        StubTransporterFactory accepting = new StubTransporterFactory("accepting", true, usable);

        Map<String, TransporterFactory> factories = new HashMap<>();
        factories.put("declining", declining);
        factories.put("accepting", accepting);
        DefaultTransporterProvider provider = new DefaultTransporterProvider(factories);

        RemoteRepository repository =
                new RemoteRepository.Builder("test", "default", "https://example.com/repository").build();
        assertSame(usable, provider.newTransporter(TestUtils.newSession(), repository));

        assertEquals("https", declining.lastProtocol);
        assertEquals(0, declining.newInstanceCalls);
        assertEquals(1, accepting.newInstanceCalls);
    }

    @Test
    void compoundAndCustomProtocolsReachCapableFactory() {
        for (String protocol : new String[] {"file", "symlink+file", "hardlink+file", "jimfs"}) {
            Transporter usable = mock(Transporter.class);
            StubTransporterFactory capable = new StubTransporterFactory("capable", true, usable);

            Map<String, TransporterFactory> factories = new HashMap<>();
            factories.put("capable", capable);
            DefaultTransporterProvider provider = new DefaultTransporterProvider(factories);

            RemoteRepository repository = new RemoteRepository.Builder(
                            "test", "default", protocol + ":/repository")
                    .build();
            assertSame(usable, provider.newTransporter(TestUtils.newSession(), repository));
            assertEquals(protocol, capable.lastProtocol);
            assertEquals(1, capable.newInstanceCalls);
        }
    }

    @Test
    void reportsDecliningFactoriesWhenNoTransporterFound() {
        StubTransporterFactory declining = new StubTransporterFactory("declining", false, null);

        Map<String, TransporterFactory> factories = new HashMap<>();
        factories.put("declining", declining);
        DefaultTransporterProvider provider = new DefaultTransporterProvider(factories);

        RemoteRepository repository =
                new RemoteRepository.Builder("test", "default", "https://example.com/repository").build();
        NoTransporterException error = assertThrows(
                NoTransporterException.class, () -> provider.newTransporter(TestUtils.newSession(), repository));

        assertTrue(error.getMessage().contains("https://example.com/repository"));
        assertEquals(1, error.getSuppressed().length);
        assertEquals(0, declining.newInstanceCalls);
    }
}
