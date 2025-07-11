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
package org.eclipse.aether.graph;

import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 */
public class DefaultDependencyNodeTest {
    @Test
    void testVisitorInterrupt() throws Exception {
        DefaultDependencyNode node =
                new DefaultDependencyNode(new Dependency(new DefaultArtifact("gid:aid:ver"), "compile"));
        // we just use dummy visitor, as it is not visiting that matters
        DependencyVisitor visitor = new DependencyVisitor() {
            @Override
            public boolean visitEnter(DependencyNode node) {
                return true;
            }

            @Override
            public boolean visitLeave(DependencyNode node) {
                return true;
            }
        };
        AtomicReference<Exception> thrown = new AtomicReference<>(null);
        Thread t = new Thread(() -> {
            Thread.currentThread().interrupt();
            try {
                node.accept(visitor);
                fail("Should fail");
            } catch (Exception e) {
                thrown.set(e);
            }
        });
        t.start();
        t.join();

        assertInstanceOf(RuntimeException.class, thrown.get(), String.valueOf(thrown.get()));
        assertInstanceOf(InterruptedException.class, thrown.get().getCause(), String.valueOf(thrown.get()));
        assertTrue(t.isInterrupted());
    }
}
