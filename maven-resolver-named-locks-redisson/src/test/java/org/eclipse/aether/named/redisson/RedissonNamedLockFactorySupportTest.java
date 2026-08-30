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
package org.eclipse.aether.named.redisson;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UT for the insecure-address guard: lock answers gate writes to the shared local repository, so a plaintext
 * non-loopback Redis address must be refused unless explicitly opted into.
 */
class RedissonNamedLockFactorySupportTest {
    @Test
    void tlsAndLoopbackAddressesAreAcceptable() {
        assertFalse(RedissonNamedLockFactorySupport.isInsecureRemoteAddress("redis://localhost:6379"));
        assertFalse(RedissonNamedLockFactorySupport.isInsecureRemoteAddress("redis://127.0.0.1:6379"));
        assertFalse(RedissonNamedLockFactorySupport.isInsecureRemoteAddress("redis://[::1]:6379"));
        assertFalse(RedissonNamedLockFactorySupport.isInsecureRemoteAddress("rediss://redis.example.com:6379"));
    }

    @Test
    void plaintextRemoteAddressesAreInsecure() {
        assertTrue(RedissonNamedLockFactorySupport.isInsecureRemoteAddress("redis://redis.ci.internal:6379"));
        assertTrue(RedissonNamedLockFactorySupport.isInsecureRemoteAddress("redis://10.0.0.5:6379"));
        // unparseable addresses fail closed
        assertTrue(RedissonNamedLockFactorySupport.isInsecureRemoteAddress("not a valid uri"));
    }

    @Test
    void plaintextRemoteAddressRefusedAtFactoryCreation() {
        System.setProperty(RedissonNamedLockFactorySupport.SYSTEM_PROP_REDIS_ADDRESS, "redis://redis.ci.internal:6379");
        try {
            // refused before any connection attempt is made
            assertThrows(IllegalStateException.class, RedissonSemaphoreNamedLockFactory::new);
        } finally {
            System.clearProperty(RedissonNamedLockFactorySupport.SYSTEM_PROP_REDIS_ADDRESS);
        }
    }
}
