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
public class MMap<K, V> {
    private static final MMap<?, ?> EMPTY_MAP = new MMap<>(new HashMap<>(0)).done();

    @SuppressWarnings("unchecked")
    public static <K, V> MMap<K, V> empty() {
        return (MMap<K, V>) MMap.EMPTY_MAP;
    }

    public static <K, V> MMap<K, V> copy(MMap<K, V> orig) {
        return new MMap<>(new HashMap<>(orig.delegate));
    }

    protected final HashMap<K, V> delegate;

    private MMap(HashMap<K, V> delegate) {
        this.delegate = delegate;
    }

    public boolean containsKey(K key) {
        return delegate.containsKey(key);
    }

    public V get(K key) {
        return delegate.get(key);
    }

    public V put(K key, V value) {
        return delegate.put(key, value);
    }

    public MMap<K, V> done() {
        return new DoneMMap<>(delegate);
    }

    @Override
    public int hashCode() {
        throw new IllegalStateException("MMap is not done yet");
    }

    @Override
    public boolean equals(Object o) {
        throw new IllegalStateException("MMap is not done yet");
    }

    private static class DoneMMap<K, V> extends MMap<K, V> {
        private volatile long hashCode = Long.MAX_VALUE;

        private DoneMMap(HashMap<K, V> delegate) {
            super(delegate);
        }

        @Override
        public V put(K key, V value) {
            throw new IllegalStateException("Done MMap is immutable");
        }

        @Override
        public MMap<K, V> done() {
            return this;
        }

        @Override
        public int hashCode() {
            if (this.hashCode != Long.MAX_VALUE) {
                return (int) hashCode;
            }
            int result = delegate.hashCode();
            this.hashCode = result;
            return result;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof MMap)) {
                return false;
            }
            MMap<?, ?> other = (MMap<?, ?>) o;
            return delegate.equals(other.delegate);
        }
    }
}
