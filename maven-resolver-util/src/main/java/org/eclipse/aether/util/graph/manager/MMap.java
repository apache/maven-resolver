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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

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

    public static <K, V> MMap<K, Collection<V>> copyWithListValue(MMap<K, Collection<V>> orig) {
        HashMap<K, Collection<V>> delegateLocal = orig.delegate;
        HashMap<K, Collection<V>> newMap = new HashMap<>((int) Math.ceil(delegateLocal.size() / 0.75D));
        for (Map.Entry<K, Collection<V>> entry : delegateLocal.entrySet()) {
            newMap.put(entry.getKey(), entry.getValue() == null ? null : new ArrayList<>(entry.getValue()));
        }
        return new MMap<>(newMap);
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
        private final int hashCode;

        private DoneMMap(HashMap<K, V> delegate) {
            super(delegate);
            this.hashCode = delegate.hashCode();
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
            return hashCode;
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
