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
package org.eclipse.aether.util.listener;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.transfer.AbstractTransferListener;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferListener;
import org.eclipse.aether.transfer.TransferResource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 */
public class ChainedTransferListenerTest {

    @Test
    void testAllEventTypesHandled() throws Exception {
        for (Method method : TransferListener.class.getMethods()) {
            assertNotNull(
                    ChainedTransferListener.class.getDeclaredMethod(method.getName(), method.getParameterTypes()));
        }
    }

    @Test
    void testListenerExceptionDoesNotBlockSubsequentListeners() {
        AtomicBoolean secondListenerCalled = new AtomicBoolean(false);
        AtomicBoolean errorHandlerCalled = new AtomicBoolean(false);

        TransferListener faultyListener = new AbstractTransferListener() {
            @Override
            public void transferSucceeded(TransferEvent event) {
                throw new RuntimeException("Simulated transfer listener failure");
            }
        };

        TransferListener normalListener = new AbstractTransferListener() {
            @Override
            public void transferSucceeded(TransferEvent event) {
                secondListenerCalled.set(true);
            }
        };

        ChainedTransferListener chained = new ChainedTransferListener(faultyListener, normalListener) {
            @Override
            protected void handleError(TransferEvent event, TransferListener listener, RuntimeException error) {
                super.handleError(event, listener, error);
                errorHandlerCalled.set(true);
            }
        };

        TransferResource resource = new TransferResource(
                "https://repo.example.com", "path/to/artifact.jar", "path/to/artifact.jar", (java.io.File) null, null);
        TransferEvent event = new TransferEvent.Builder(TestUtils.newSession(), resource)
                .setType(TransferEvent.EventType.SUCCEEDED)
                .setRequestType(TransferEvent.RequestType.GET)
                .build();

        assertDoesNotThrow(() -> chained.transferSucceeded(event));
        assertTrue(errorHandlerCalled.get(), "handleError should have been invoked for faulty listener");
        assertTrue(
                secondListenerCalled.get(), "Subsequent listener should still receive event despite previous failure");
    }
}
