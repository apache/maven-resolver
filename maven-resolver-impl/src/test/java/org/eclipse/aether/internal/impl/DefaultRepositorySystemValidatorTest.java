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

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.aether.DefaultRepositoryCache;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.spi.validator.Validator;
import org.eclipse.aether.spi.validator.ValidatorFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefaultRepositorySystemValidatorTest {
    @Test
    void abstain() {
        DefaultRepositorySystemValidator validator = new DefaultRepositorySystemValidator(
                Collections.singletonMap("abstain", session -> ValidatorFactory.NOOP));
        ArtifactDescriptorRequest request =
                new ArtifactDescriptorRequest(new DefaultArtifact("foo:bar:1.0"), Collections.emptyList(), "test");
        assertDoesNotThrow(() -> validator.validateArtifactDescriptorRequest(TestUtils.newSession(), request));
    }

    @Test
    void fail() {
        DefaultRepositorySystemValidator validator =
                new DefaultRepositorySystemValidator(Collections.singletonMap("fail", session -> new Validator() {
                    @Override
                    public void validateArtifact(Artifact artifact) throws IllegalArgumentException {
                        throw new IllegalArgumentException("Artifact validation failed");
                    }
                }));
        ArtifactDescriptorRequest request =
                new ArtifactDescriptorRequest(new DefaultArtifact("foo:bar:1.0"), Collections.emptyList(), "test");
        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateArtifactDescriptorRequest(TestUtils.newSession(), request));
    }

    @Test
    void withCaching() {
        final ConcurrentHashMap<Integer, AtomicInteger> instanceCounter = new ConcurrentHashMap<>();
        DefaultRepositorySystemValidator validator =
                new DefaultRepositorySystemValidator(Collections.singletonMap("counter", session -> new Validator() {
                    @Override
                    public void validateArtifact(Artifact artifact) throws IllegalArgumentException {
                        instanceCounter
                                .computeIfAbsent(System.identityHashCode(this), k -> new AtomicInteger(0))
                                .incrementAndGet();
                    }
                }));
        DefaultRepositorySystemSession sameSession = TestUtils.newSession();
        sameSession.setCache(new DefaultRepositoryCache()); // with cache
        assertDoesNotThrow(() -> validator.validateArtifactDescriptorRequest(
                sameSession,
                new ArtifactDescriptorRequest(new DefaultArtifact("foo:bar:1.0"), Collections.emptyList(), "test")));
        assertDoesNotThrow(() -> validator.validateArtifactDescriptorRequest(
                sameSession,
                new ArtifactDescriptorRequest(new DefaultArtifact("baz:foo:1.0"), Collections.emptyList(), "test")));
        // one instance is
        assertEquals(1, instanceCounter.size());
        // used twice
        assertEquals(
                2,
                instanceCounter
                        .get(instanceCounter.keySet().stream().iterator().next())
                        .get());
    }

    @Test
    void withoutCaching() {
        final ConcurrentHashMap<Integer, AtomicInteger> instanceCounter = new ConcurrentHashMap<>();
        DefaultRepositorySystemValidator validator =
                new DefaultRepositorySystemValidator(Collections.singletonMap("counter", session -> new Validator() {
                    @Override
                    public void validateArtifact(Artifact artifact) throws IllegalArgumentException {
                        instanceCounter
                                .computeIfAbsent(System.identityHashCode(this), k -> new AtomicInteger(0))
                                .incrementAndGet();
                    }
                }));
        DefaultRepositorySystemSession sameSession = TestUtils.newSession();
        sameSession.setCache(null); // without cache
        assertDoesNotThrow(() -> validator.validateArtifactDescriptorRequest(
                sameSession,
                new ArtifactDescriptorRequest(new DefaultArtifact("foo:bar:1.0"), Collections.emptyList(), "test")));
        assertDoesNotThrow(() -> validator.validateArtifactDescriptorRequest(
                sameSession,
                new ArtifactDescriptorRequest(new DefaultArtifact("baz:foo:1.0"), Collections.emptyList(), "test")));
        // two instances
        assertEquals(2, instanceCounter.size());
        for (AtomicInteger counter : instanceCounter.values()) {
            // each used once
            assertEquals(1, counter.get());
        }
    }

    @Test
    void dependencies() {
        final List<Dependency> dependencies =
                Collections.singletonList(new Dependency(new DefaultArtifact("foo:bar:1.0"), "compile"));
        final List<Dependency> managedDependencies =
                Collections.singletonList(new Dependency(new DefaultArtifact("baz:foo:1.0"), "compile"));

        final AtomicInteger validateDependencies = new AtomicInteger(0);
        final AtomicInteger validateManagedDependencies = new AtomicInteger(0);
        DefaultRepositorySystemValidator validator = new DefaultRepositorySystemValidator(Collections.singletonMap(
                "dependencies", session -> new Validator() {
                    @Override
                    public void validateDependency(Dependency dependency) throws IllegalArgumentException {
                        assertTrue(dependencies.contains(dependency));
                        validateDependencies.incrementAndGet();
                    }

                    @Override
                    public void validateManagedDependency(Dependency managedDependency)
                            throws IllegalArgumentException {
                        assertTrue(managedDependencies.contains(managedDependency));
                        validateManagedDependencies.incrementAndGet();
                    }
                }));
        assertDoesNotThrow(() -> validator.validateCollectRequest(
                TestUtils.newSession(),
                new CollectRequest(dependencies, managedDependencies, Collections.emptyList())));
        assertEquals(1, validateDependencies.get());
        assertEquals(1, validateManagedDependencies.get());
    }
}
