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
package org.eclipse.aether.util.repository;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.repository.WorkspaceRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ChainedWorkspaceReaderTest {
    /**
     * Stable reader, returns always same instance for {@link #getRepository()}.
     */
    private static class StableWorkspaceReader implements WorkspaceReader {
        private final WorkspaceRepository repository = new WorkspaceRepository("stable", "stableKey");

        @Override
        public WorkspaceRepository getRepository() {
            return repository;
        }

        @Override
        public File findArtifact(Artifact artifact) {
            return null;
        }

        @Override
        public List<String> findVersions(Artifact artifact) {
            return Collections.emptyList();
        }
    }

    /**
     * Unstable reader, returns always different instance with different key for {@link #getRepository()}.
     */
    private static class UnstableWorkspaceReader implements WorkspaceReader {
        private final AtomicInteger count = new AtomicInteger();

        @Override
        public WorkspaceRepository getRepository() {
            return new WorkspaceRepository("unstable", count.getAndIncrement());
        }

        @Override
        public File findArtifact(Artifact artifact) {
            return null;
        }

        @Override
        public List<String> findVersions(Artifact artifact) {
            return Collections.emptyList();
        }
    }

    @Test
    void constructEmpty() {
        ChainedWorkspaceReader reader = new ChainedWorkspaceReader();
        WorkspaceRepository repository = reader.getRepository();
        assertNotNull(repository);
        assertEquals("", repository.getContentType());
    }

    @Test
    void constructNullReader() {
        ChainedWorkspaceReader reader = new ChainedWorkspaceReader((WorkspaceReader) null);
        WorkspaceRepository repository = reader.getRepository();
        assertNotNull(repository);
        assertEquals("", repository.getContentType());
    }

    @Test
    void constructNullVararg() {
        ChainedWorkspaceReader reader = new ChainedWorkspaceReader((WorkspaceReader[]) null);
        WorkspaceRepository repository = reader.getRepository();
        assertNotNull(repository);
        assertEquals("", repository.getContentType());
    }

    @Test
    void constructOne() {
        ChainedWorkspaceReader reader = new ChainedWorkspaceReader(new StableWorkspaceReader());
        WorkspaceRepository repository = reader.getRepository();
        assertNotNull(repository);
        assertEquals("stable", repository.getContentType());
    }

    @Test
    void constructTwo() {
        ChainedWorkspaceReader reader =
                new ChainedWorkspaceReader(new StableWorkspaceReader(), new StableWorkspaceReader());
        WorkspaceRepository repository = reader.getRepository();
        assertNotNull(repository);
        assertEquals("stable+stable", repository.getContentType());
    }

    @Test
    void constructMultipleWithNulls() {
        ChainedWorkspaceReader reader1 =
                new ChainedWorkspaceReader(null, new StableWorkspaceReader(), null, new StableWorkspaceReader(), null);
        ChainedWorkspaceReader reader2 =
                new ChainedWorkspaceReader(new StableWorkspaceReader(), new StableWorkspaceReader());
        WorkspaceRepository repository1 = reader1.getRepository();
        assertNotNull(repository1);
        WorkspaceRepository repository2 = reader2.getRepository();
        assertNotNull(repository2);
        assertEquals("stable+stable", repository1.getContentType());
        assertEquals("stable+stable", repository2.getContentType());
        assertEquals(repository1.getKey(), repository2.getKey());
    }

    @Test
    void keyChange() {
        WorkspaceReader reader =
                ChainedWorkspaceReader.newInstance(new StableWorkspaceReader(), new UnstableWorkspaceReader());
        WorkspaceRepository repository = reader.getRepository();
        assertNotNull(reader);
        assertEquals("stable+unstable", repository.getContentType());
        assertNotEquals(repository, reader.getRepository());
    }

    @Test
    void concurrentKeyChange() throws InterruptedException {
        WorkspaceReader reader =
                ChainedWorkspaceReader.newInstance(new StableWorkspaceReader(), new UnstableWorkspaceReader());
        final int threads = 10;
        List<WorkspaceRepository> results = new ArrayList<>(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                        try {
                            results.add(reader.getRepository());
                        } finally {
                            latch.countDown();
                        }
                    })
                    .start();
        }
        latch.await();
        // assert no same element present in results
        assertEquals(new HashSet<>(results).size(), results.size());
        for (int i = 0; i < threads; i++) {
            assertEquals("stable+unstable", results.get(i).getContentType());
        }
    }
}
