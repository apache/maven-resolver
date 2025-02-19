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
package org.eclipse.aether.named;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

/**
 * A named lock key.
 * <p>
 * Instances of this class are immutable, implement hash/equals and toString method (for easier debug).
 *
 * @since 2.0.0
 */
public final class NamedLockKey {
    private final String name;

    private final Collection<String> resources;

    private NamedLockKey(String name, Collection<String> resources) {
        this.name = Objects.requireNonNull(name, "name");
        this.resources = Collections.unmodifiableCollection(Objects.requireNonNull(resources, "resources"));
    }

    /**
     * Returns this key name, never {@code null}. This is the identity of this lock key instance and is the only
     * thing that is used in implementations of hash/equals, etc.
     */
    public String name() {
        return name;
    }

    /**
     * Auxiliary information, not used by Resolver. Meant to return resource name(s) or any kind of identifiers
     * protected by this key, never {@code null}.
     * <p>
     * Contents on this field is consumer project specific, and should be used only as "extra information":
     * resolver itself uses these only for logging purposes.
     */
    public Collection<String> resources() {
        return resources;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NamedLockKey that = (NamedLockKey) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + "name='" + name + '\'' + ", resources=" + resources + '}';
    }

    public static NamedLockKey of(String name, String... resources) {
        return of(name, Arrays.asList(resources));
    }

    public static NamedLockKey of(String name, Collection<String> resources) {
        return new NamedLockKey(name, resources);
    }
}
