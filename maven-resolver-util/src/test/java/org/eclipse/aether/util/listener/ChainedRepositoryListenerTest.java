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

import org.eclipse.aether.AbstractRepositoryListener;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositoryListener;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 */
public class ChainedRepositoryListenerTest {

    @Test
    public void testAllEventTypesHandled() throws Exception {
        for (Method method : RepositoryListener.class.getMethods()) {
            assertNotNull(
                    ChainedRepositoryListener.class.getDeclaredMethod(method.getName(), method.getParameterTypes()));
        }
    }

    @Test
    public void testListenerExceptionDoesNotBlockSubsequentListeners() {
        final AtomicBoolean secondListenerCalled = new AtomicBoolean(false);

        RepositoryListener faultyListener = new AbstractRepositoryListener() {
            @Override
            public void artifactResolved(RepositoryEvent event) {
                throw new RuntimeException("Simulated listener failure");
            }
        };

        RepositoryListener normalListener = new AbstractRepositoryListener() {
            @Override
            public void artifactResolved(RepositoryEvent event) {
                secondListenerCalled.set(true);
            }
        };

        ChainedRepositoryListener chained = new ChainedRepositoryListener(faultyListener, normalListener);

        RepositoryEvent event = new RepositoryEvent.Builder(
                        TestUtils.newSession(), RepositoryEvent.EventType.ARTIFACT_RESOLVED)
                .build();

        chained.artifactResolved(event);
        assertTrue(
                "Subsequent listener should still receive event despite previous failure", secondListenerCalled.get());
    }
}
