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

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that {@link Error}s thrown by callables complete the future instead of leaving it hanging.
 */
class SmartExecutorTest {
    private static final Callable<String> BOOM = () -> {
        throw new AssertionError("boom");
    };

    private static void assertErrorPropagated(SmartExecutor executor) {
        try (SmartExecutor ignored = executor) {
            Future<String> future = executor.submit(BOOM);
            ExecutionException e = assertThrows(ExecutionException.class, future::get);
            assertInstanceOf(AssertionError.class, e.getCause());
            assertTrue(future.isDone());
        }
    }

    @Test
    void directPropagatesError() {
        assertErrorPropagated(new SmartExecutor.Direct());
    }

    @Test
    void pooledPropagatesError() {
        ExecutorService pool = Executors.newFixedThreadPool(1);
        assertErrorPropagated(new SmartExecutor.Pooled(pool));
    }

    @Test
    void limitedPropagatesError() {
        ExecutorService pool = Executors.newFixedThreadPool(1);
        assertErrorPropagated(new SmartExecutor.Limited(new SmartExecutor.Pooled(pool), 1));
    }

    @Test
    void limitedReleasesPermitAfterError() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(1);
        try (SmartExecutor executor = new SmartExecutor.Limited(new SmartExecutor.Pooled(pool), 1)) {
            assertThrows(ExecutionException.class, () -> executor.submit(BOOM).get());
            // would block forever if the permit leaked
            executor.submit(() -> "ok").get();
        }
    }
}
