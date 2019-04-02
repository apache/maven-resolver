package org.eclipse.aether.internal.impl;

import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Simple LRU cache with size and time bounds.
 * 
 * Semantics are: If ttl is positive than entries will be expired after given
 * ttl (no matter if they are read or not). If maxSize is positive than those
 * number of entries will be never exceeded. Expiry of entry is effectively
 * signalled by returning null value from the cache, although the cache does not
 * decrease in size immediately, as the implementation will expunge expired
 * entires once per 10seconds.
 *
 * The implementation keeps a simple unordered hash map of cached entries plus a
 * double-ended linked list that is reordered upon put/get operations. The tail
 * of list is the least recently used element. The head is the most recently
 * used element.
 * 
 * <strong>Class is not thread safe. Use
 * {@link Collections#synchronizedMap(Map)}.</strong>
 * 
 * <strong>The iteration order of views is unspecified - it is not according to
 * usage</strong>
 * 
 * <strong>Iteration over views should not be mixed with get/put/remove/clear
 * operations on the map as it will lead to
 * {@link ConcurrentModificationException}</strong>
 * 
 * <strong>Values of elements during iteration may become expired and they will
 * turn into null, however the expunging process is not triggered during
 * iteration.</strong>
 *
 * @param <K> key type, it should follow {@link HashMap} semantics
 * @param <V> value type, any object will do (only {@link Object#equals(Object)}
 *        is used to provide {@link #containsValue(Object)})
 */
public class LRUCache<K, V> implements Map<K, V> {

    // expunge expired elements once per 10s
    private static final int EXPUNGE_TIMEOUT = 10000;

    // internal entry in the cache
    static class E<K, V> {

        // key of entry
        K key;

        // valu ef entry
        V value;

        // timestamp of modification (not updated on access)
        long ts;

        // next element in linked list
        E<K, V> next;

        // previous element in linked list
        E<K, V> prev;

        public E(K key, V value) {
            super();
            this.key = key;
            this.value = value;
            this.ts = System.currentTimeMillis();
        }

        public boolean isExpired(int ttl) {
            return ttl > 0 && System.currentTimeMillis() - this.ts > ttl;
        }

        public V update(V value, int ttl) {
            V oldValue = getValue(ttl);
            this.value = value;
            this.ts = System.currentTimeMillis();
            return oldValue;
        }

        public V getValue(int ttl) {
            if (!isExpired(ttl)) {
                return this.value;
            } else {
                return null;
            }
        }

        // just to provide containsValue
        @Override
        public boolean equals(Object obj) {
            if (obj instanceof E) {
                E e = (E) obj;
                return Objects.equals(e.value, this.value);
            } else {
                return false;
            }
        }

        public K getKey() {
            return key;
        }

    }

    // maximum size, must be positive to be effective
    int maxSize;

    // maximum time to live, must be positive to be effective
    int ttl;

    // last timestamp of expiry check on elements
    long lastExpungeTs;

    // actual cache entries
    Map<K, E<K, V>> map;

    // linked list head
    E<K, V> head;

    // linked list tail
    E<K, V> tail;

    /**
     * Construct new cache instance optionally bounded in size and ttl of elements.
     * 
     * @param maxSize maximum size or 0 for unlimited
     * @param ttl     maximum ttl of elements in ms or 0 for unlimited
     */
    public LRUCache(int maxSize, int ttl) {
        this.lastExpungeTs = System.currentTimeMillis();
        this.maxSize = maxSize;
        this.ttl = ttl;
        this.map = new HashMap<>();
    }

    @Override
    public V put(K key, V value) {
        E<K, V> entry = map.get(key);
        V oldValue = null;
        if (entry != null) {
            oldValue = entry.update(value, ttl);
            LRUListSetHead(entry);
        } else {
            entry = new E<>(key, value);
            map.put(key, entry);
            LRUListSetHead(entry);
            expungeLRU();
        }

        expungeExpired();

        return oldValue;
    }

    /**
     * Remove LRU elements so that size < maxSize (unless maxSize is 0)
     */
    private void expungeLRU() {
        if (maxSize > 0 && map.size() > maxSize) {
            map.remove(tail.key);
            LRUListRemove(tail);
        }
    }

    /**
     * Remove expired elements so that their ts is not older than given ttl (unless
     * ttl = 0).
     */
    private void expungeExpired() {
        if (ttl > 0) {
            long now = System.currentTimeMillis();
            if (lastExpungeTs + EXPUNGE_TIMEOUT < now) {
                lastExpungeTs = now;
                for (Iterator<Entry<K, E<K, V>>> it = map.entrySet().iterator(); it.hasNext();) {
                    Entry<K, E<K, V>> mapEntry = it.next();
                    E<K, V> cacheEntry = mapEntry.getValue();
                    if (cacheEntry.isExpired(ttl)) {
                        LRUListRemove(cacheEntry);
                        it.remove();
                    }
                }
            }
        }
    }

    /**
     * Remove element from LRU list.
     * 
     * @param e
     */
    private void LRUListRemove(E<K, V> e) {
        if (e.next != null) {
            e.next.prev = e.prev;
        }
        if (e.prev != null) {
            e.prev.next = e.next;
        }
        if (e == head) {
            head = e.next;
        }
        if (e == tail) {
            tail = e.prev;
        }
        e.next = null;
        e.prev = null;
    }

    /**
     * Set given element as head of LRU list.
     * 
     * @param e
     */
    private void LRUListSetHead(E<K, V> e) {
        // first remove from LRU list (could check if its already head but its
        // unlikely).
        LRUListRemove(e);
        // if existing head then update links
        if (head != null) {
            e.next = head;
            head.prev = e;
        }
        head = e;
        // set also tail if not present
        if (tail == null) {
            tail = e;
        }
    }

    @Override
    public V get(Object key) {
        E<K, V> entry = map.get(key);
        if (entry != null) {
            LRUListSetHead(entry);
            V value = entry.getValue(ttl);
            expungeExpired();
            return value;
        } else {
            expungeExpired();
            return null;
        }
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean containsKey(Object key) {
        return map.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return map.containsValue(value);
    }

    @Override
    public V remove(Object key) {
        E<K, V> el = map.remove(key);
        if (el != null) {
            LRUListRemove(el);
        }
        return el != null ? el.getValue(ttl) : null;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        for (Entry<? extends K, ? extends V> mapEntry : m.entrySet()) {
            put(mapEntry.getKey(), mapEntry.getValue());
        }
    }

    @Override
    public void clear() {
        map.clear();
        // clear LRU list
        head = null;
        tail = null;
        lastExpungeTs = System.currentTimeMillis();
    }

    @Override
    public Set<K> keySet() {
        expungeExpired();
        return Collections.unmodifiableSet(map.keySet());
    }

    @Override
    public Collection<V> values() {
        expungeExpired();
        return new AbstractCollection<V>() {

            @Override
            public Iterator<V> iterator() {
                return new Iterator<V>() {

                    Iterator<E<K, V>> it = map.values().iterator();

                    @Override
                    public boolean hasNext() {
                        return it.hasNext();
                    }

                    @Override
                    public V next() {
                        E<K, V> e = it.next();
                        return e.getValue(ttl);
                    }

                };
            }

            @Override
            public int size() {
                return map.size();
            }
        };
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        expungeExpired();
        return new AbstractSet<Entry<K, V>>() {

            @Override
            public Iterator<Entry<K, V>> iterator() {
                return new Iterator<Map.Entry<K, V>>() {
                    Iterator<E<K, V>> it = map.values().iterator();

                    @Override
                    public boolean hasNext() {
                        return it.hasNext();
                    }

                    @Override
                    public Entry<K, V> next() {
                        final E<K, V> entry = it.next();
                        return new Map.Entry<K, V>() {

                            @Override
                            public K getKey() {
                                return entry.getKey();
                            }

                            @Override
                            public V getValue() {
                                return entry.getValue(ttl);
                            }

                            @Override
                            public V setValue(V value) {
                                V oldValue = entry.update(value, ttl);
                                LRUListSetHead(entry);
                                return oldValue;
                            }
                        };
                    }

                };

            }

            @Override
            public int size() {
                return map.size();
            }

        };
    }
}
