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
package org.eclipse.aether.util.version;

import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance test to demonstrate the benefits of caching in GenericVersionScheme.
 * This test is not run as part of the regular test suite but can be used to verify
 * that caching provides performance benefits.
 */
public class GenericVersionSchemeCachingPerformanceTest {

    @Test
    void testCachingPerformance() {
        GenericVersionScheme scheme = new GenericVersionScheme();

        // Common version strings that would be parsed repeatedly in real scenarios
        String[] commonVersions = {
            "1.0.0",
            "1.0.1",
            "1.0.2",
            "1.1.0",
            "1.1.1",
            "2.0.0",
            "2.0.1",
            "1.0.0-SNAPSHOT",
            "1.1.0-SNAPSHOT",
            "2.0.0-SNAPSHOT",
            "1.0.0-alpha",
            "1.0.0-beta",
            "1.0.0-rc1",
            "1.0.0-final",
            "3.0.0",
            "3.1.0",
            "3.2.0",
            "4.0.0",
            "5.0.0"
        };

        int iterations = 10000;

        // Warm up
        for (int i = 0; i < 1000; i++) {
            for (String version : commonVersions) {
                try {
                    scheme.parseVersion(version);
                } catch (InvalidVersionSpecificationException e) {
                    fail("Unexpected exception during warmup: " + e.getMessage());
                }
            }
        }

        // Test with caching (repeated parsing of same versions)
        long startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            for (String version : commonVersions) {
                try {
                    GenericVersion parsed = scheme.parseVersion(version);
                    assertNotNull(parsed);
                    assertEquals(version, parsed.toString());
                } catch (InvalidVersionSpecificationException e) {
                    fail("Unexpected exception during caching test: " + e.getMessage());
                }
            }
        }
        long cachedTime = System.nanoTime() - startTime;

        // Test without caching (direct instantiation)
        startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            for (String version : commonVersions) {
                GenericVersion parsed = new GenericVersion(version);
                assertNotNull(parsed);
                assertEquals(version, parsed.toString());
            }
        }
        long directTime = System.nanoTime() - startTime;

        System.out.println("Performance Test Results:");
        System.out.println("Cached parsing time: " + (cachedTime / 1_000_000) + " ms");
        System.out.println("Direct instantiation time: " + (directTime / 1_000_000) + " ms");
        System.out.println("Speedup factor: " + String.format("%.2f", (double) directTime / cachedTime));

        // The cached version should be significantly faster for repeated parsing
        // Note: This assertion might be too strict for CI environments, so we use a conservative factor
        assertTrue(
                cachedTime < directTime,
                "Cached parsing should be faster than direct instantiation for repeated versions");
    }

    @Test
    void testCachingCorrectness() {
        GenericVersionScheme scheme = new GenericVersionScheme();

        // Test that caching doesn't affect correctness
        String[] versions = {
            "1.0.0", "1.0.1", "1.1.0", "2.0.0", "1.0.0-SNAPSHOT", "1.0.0-alpha", "1.0.0-beta", "1.0.0-rc1"
        };

        // Parse each version multiple times and verify they're the same instance
        for (String versionStr : versions) {
            try {
                GenericVersion first = scheme.parseVersion(versionStr);
                GenericVersion second = scheme.parseVersion(versionStr);
                GenericVersion third = scheme.parseVersion(versionStr);

                // Should be the same cached instance
                assertSame(first, second, "Second parse should return cached instance");
                assertSame(first, third, "Third parse should return cached instance");

                // Should have correct string representation
                assertEquals(versionStr, first.toString());
                assertEquals(versionStr, second.toString());
                assertEquals(versionStr, third.toString());
            } catch (InvalidVersionSpecificationException e) {
                fail("Unexpected exception for version " + versionStr + ": " + e.getMessage());
            }
        }
    }

    @Test
    void testConcurrentCaching() throws InterruptedException {
        GenericVersionScheme scheme = new GenericVersionScheme();
        String version = "1.0.0";
        int numThreads = 10;
        Thread[] threads = new Thread[numThreads];
        GenericVersion[] results = new GenericVersion[numThreads];

        // Create threads that parse the same version concurrently
        for (int i = 0; i < numThreads; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    results[index] = scheme.parseVersion(version);
                } catch (InvalidVersionSpecificationException e) {
                    throw new RuntimeException("Unexpected exception in thread " + index, e);
                }
            });
        }

        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // All results should be the same cached instance
        GenericVersion first = results[0];
        assertNotNull(first);
        for (int i = 1; i < numThreads; i++) {
            assertSame(first, results[i], "All concurrent parses should return the same cached instance");
        }
    }
}
