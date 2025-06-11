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
package org.eclipse.aether;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DefaultRepositoryCacheTest {

    private DefaultRepositoryCache cache = new DefaultRepositoryCache();

    private RepositorySystemSession session = new DefaultRepositorySystemSession();

    private Object get(Object key) {
        return cache.get(session, key);
    }

    private void put(Object key, Object value) {
        cache.put(session, key, value);
    }

    @Test(expected = RuntimeException.class)
    public void testGetNullKey() {
        get(null);
    }

    @Test(expected = RuntimeException.class)
    public void testPutNullKey() {
        put(null, "data");
    }

    @Test
    public void testGetPut() {
        Object key = "key";
        assertNull(get(key));
        put(key, "value");
        assertEquals("value", get(key));
        put(key, "changed");
        assertEquals("changed", get(key));
        put(key, null);
        assertNull(get(key));
    }

    @Test(timeout = 10000L)
    public void testConcurrency() throws Exception {
        final AtomicReference<Throwable> error = new AtomicReference<>();
        Thread[] threads = new Thread[20];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread() {
                @Override
                public void run() {
                    for (int i = 0; i < 100; i++) {
                        String key = UUID.randomUUID().toString();
                        try {
                            put(key, Boolean.TRUE);
                            assertEquals(Boolean.TRUE, get(key));
                        } catch (Throwable t) {
                            error.compareAndSet(null, t);
                            t.printStackTrace();
                        }
                    }
                }
            };
        }
        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        assertNull(String.valueOf(error.get()), error.get());
    }
}
