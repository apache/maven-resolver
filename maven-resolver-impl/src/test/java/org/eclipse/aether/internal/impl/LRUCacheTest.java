package org.eclipse.aether.internal.impl;

import static org.junit.Assert.*;

import org.junit.Test;

public class LRUCacheTest {
    
    @Test
    public void testSizeBound( ){
        LRUCache<String, Integer> cache = new LRUCache<>(10, 0);
        for(int i = 0;i<20;i++) {
            cache.put("#"+i, i);
        }
        assertEquals(10, cache.size());
        assertEquals(null, cache.get("#0")); // expunged
        assertEquals(Integer.valueOf(10), cache.get("#10")); // not expunged
        
        // second time its the same
        for(int i = 0;i<20;i++) {
            cache.put("#"+i, i);
        }
        assertEquals(10, cache.size());
        assertEquals(null, cache.get("#0")); // expunged
        assertEquals(Integer.valueOf(10), cache.get("#10")); // not expunged
        
        // now we are going to 'touch' #0 to prevent expunging
        for(int i = 0;i<20;i++) {
            cache.put("#"+i, i);
            cache.get("#0");
        }
        assertEquals(10, cache.size());
        assertEquals(Integer.valueOf(0), cache.get("#0")); // not expunged
        assertEquals(null, cache.get("#10")); // expunged 

    }
    
    @Test
    public void testTimeBound( ) throws InterruptedException{
        LRUCache<String, Integer> cache = new LRUCache<>(0, 100);
        cache.put("#10", 10);
        assertEquals(1, cache.size());
        assertEquals(Integer.valueOf(10), cache.get("#10")); // not expired
        assertEquals(1, cache.size());
        Thread.sleep(50);
        assertEquals(1, cache.size());
        assertEquals(Integer.valueOf(10), cache.get("#10")); // not expired
        assertEquals(1, cache.size());
        Thread.sleep(200);
        assertEquals(1, cache.size());
        assertEquals(null, cache.get("#10")); // expired
        assertEquals(1, cache.size());
        Thread.sleep(10000);
        assertEquals(1, cache.size()); // not expunged yet
        assertEquals(null, cache.get("#10")); // expired
        assertEquals(0, cache.size()); // and now expunged
        
    }

}
