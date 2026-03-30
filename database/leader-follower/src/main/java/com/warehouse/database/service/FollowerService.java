package com.warehouse.database.service;

import com.warehouse.database.model.KeyValue;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * FollowerService handles storage for Follower nodes.
 * Followers:
 * 1. Receive replication updates from the Leader
 * 2. Store data locally in memory
 * 3. Return local data when queried (for R=5 reads)
 */
@Service
public class FollowerService {

    private final ConcurrentHashMap<String, KeyValue> store = new ConcurrentHashMap<>();

    /**
     * Store data received from Leader during replication.
     * Includes a 100ms delay to simulate realistic async replication lag.
     */
    public void storeReplication(KeyValue keyValue) {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        store.put(keyValue.getKey(), keyValue);
        System.out.println("Follower stored: " + keyValue);
    }

    /**
     * Get local value for a key (no delay — used internally).
     */
    public KeyValue getLocal(String key) {
        return store.get(key);
    }

    /**
     * Get local value with 50ms delay.
     * Used during R=5 reads when ReadCoordinator queries this Follower.
     * The delay simulates realistic network + disk read latency.
     */
    public KeyValue getWithDelay(String key) {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return store.get(key);
    }

    public int size() { return store.size(); }
    public void clear() { store.clear(); }
    public boolean containsKey(String key) { return store.containsKey(key); }
}
