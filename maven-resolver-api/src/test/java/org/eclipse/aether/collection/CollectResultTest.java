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
package org.eclipse.aether.collection;

import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.aether.graph.DependencyCycle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class CollectResultTest {

    @Test
    void testConcurrentAddException() throws Exception {
        CollectResult result = new CollectResult(new CollectRequest());

        int threads = 10;
        int additionsPerThread = 100;

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<?>[] futures = new Future<?>[threads];

            for (int i = 0; i < threads; i++) {
                futures[i] = executor.submit(() -> {
                    start.await();

                    for (int j = 0; j < additionsPerThread; j++) {
                        result.addException(new Exception());
                    }
                    return null;
                });
            }

            start.countDown();

            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }

            assertEquals(threads * additionsPerThread, result.getExceptions().size());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void testConcurrentAddCycle() throws Exception {
        CollectResult result = new CollectResult(new CollectRequest());

        int threads = 10;
        int additionsPerThread = 100;

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<?>[] futures = new Future<?>[threads];

            for (int i = 0; i < threads; i++) {
                futures[i] = executor.submit(() -> {
                    start.await();

                    for (int j = 0; j < additionsPerThread; j++) {
                        result.addCycle(mock(DependencyCycle.class));
                    }
                    return null;
                });
            }

            start.countDown();

            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }

            assertEquals(threads * additionsPerThread, result.getCycles().size());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void testGetExceptionsReturnsUnmodifiableList() {
        CollectResult result = new CollectResult(new CollectRequest());
        result.addException(new Exception());

        assertThrows(
                UnsupportedOperationException.class,
                () -> result.getExceptions().clear());
    }

    @Test
    void testGetCyclesReturnsUnmodifiableList() {
        CollectResult result = new CollectResult(new CollectRequest());
        result.addCycle(mock(DependencyCycle.class));

        assertThrows(
                UnsupportedOperationException.class, () -> result.getCycles().clear());
    }

    @Test
    void testConcurrentIterationWhileAddingExceptions() throws Exception {
        CollectResult result = new CollectResult(new CollectRequest());
        result.addException(new Exception());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch iteratorCreated = new CountDownLatch(1);
        CountDownLatch continueIteration = new CountDownLatch(1);

        try {
            Future<?> reader = executor.submit(() -> {
                Iterator<Exception> iterator = result.getExceptions().iterator();
                iteratorCreated.countDown();
                continueIteration.await();

                while (iterator.hasNext()) {
                    iterator.next();
                }
                return null;
            });

            Future<?> writer = executor.submit(() -> {
                iteratorCreated.await();
                result.addException(new Exception());
                continueIteration.countDown();
                return null;
            });

            reader.get(30, TimeUnit.SECONDS);
            writer.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(2, result.getExceptions().size());
    }

    @Test
    void testConcurrentIterationWhileAddingCycles() throws Exception {
        CollectResult result = new CollectResult(new CollectRequest());
        result.addCycle(mock(DependencyCycle.class));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch iteratorCreated = new CountDownLatch(1);
        CountDownLatch continueIteration = new CountDownLatch(1);

        try {
            Future<?> reader = executor.submit(() -> {
                Iterator<DependencyCycle> iterator = result.getCycles().iterator();
                iteratorCreated.countDown();
                continueIteration.await();

                while (iterator.hasNext()) {
                    iterator.next();
                }
                return null;
            });

            Future<?> writer = executor.submit(() -> {
                iteratorCreated.await();
                result.addCycle(mock(DependencyCycle.class));
                continueIteration.countDown();
                return null;
            });

            reader.get(30, TimeUnit.SECONDS);
            writer.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(2, result.getCycles().size());
    }
}
