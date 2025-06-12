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
package org.eclipse.aether.util.graph.manager;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Warning: this is a special map-like construct that suits only and should be used only in this package!
 * It has the following properties:
 * <ul>
 *     <ul>memorizes once calculated hashCode</ul>
 *     <ul>once hashCode calculated, goes into "read only" mode (put method will fail)</ul>
 *     <ul>otherwise all the rest is same as for {@link HashMap}</ul>
 * </ul>
 *
 * This class is not a generic map class; only those methods are "protected" that are in use in this very
 * package.
 *
 * @param <K>
 * @param <V>
 */
public interface MMap<K, V> {
    @SuppressWarnings("unchecked")
    static <K, V> MMap<K, V> empty() {
        return (MMap<K, V>) MMapImpl.EMPTY_MAP;
    }

    static <K, V> MMap<K, V> copy(MMap<K, V> orig) {
        return new MMapImpl<>(orig);
    }

    boolean containsKey(K key);

    V get(K key);

    V put(K key, V value);

    @Override
    int hashCode();

    @Override
    boolean equals(Object o);

    class MMapImpl<K, V> extends HashMap<K, V> implements MMap<K, V> {
        private static final MMap<?, ?> EMPTY_MAP = new MMapImpl<>();

        private final ConcurrentHashMap<String, Object> cache = new ConcurrentHashMap<>();

        private MMapImpl() {}

        @SuppressWarnings("unchecked")
        private MMapImpl(MMap<? extends K, ? extends V> m) {
            super((MMapImpl) m);
        }

        @Override
        public boolean containsKey(Object key) {
            return super.containsKey(key);
        }

        @Override
        public V get(Object key) {
            return super.get(key);
        }

        @Override
        public V put(K key, V value) {
            if (!cache.isEmpty()) {
                throw new IllegalStateException("MMap is immutable");
            }
            return super.put(key, value);
        }

        @Override
        public int hashCode() {
            return (Integer) cache.computeIfAbsent("hashCode", k -> super.hashCode());
        }

        @Override
        public boolean equals(Object o) {
            return super.equals(o);
        }
    }
}
