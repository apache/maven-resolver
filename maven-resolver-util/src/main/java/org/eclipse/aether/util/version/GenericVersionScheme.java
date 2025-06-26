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

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.version.InvalidVersionSpecificationException;

/**
 * A version scheme using a generic version syntax and common sense sorting.
 * <p>
 * This scheme accepts versions of any form, interpreting a version as a sequence of numeric and alphabetic segments.
 * The characters '-', '_', and '.' as well as the mere transitions from digit to letter and vice versa delimit the
 * version segments. Delimiters are treated as equivalent.
 * </p>
 * <p>
 * Numeric segments are compared mathematically, alphabetic segments are compared lexicographically and
 * case-insensitively. However, the following qualifier strings are recognized and treated specially: "alpha" = "a" &lt;
 * "beta" = "b" &lt; "milestone" = "m" &lt; "cr" = "rc" &lt; "snapshot" &lt; "final" = "ga" &lt; "sp". All of those
 * well-known qualifiers are considered smaller/older than other strings. An empty segment/string is equivalent to 0.
 * </p>
 * <p>
 * In addition to the above mentioned qualifiers, the tokens "min" and "max" may be used as final version segment to
 * denote the smallest/greatest version having a given prefix. For example, "1.2.min" denotes the smallest version in
 * the 1.2 line, "1.2.max" denotes the greatest version in the 1.2 line. A version range of the form "[M.N.*]" is short
 * for "[M.N.min, M.N.max]".
 * </p>
 * <p>
 * Numbers and strings are considered incomparable against each other. Where version segments of different kind would
 * collide, comparison will instead assume that the previous segments are padded with trailing 0 or "ga" segments,
 * respectively, until the kind mismatch is resolved, e.g. "1-alpha" = "1.0.0-alpha" &lt; "1.0.1-ga" = "1.0.1".
 * </p>
 */
public class GenericVersionScheme extends VersionSchemeSupport {

    // Using WeakHashMap wrapped in synchronizedMap for thread safety and memory-sensitive caching
    private final Map<String, GenericVersion> versionCache = Collections.synchronizedMap(new WeakHashMap<>());

    // Cache statistics
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong totalRequests = new AtomicLong(0);

    // Static statistics across all instances
    private static final AtomicLong GLOBAL_CACHE_HITS = new AtomicLong(0);
    private static final AtomicLong GLOBAL_CACHE_MISSES = new AtomicLong(0);
    private static final AtomicLong GLOBAL_TOTAL_REQUESTS = new AtomicLong(0);
    private static final AtomicLong INSTANCE_COUNT = new AtomicLong(0);

    static {
        // Register shutdown hook to print statistics if enabled
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (isStatisticsEnabled()) {
                printGlobalStatistics();
            }
        }));
    }

    public GenericVersionScheme() {
        INSTANCE_COUNT.incrementAndGet();
    }

    /**
     * Checks if version scheme cache statistics should be printed.
     * This checks both the system property and the configuration property.
     */
    private static boolean isStatisticsEnabled() {
        // Check system property first (for backwards compatibility and ease of use)
        String sysProp = System.getProperty(ConfigurationProperties.VERSION_SCHEME_CACHE_DEBUG);
        if (sysProp != null) {
            return Boolean.parseBoolean(sysProp);
        }

        // Default to false if not configured
        return ConfigurationProperties.DEFAULT_VERSION_SCHEME_CACHE_DEBUG;
    }

    @Override
    public GenericVersion parseVersion(final String version) throws InvalidVersionSpecificationException {
        totalRequests.incrementAndGet();
        GLOBAL_TOTAL_REQUESTS.incrementAndGet();

        GenericVersion existing = versionCache.get(version);
        if (existing != null) {
            cacheHits.incrementAndGet();
            GLOBAL_CACHE_HITS.incrementAndGet();
            return existing;
        } else {
            cacheMisses.incrementAndGet();
            GLOBAL_CACHE_MISSES.incrementAndGet();
            return versionCache.computeIfAbsent(version, GenericVersion::new);
        }
    }

    /**
     * Get cache statistics for this instance.
     */
    public String getCacheStatistics() {
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long total = totalRequests.get();
        double hitRate = total > 0 ? (double) hits / total * 100.0 : 0.0;

        return String.format(
                "GenericVersionScheme Cache Stats: hits=%d, misses=%d, total=%d, hit-rate=%.2f%%, cache-size=%d",
                hits, misses, total, hitRate, versionCache.size());
    }

    /**
     * Print global statistics across all instances.
     */
    private static void printGlobalStatistics() {
        long hits = GLOBAL_CACHE_HITS.get();
        long misses = GLOBAL_CACHE_MISSES.get();
        long total = GLOBAL_TOTAL_REQUESTS.get();
        long instances = INSTANCE_COUNT.get();
        double hitRate = total > 0 ? (double) hits / total * 100.0 : 0.0;

        System.err.println("=== GenericVersionScheme Global Cache Statistics (WeakHashMap) ===");
        System.err.println(String.format("Total instances created: %d", instances));
        System.err.println(String.format("Total requests: %d", total));
        System.err.println(String.format("Cache hits: %d", hits));
        System.err.println(String.format("Cache misses: %d", misses));
        System.err.println(String.format("Hit rate: %.2f%%", hitRate));
        System.err.println(
                String.format("Average requests per instance: %.2f", instances > 0 ? (double) total / instances : 0.0));
        System.err.println("=== End Cache Statistics ===");
    }

    /**
     * A handy main method that behaves similarly like maven-artifact ComparableVersion is, to make possible test
     * and possibly compare differences between the two.
     * <p>
     * To check how "1.2.7" compares to "1.2-SNAPSHOT", for example, you can issue
     * <pre>jbang --main=org.eclipse.aether.util.version.GenericVersionScheme org.apache.maven.resolver:maven-resolver-util:1.9.18 "1.2.7" "1.2-SNAPSHOT"</pre>
     * command to command line, output is very similar to that of ComparableVersion on purpose.
     */
    public static void main(String... args) {
        System.out.println(
                "Display parameters as parsed by Maven Resolver 'generic' scheme (in canonical form and as a list of tokens)"
                        + " and comparison result:");
        if (args.length == 0) {
            return;
        }

        GenericVersionScheme scheme = new GenericVersionScheme();
        GenericVersion prev = null;
        int i = 1;
        for (String version : args) {
            try {
                GenericVersion c = scheme.parseVersion(version);

                if (prev != null) {
                    int compare = prev.compareTo(c);
                    System.out.println(
                            "   " + prev + ' ' + ((compare == 0) ? "==" : ((compare < 0) ? "<" : ">")) + ' ' + version);
                }

                System.out.println((i++) + ". " + version + " -> " + c.asString() + "; tokens: " + c.asItems());

                prev = c;
            } catch (InvalidVersionSpecificationException e) {
                System.err.println("Invalid version: " + version + " - " + e.getMessage());
            }
        }
    }
}
