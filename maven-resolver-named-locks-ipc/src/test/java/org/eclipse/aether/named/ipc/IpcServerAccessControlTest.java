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
package org.eclipse.aether.named.ipc;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.channels.ByteChannel;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * UT for the cross-client access control of {@link IpcServer}: lock contexts are scoped to the connection that
 * created them, and stopping the daemon requires the bootstrap token that is only shared with the spawning
 * client. The protocol is otherwise unauthenticated, so these are the guarantees that keep one local client from
 * disrupting another client's locks.
 */
class IpcServerAccessControlTest {
    private static final String TOKEN = "test-bootstrap-token";

    private IpcServer server;

    private String serverAddress;

    /**
     * A minimal raw protocol client, mirroring the wire format used by {@link IpcClient}.
     */
    static class RawClient implements AutoCloseable {
        final SocketChannel socket;
        final DataInputStream input;
        final DataOutputStream output;
        int requestId;

        RawClient(String address) throws IOException {
            socket = SocketChannel.open(SocketFamily.fromString(address));
            ByteChannel wrapper = new ByteChannelWrapper(socket);
            input = new DataInputStream(Channels.newInputStream(wrapper));
            output = new DataOutputStream(Channels.newOutputStream(wrapper));
        }

        List<String> request(String... words) throws IOException {
            output.writeInt(++requestId);
            output.writeInt(words.length);
            for (String word : words) {
                output.writeUTF(word);
            }
            output.flush();
            input.readInt(); // response id
            int sz = input.readInt();
            List<String> response = new ArrayList<>();
            for (int i = 0; i < sz; i++) {
                response.add(input.readUTF());
            }
            return response;
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    @BeforeEach
    void startServer() throws Exception {
        try (ServerSocketChannel rendezvous = SocketFamily.unix.openServerSocket()) {
            String tmpaddr = SocketFamily.toString(rendezvous.getLocalAddress());
            server = IpcServer.runServer(SocketFamily.unix, tmpaddr, TOKEN);
            try (SocketChannel handshake = rendezvous.accept()) {
                DataInputStream dis = new DataInputStream(Channels.newInputStream(handshake));
                assertEquals(TOKEN, dis.readUTF());
                serverAddress = dis.readUTF();
            }
        }
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void contextsAreScopedToTheCreatingConnection() throws Exception {
        try (RawClient owner = new RawClient(serverAddress);
                RawClient other = new RawClient(serverAddress)) {
            List<String> created = owner.request(IpcMessages.REQUEST_CONTEXT, "false");
            assertEquals(IpcMessages.RESPONSE_CONTEXT, created.get(0));
            String contextId = created.get(1);

            // another connection must not be able to close (and thereby unlock) a foreign context;
            // the server drops the offending connection instead of answering
            assertThrows(IOException.class, () -> other.request(IpcMessages.REQUEST_CLOSE, contextId));

            // while the owning connection still can
            List<String> closed = owner.request(IpcMessages.REQUEST_CLOSE, contextId);
            assertEquals(IpcMessages.RESPONSE_CLOSE, closed.get(0));
        }
    }

    @Test
    void acquireIsScopedToTheCreatingConnection() throws Exception {
        try (RawClient owner = new RawClient(serverAddress);
                RawClient other = new RawClient(serverAddress)) {
            List<String> created = owner.request(IpcMessages.REQUEST_CONTEXT, "true");
            String contextId = created.get(1);

            assertThrows(IOException.class, () -> other.request(IpcMessages.REQUEST_ACQUIRE, contextId, "some-key"));
        }
    }

    @Test
    void stopRequiresTheBootstrapToken() throws Exception {
        try (RawClient mallory = new RawClient(serverAddress)) {
            assertThrows(IOException.class, () -> mallory.request(IpcMessages.REQUEST_STOP, "wrong-token"));
        }
        try (RawClient mallory = new RawClient(serverAddress)) {
            assertThrows(IOException.class, () -> mallory.request(IpcMessages.REQUEST_STOP));
        }

        // the server survived both attempts and still serves...
        try (RawClient legit = new RawClient(serverAddress)) {
            List<String> created = legit.request(IpcMessages.REQUEST_CONTEXT, "true");
            assertEquals(IpcMessages.RESPONSE_CONTEXT, created.get(0));

            // ...and the holder of the bootstrap token may stop it
            List<String> stopped = legit.request(IpcMessages.REQUEST_STOP, TOKEN);
            assertEquals(IpcMessages.RESPONSE_STOP, stopped.get(0));
        }
    }
}
