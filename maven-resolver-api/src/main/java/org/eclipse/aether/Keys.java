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
package org.eclipse.aether;

import java.util.Arrays;

import static java.util.Objects.requireNonNull;

/**
 * A helper class to create keys, to be used with {@link SessionData} and {@link RepositoryCache} instances as keys.
 * Resolver codebase started with {@link String} keys, that are generally perfect for keys in these constructs, as
 * long as they are used by components loaded once in system. As we saw, some subsystems like transports can be
 * loaded multiple times. For example, in case of Maven, some transport may be present in Maven core, but also loaded up
 * by some extension. In this case, key should distinguish between their {@link ClassLoader}s.
 * <p>
 * It is this class caller responsibility to use proper objects as keys, this class merely helps one to create composite
 * keys out of object instances that are expected to be anyway good candidates as key. Examples of these objects are
 * {@link String} instances but also {@link Class} instances, and many other types also usable as keys.
 * <p>
 * Important: never forget to perform {@code null}-check, when getting cache from {@link RepositorySystemSession#getCache()}
 * method as it may return {@code null}, when cache is disabled session-wise.
 * <p>
 * Historical note: As mentioned above, use of {@link String} instances for keys is perfect match, and it worked from
 * very start. But, as use cases got more and more complex and keys used started to be constructed in more and more
 * sophisticated ways, but were still {@link String} instances. Believe, or not, the sole purpose of string keys
 * was easier debugging. By using this helper class, this convenience should remain.
 *
 * @see RepositorySystemSession#getData()
 * @see SessionData
 * @see RepositorySystemSession#getCache()
 * @see RepositoryCache
 * @since 2.0.19
 */
public final class Keys {
    private Keys() {}

    /**
     * Creates object instance usable as key in session data and cache. Objects passed to this method may or may
     * not implement equals/hashCode, but it is responsibility of caller to understand what is she or he doing.
     * <p>
     * If the first element of key elements is an object instance that was created by this method, creation of
     * "subkeys" happens, where original key elements expanded from first element and are concatenated with new ones.
     * This expansion happens <em>only, if the first key element was already a key created by this class</em>.
     * If the arguments contain only one argument, and it is an object instance that was created by this method,
     * the passed in instance is returned unmodified.
     * <p>
     * Based on what kind of elements are used, one can create multiple kind of keys:
     * <ul>
     *     <li>To create <em>globally matched keys</em>, preferred is to use {@link String} key elements</li>
     *     <li>To create <em>ClassLoader wide matched keys</em>, make sure at least one key element is {@link Class} that needs to be scoped to ClassLoader</li>
     *     <li>To create <em>private, matched by creator only keys</em>, make sure to have one key element, that requires instance equality matching, and there is no other same instance of element</li>
     * </ul>
     *
     * @param keys The key elements, it may not be {@code null} and may not have zero elements.
     * @return An object instance usable as key.
     */
    public static Object of(Object... keys) {
        requireNonNull(keys, "keys cannot be null");
        if (keys.length == 0) {
            throw new IllegalArgumentException("keys must have at least one element");
        } else if (keys[0] instanceof Key) {
            Key head = (Key) keys[0];
            if (keys.length == 1) {
                return head;
            } else {
                return new Key(concat(head.keys, Arrays.copyOfRange(keys, 1, keys.length)));
            }
        } else {
            return new Key(keys);
        }
    }

    private static final class Key {
        private final Object[] keys;
        private final int hashCode;

        private Key(Object[] keys) {
            this.keys = keys.clone();
            this.hashCode = Arrays.hashCode(keys);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (!(obj instanceof Key)) {
                return false;
            }
            Key that = (Key) obj;
            return Arrays.equals(keys, that.keys);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public String toString() {
            return Arrays.toString(keys);
        }
    }

    private static Object[] concat(Object[] one, Object[] two) {
        Object[] result = Arrays.copyOf(one, one.length + two.length);
        System.arraycopy(two, 0, result, one.length, two.length);
        return result;
    }
}
