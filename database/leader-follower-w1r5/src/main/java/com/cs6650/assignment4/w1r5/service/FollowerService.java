package com.cs6650.assignment4.w1r5.service;

import com.cs6650.assignment4.w1r5.model.KeyValue;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * FollowerService handles storage for Follower nodes.
 * Followers:
 * 1. Receive replication updates from the Leader
 * 2. Store data locally in memory
 * 3. Return local data when asked (for R=5 reads)
 */
@Service
public class FollowerService {

    // In-memory storage (thread-safe HashMap)
    // We can't use regular Hashmap because it will crash if two threads access it.
    private final ConcurrentHashMap<String, KeyValue> store = new ConcurrentHashMap<>();

    /**
     * Store data received from Leader during replication.
     * This is called when Leader sends updates to Followers.
     *
     * Assignment requirement: Add 100ms delay when receiving updates.
     *
     * @param keyValue The key-value pair to store
     */
    public void storeReplication(KeyValue keyValue) {
        // Simulate 100ms delay (assignment requirement)
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Store the data
        store.put(keyValue.getKey(), keyValue);

        System.out.println("Follower stored: " + keyValue);
    }

    /**
     * Get local value for a key (no delay for internal use).
     * This is used by the Leader when it needs to read from itself.
     *
     * @param key The key to look up
     * @return The KeyValue if found, null otherwise
     */
    public KeyValue getLocal(String key) {
        return store.get(key);
    }

    /**
     * Get local value for a key with 50ms delay.
     * This is used during R=5 reads when ReadCoordinator queries this Follower.
     *
     * Assignment requirement: Add 50ms delay when Follower responds to reads.
     *
     * @param key The key to look up
     * @return The KeyValue if found, null otherwise
     */
    public KeyValue getWithDelay(String key) {
        // Simulate 50ms delay (assignment requirement)
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return store.get(key);
    }

    // ==========================================
    // UTILITY METHODS (for testing/debugging)
    // ==========================================

    /**
     * Get the size of the local store (for debugging)
     */
    public int size() {
        return store.size();
    }

    /**
     * Clear all data (useful for testing)
     */
    public void clear() {
        store.clear();
    }

    /**
     * Check if a key exists
     */
    public boolean containsKey(String key) {
        return store.containsKey(key);
    }
}