package com.cs6650.assignment4.w1r5.service;

import com.cs6650.assignment4.w1r5.config.NodeConfig;
import com.cs6650.assignment4.w1r5.model.KeyValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import java.util.ArrayList;


/**
 * LeaderService handles writes for the Leader node in W=1, R=5 strategy.
 *
 * W=1 means: Write to 1 node (Leader) before responding to client.
 * After responding, replicate to Followers asynchronously in background.
 */
@Service
public class LeaderService {

    // In-memory storage
    private final ConcurrentHashMap<String, KeyValue> store = new ConcurrentHashMap<>();

    // Version counter (thread-safe, increments for each write)
    private final AtomicLong versionCounter = new AtomicLong(0);

    // Configuration (to know where Followers are)
    private final NodeConfig nodeConfig;

    // HTTP client (to send requests to Followers)
    private final RestTemplate restTemplate;

    // Store for registered follower URLs
    private final ConcurrentHashMap<Integer, String> registeredFollowers = new ConcurrentHashMap<>();

    @Autowired
    public LeaderService(NodeConfig nodeConfig) {
        this.nodeConfig = nodeConfig;
        this.restTemplate = new RestTemplate();
    }

    // Method to register a follower
    public void registerFollower(Integer nodeId, String followerUrl) {
      registeredFollowers.put(nodeId, followerUrl);
      System.out.println("LeaderService: Registered follower " + nodeId + " at " + followerUrl);
    }

    // Method to get all registered follower URLs
    public List<String> getRegisteredFollowerUrls() {
      return new ArrayList<>(registeredFollowers.values());
    }

    // Method to check how many followers are registered
    public int getRegisteredFollowerCount() {
      return registeredFollowers.size();
    }
    /**
     * Handle write from client (W=1 strategy).
     *
     * Steps:
     * 1. Increment version
     * 2. Store locally on Leader
     * 3. Return IMMEDIATELY (W=1!)
     * 4. Replicate to Followers in background thread
     *
     * @param key The key to write
     * @param value The value to store
     * @return The KeyValue that was stored
     */
    public KeyValue handleWrite(String key, String value) {
        // Step 1: Get next version number (thread-safe increment)
        long newVersion = versionCounter.incrementAndGet();

        // Step 2: Create KeyValue with new version
        KeyValue kv = new KeyValue(key, value, newVersion);

        // Step 3: Store locally on Leader
        store.put(key, kv);

        System.out.println("Leader stored locally: " + kv);

        // Step 4: Start background replication (don't wait!)
        replicateToFollowersAsync(kv);

        // Step 5: Return immediately (W=1 - only Leader updated)
        return kv;
    }

    /**
     * Get local value from Leader (no delay).
     * Used for immediate reads from Leader itself.
     *
     * @param key The key to look up
     * @return The KeyValue if found, null otherwise
     */
    public KeyValue getLocal(String key) {
        return store.get(key);
    }

    // ==========================================
    // REPLICATION LOGIC
    // ==========================================

    /**
     * Replicate to all Followers asynchronously (in background).
     * This runs in a separate thread so it doesn't block the client response.
     *
     * Assignment requirements:
     * - Add 200ms delay AFTER sending to each Follower
     * - Send to all 4 Followers
     *
     * @param kv The KeyValue to replicate
     */
    private void replicateToFollowersAsync(KeyValue kv) {
        // Create a new thread for replication (so we don't block)
        new Thread(() -> {
            System.out.println("Starting background replication for: " + kv.getKey());

            // Get URLs of all registered Followers (dynamic list from AWS ECS)
            for (String followerUrl : getRegisteredFollowerUrls()) {
                try {
                    // Send replication to this Follower
                    sendReplicationToFollower(followerUrl, kv);

                    System.out.println("Replicated to: " + followerUrl);

                    // Assignment requirement: 200ms delay after each send
                    Thread.sleep(200);

                } catch (Exception e) {
                    System.err.println("Failed to replicate to " + followerUrl + ": " + e.getMessage());
                    // Continue with next Follower even if this one fails
                }
            }

            System.out.println("Replication complete for: " + kv.getKey());
        }).start();  // Start the thread (runs in background)
    }

    /**
     * Send HTTP POST request to a single Follower.
     * The Follower's /replicate endpoint will receive this.
     *
     * @param followerUrl The base URL of the Follower (e.g., http://localhost:8081)
     * @param kv The KeyValue to send
     */
    private void sendReplicationToFollower(String followerUrl, KeyValue kv) {
        String url = followerUrl + "/api/replicate";

        // Send POST request with KeyValue in body
        restTemplate.postForObject(url, kv, String.class);
    }

    // ==========================================
    // UTILITY METHODS (for testing/debugging)
    // ==========================================

    /**
     * Get the current version counter value
     */
    public long getCurrentVersion() {
        return versionCounter.get();
    }

    /**
     * Get the size of the local store
     */
    public int size() {
        return store.size();
    }

    /**
     * Clear all data (useful for testing)
     */
    public void clear() {
        store.clear();
        versionCounter.set(0);
    }
}