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
package org.eclipse.aether.named.hazelcast;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * UT for the explicit-configuration guard used by the default constructors: creating a Hazelcast member or client
 * from built-in defaults would expose the lock state to unauthenticated network peers, so the guard must refuse
 * when no explicit configuration is discoverable.
 */
class RequireExplicitHazelcastConfigTest {
    @Test
    void refusesWithoutExplicitConfiguration() {
        ClassLoader emptyClassLoader = new URLClassLoader(new URL[0], null);
        assertThrows(
                IllegalStateException.class,
                () -> HazelcastSemaphoreNamedLockFactory.requireExplicitHazelcastConfig(
                        "no.such.system.property." + UUID.randomUUID(), emptyClassLoader, "no-such-config.xml"));
    }

    @Test
    void acceptsSystemPropertyConfiguration() {
        String property = "test.hazelcast.config." + UUID.randomUUID();
        System.setProperty(property, "somewhere.xml");
        try {
            ClassLoader emptyClassLoader = new URLClassLoader(new URL[0], null);
            HazelcastSemaphoreNamedLockFactory.requireExplicitHazelcastConfig(
                    property, emptyClassLoader, "no-such-config.xml");
        } finally {
            System.clearProperty(property);
        }
    }

    @Test
    void acceptsClasspathConfiguration() {
        // the test classpath ships hazelcast.xml (used by the ITs)
        HazelcastSemaphoreNamedLockFactory.requireExplicitHazelcastConfig(
                "no.such.system.property." + UUID.randomUUID(), getClass().getClassLoader(), "hazelcast.xml");
    }
}
