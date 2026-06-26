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
package org.eclipse.aether.util.concurrency;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A concurrent cache with weak keys and weak values, inspired by maven-impl's Cache class
 * but stripped down and optimized for hot-path performance.
 * <p>
 * Design compared to the full Cache:
 * <ul>
 *   <li><b>Zero allocation on get()</b> — uses a ThreadLocal reusable lookup key
 *       instead of allocating a new wrapper on every read</li>
 *   <li><b>O(1) cleanup per stale entry</b> — uses identity-based removal from
 *       the ReferenceQueue instead of a full {@code entrySet().removeIf()} scan</li>
 *   <li><b>Lock-free reads</b> — {@link ConcurrentHashMap#get} is a volatile read
 *       with no lock acquisition</li>
 *   <li><b>Lock-striped writes</b> — ConcurrentHashMap uses fine-grained locking</li>
 * </ul>
 * <p>
 * Keys are held via {@link WeakReference}: when a key is no longer strongly referenced
 * elsewhere, its entry becomes eligible for garbage collection. Values are also held
 * via WeakReference. Stale entries are cleaned up lazily on {@link #put}.
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 * @since 2.0.19
 */
public class ConcurrentWeakCache<K, V> {

    private final ConcurrentHashMap<Object, WeakReference<V>> map;
    private final ReferenceQueue<Object> queue = new ReferenceQueue<>();

    @SuppressWarnings("rawtypes")
    private static final ThreadLocal<LookupKey> LOOKUP_KEY = new ThreadLocal<LookupKey>() {
        @Override
        protected LookupKey initialValue() {
            return new LookupKey();
        }
    };

    /**
     * Creates a new cache with default initial capacity.
     */
    public ConcurrentWeakCache() {
        this.map = new ConcurrentHashMap<>();
    }

    /**
     * Creates a new cache with the given initial capacity.
     *
     * @param initialCapacity the initial capacity
     */
    public ConcurrentWeakCache(int initialCapacity) {
        this.map = new ConcurrentHashMap<>(initialCapacity);
    }

    /**
     * Returns the value for the given key, or {@code null} if the key is not present
     * or the value has been garbage collected.
     * <p>
     * This method is lock-free and allocates no objects.
     *
     * @param key the key to look up
     * @return the cached value, or {@code null}
     */
    @SuppressWarnings("unchecked")
    public V get(K key) {
        LookupKey<K> lookupKey = LOOKUP_KEY.get();
        lookupKey.set(key);
        try {
            WeakReference<V> ref = map.get(lookupKey);
            return ref != null ? ref.get() : null;
        } finally {
            lookupKey.clear();
        }
    }

    /**
     * Stores a key-value pair in the cache. Both key and value are held via weak references.
     * <p>
     * Also performs lazy cleanup of entries whose keys have been garbage collected.
     *
     * @param key   the key
     * @param value the value
     */
    public void put(K key, V value) {
        expungeStaleEntries();
        map.put(new WeakKey<>(key, queue), new WeakReference<>(value));
    }

    /**
     * If the key is not already present (or its value has been GC'd), stores the key-value pair
     * and returns the given value. If the key is already present with a live value, returns the
     * existing value without storing.
     * <p>
     * <b>Concurrency note:</b> Under normal operation (no GC activity), concurrent callers for
     * the same key will always receive the same instance. In the rare case where a previously
     * cached value has been garbage-collected and two threads race to replace it, each may
     * return its own value for that single call; subsequent callers will converge on one instance.
     * This is an acceptable trade-off to avoid per-key locking on the hot path.
     * <p>
     * Also performs lazy cleanup of entries whose keys have been garbage collected.
     *
     * @param key   the key
     * @param value the value to store if absent
     * @return the existing value if present, or the given value if newly stored
     */
    public V putIfAbsent(K key, V value) {
        // Fast path: lock-free lookup, zero allocation (ThreadLocal LookupKey)
        V existing = get(key);
        if (existing != null) {
            return existing;
        }
        // Slow path: allocate WeakKey + WeakReference and insert
        expungeStaleEntries();
        WeakReference<V> newRef = new WeakReference<>(value);
        WeakReference<V> existingRef = map.putIfAbsent(new WeakKey<>(key, queue), newRef);
        if (existingRef != null) {
            V existingValue = existingRef.get();
            if (existingValue != null) {
                return existingValue;
            }
            // Existing entry's value was GC'd — overwrite with new value.
            // Benign race: two threads may both reach here and each return their own value;
            // all subsequent callers converge on whichever thread's put() wins.
            map.put(new WeakKey<>(key, queue), newRef);
        }
        return value;
    }

    /**
     * Returns the number of entries in the cache (including possibly stale ones).
     *
     * @return the cache size
     */
    public int size() {
        return map.size();
    }

    /**
     * Removes entries whose weak-reference keys have been garbage collected.
     * Called automatically on {@link #put}. Each stale entry is removed in O(1)
     * via identity match — no map scan required.
     */
    private void expungeStaleEntries() {
        Object staleKey;
        while ((staleKey = queue.poll()) != null) {
            // ConcurrentHashMap.remove() checks (storedKey == staleKey) first.
            // Since the staleKey IS the same WeakKey object that was stored in
            // the map, identity matches and the entry is removed in O(1).
            map.remove(staleKey);
        }
    }

    /**
     * Reusable lookup key stored in a ThreadLocal for zero-allocation reads.
     * Used only for {@link ConcurrentHashMap#get} lookups, never stored in the map.
     * <p>
     * Implements equals/hashCode to match {@link WeakKey} entries in the map:
     * ConcurrentHashMap.get(lookupKey) calls {@code lookupKey.equals(storedWeakKey)},
     * which delegates to {@code key.equals(storedWeakKey.get())}.
     */
    static class LookupKey<K> {
        private K key;
        private int hash;

        void set(K key) {
            this.key = key;
            this.hash = key.hashCode();
        }

        void clear() {
            this.key = null; // prevent leak via ThreadLocal
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        @SuppressWarnings("rawtypes")
        public boolean equals(Object o) {
            if (o instanceof WeakKey) {
                Object otherKey = ((WeakKey) o).get();
                return key != null && key.equals(otherKey);
            }
            return false;
        }
    }

    /**
     * Weak-reference key stored in the map. When the referent is garbage collected,
     * this object is enqueued in the {@link ReferenceQueue} for cleanup.
     * <p>
     * The cleanup path uses identity: {@code map.remove(staleWeakKey)} matches because
     * ConcurrentHashMap checks {@code storedKey == staleWeakKey} before calling equals().
     * Since the queued WeakKey IS the same object that's in the map, this is always true.
     */
    static class WeakKey<K> extends WeakReference<K> {
        private final int hash;

        @SuppressWarnings("unchecked")
        WeakKey(K key, ReferenceQueue<? super K> queue) {
            super(key, (ReferenceQueue<? super K>) queue);
            this.hash = key.hashCode();
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        @SuppressWarnings("rawtypes")
        public boolean equals(Object o) {
            if (this == o) {
                return true; // identity match — used for cleanup and normal dedup
            }
            K k = get();
            if (k == null) {
                return false; // referent GC'd
            }
            if (o instanceof WeakKey) {
                return k.equals(((WeakKey) o).get());
            }
            if (o instanceof LookupKey) {
                return k.equals(((LookupKey) o).key);
            }
            return false;
        }
    }
}
