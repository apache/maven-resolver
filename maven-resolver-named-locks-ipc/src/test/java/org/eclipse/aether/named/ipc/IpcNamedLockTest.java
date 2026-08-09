/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.eclipse.aether.named.ipc;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.aether.named.NamedLockKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IpcNamedLockTest {

    @Test
    void unlockFailureDuringTimeoutCleanupIsLogged() throws Exception {
        IpcClient client = mock(IpcClient.class);
        IpcNamedLockFactory factory = mock(IpcNamedLockFactory.class);

        NamedLockKey key = NamedLockKey.of("test");
        Collection<String> keys = Collections.singleton("test");
        IpcNamedLock lock = new IpcNamedLock(key, factory, client, keys);

        when(client.newContext(true, 100L, TimeUnit.MILLISECONDS)).thenReturn("ctx-1");
        doThrow(new TimeoutException("lock timed out")).when(client).lock("ctx-1", keys, 100L, TimeUnit.MILLISECONDS);
        doThrow(new RuntimeException("simulated unlock failure")).when(client).unlock("ctx-1");

        PrintStream originalErr = System.err;
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errBuffer, true, StandardCharsets.UTF_8));
        try {
            assertFalse(lock.lockShared(100L, TimeUnit.MILLISECONDS));
        } finally {
            System.setErr(originalErr);
        }

        String logOutput = errBuffer.toString(StandardCharsets.UTF_8);
        assertTrue(
                logOutput.contains("simulated unlock failure"),
                "expected unlock failure to be logged, but was: " + logOutput);
    }
}
