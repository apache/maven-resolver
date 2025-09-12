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
package org.eclipse.aether.util.filter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.util.artifact.JavaScopes;

/**
 * A utility class assisting in the creation of dependency node filters.
 */
public final class DependencyFilterUtils {

    private DependencyFilterUtils() {
        // hide constructor
    }

    /**
     * Creates a new filter that negates the specified filter.
     *
     * @param filter the filter to negate, must not be {@code null}
     * @return the new filter, never {@code null}
     */
    public static DependencyFilter notFilter(DependencyFilter filter) {
        return new NotDependencyFilter(filter);
    }

    /**
     * Creates a new filter that combines the specified filters using a logical {@code AND}. If no filters are
     * specified, the resulting filter accepts everything.
     *
     * @param filters the filters to combine, may be {@code null}
     * @return the new filter, never {@code null}
     */
    public static DependencyFilter andFilter(DependencyFilter... filters) {
        if (filters != null && filters.length == 1) {
            return filters[0];
        } else {
            return new AndDependencyFilter(filters);
        }
    }

    /**
     * Creates a new filter that combines the specified filters using a logical {@code AND}. If no filters are
     * specified, the resulting filter accepts everything.
     *
     * @param filters the filters to combine, may be {@code null}
     * @return the new filter, never {@code null}
     */
    public static DependencyFilter andFilter(Collection<DependencyFilter> filters) {
        if (filters != null && filters.size() == 1) {
            return filters.iterator().next();
        } else {
            return new AndDependencyFilter(filters);
        }
    }

    /**
     * Creates a new filter that combines the specified filters using a logical {@code OR}. If no filters are specified,
     * the resulting filter accepts nothing.
     *
     * @param filters the filters to combine, may be {@code null}
     * @return the new filter, never {@code null}
     */
    public static DependencyFilter orFilter(DependencyFilter... filters) {
        if (filters != null && filters.length == 1) {
            return filters[0];
        } else {
            return new OrDependencyFilter(filters);
        }
    }

    /**
     * Creates a new filter that combines the specified filters using a logical {@code OR}. If no filters are specified,
     * the resulting filter accepts nothing.
     *
     * @param filters the filters to combine, may be {@code null}
     * @return the new filter, never {@code null}
     */
    public static DependencyFilter orFilter(Collection<DependencyFilter> filters) {
        if (filters != null && filters.size() == 1) {
            return filters.iterator().next();
        } else {
            return new OrDependencyFilter(filters);
        }
    }

    /**
     * Creates a new filter that selects dependencies whose scope matches one or more of the specified classpath types.
     * A classpath type is a set of scopes separated by either {@code ','} or {@code '+'}.
     *
     * @param classpathTypes the classpath types, may be {@code null} or empty to match no dependency
     * @return the new filter, never {@code null}
     * @see JavaScopes
     * @deprecated resolver is oblivious about "scopes", it is consumer project which needs to lay these down and
     * also assign proper semantics. Moreover, Resolver is oblivious about notions of "classpath", "modulepath", and
     * any other similar uses. These should be handled by consumer project.
     */
    @Deprecated
    public static DependencyFilter classpathFilter(String... classpathTypes) {
        return classpathFilter((classpathTypes != null) ? Arrays.asList(classpathTypes) : null);
    }

    /**
     * Creates a new filter that selects dependencies whose scope matches one or more of the specified classpath types.
     * A classpath type is a set of scopes separated by either {@code ','} or {@code '+'}.
     *
     * @param classpathTypes the classpath types, may be {@code null} or empty to match no dependency
     * @return the new filter, never {@code null}
     * @see JavaScopes
     * @deprecated resolver is oblivious about "scopes", it is consumer project which needs to lay these down and
     * also assign proper semantics. Moreover, Resolver is oblivious about notions of "classpath", "modulepath", and
     * any other similar uses. These should be handled by consumer project.
     */
    @Deprecated
    public static DependencyFilter classpathFilter(Collection<String> classpathTypes) {
        Collection<String> types = new HashSet<>();

        if (classpathTypes != null) {
            for (String classpathType : classpathTypes) {
                String[] tokens = classpathType.split("[+,]");
                for (String token : tokens) {
                    token = token.trim();
                    if (!token.isEmpty()) {
                        types.add(token);
                    }
                }
            }
        }

        Collection<String> included = new HashSet<>();
        for (String type : types) {
            if (JavaScopes.COMPILE.equals(type)) {
                Collections.addAll(included, JavaScopes.COMPILE, JavaScopes.PROVIDED, JavaScopes.SYSTEM);
            } else if (JavaScopes.RUNTIME.equals(type)) {
                Collections.addAll(included, JavaScopes.COMPILE, JavaScopes.RUNTIME);
            } else if (JavaScopes.TEST.equals(type)) {
                Collections.addAll(
                        included,
                        JavaScopes.COMPILE,
                        JavaScopes.PROVIDED,
                        JavaScopes.SYSTEM,
                        JavaScopes.RUNTIME,
                        JavaScopes.TEST);
            } else {
                included.add(type);
            }
        }

        Collection<String> excluded = new HashSet<>();
        Collections.addAll(
                excluded,
                JavaScopes.COMPILE,
                JavaScopes.PROVIDED,
                JavaScopes.SYSTEM,
                JavaScopes.RUNTIME,
                JavaScopes.TEST);
        excluded.removeAll(included);

        return new ScopeDependencyFilter(null, excluded);
    }
}
