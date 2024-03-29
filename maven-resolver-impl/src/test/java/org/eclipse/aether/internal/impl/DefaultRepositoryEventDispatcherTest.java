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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Locale;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositoryListener;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 */
public class DefaultRepositoryEventDispatcherTest {

    @Test
    void testDispatchHandlesAllEventTypes() {
        DefaultRepositoryEventDispatcher dispatcher = new DefaultRepositoryEventDispatcher(Collections.emptyMap());

        ListenerHandler handler = new ListenerHandler();

        RepositoryListener listener = (RepositoryListener)
                Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {RepositoryListener.class}, handler);

        DefaultRepositorySystemSession session = TestUtils.newSession();
        session.setRepositoryListener(listener);

        for (RepositoryEvent.EventType type : RepositoryEvent.EventType.values()) {
            RepositoryEvent event = new RepositoryEvent.Builder(session, type).build();

            handler.methodName = null;

            dispatcher.dispatch(event);

            assertNotNull(handler.methodName, "not handled: " + type);

            assertEquals(
                    type.name().replace("_", "").toLowerCase(Locale.ENGLISH),
                    handler.methodName.toLowerCase(Locale.ENGLISH),
                    "badly handled: " + type);
        }
    }

    static class ListenerHandler implements InvocationHandler {

        public String methodName;

        public Object invoke(Object proxy, Method method, Object[] args) {
            if (args.length == 1 && args[0] instanceof RepositoryEvent) {
                methodName = method.getName();
            }

            return null;
        }
    }
}
