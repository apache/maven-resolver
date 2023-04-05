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
package org.eclipse.aether.util.concurrency;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ExecutorUtilsTest {

    @Test
    public void threadCountAsNumber() {
        int threads = ExecutorUtils.threadCount(aSession(123), 1, "threads");
        assertEquals(123, threads);
    }

    @Test
    public void threadCountAsString() {
        int threads = ExecutorUtils.threadCount(aSession("123"), 1, "threads");
        assertEquals(123, threads);
    }

    @Test
    public void dynamicThreadCount() {
        int threads = ExecutorUtils.threadCount(aSession("2C"), 1, "threads");
        assertEquals(Runtime.getRuntime().availableProcessors() * 2, threads);
    }

    private RepositorySystemSession aSession(Object threads) {
        DefaultRepositorySystemSession session = new DefaultRepositorySystemSession();
        Map<String, Object> properties = new HashMap<>();
        properties.put("threads", threads);
        session.setConfigProperties(properties);

        return session;
    }
}
