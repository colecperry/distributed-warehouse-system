package com.cs6650.assignment4.w1r5.service;

import com.cs6650.assignment4.w1r5.config.NodeConfig;
import com.cs6650.assignment4.w1r5.model.KeyValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;

/**
 * ReadCoordinator implements R=5 read logic.
 *
 * R=5 means: Read from ALL 5 nodes and return the value with highest version.
 * This ensures you always get the most recent data, even if some Followers
 * haven't been updated yet (inconsistency window).
 */
@Service
public class ReadCoordinator {

    private final NodeConfig nodeConfig;
    private final LeaderService leaderService;
    private final RestTemplate restTemplate;

    // Thread pool for parallel reads (reuse threads for efficiency)
    private final ExecutorService executor;

    @Autowired
    public ReadCoordinator(NodeConfig nodeConfig, LeaderService leaderService) {
        this.nodeConfig = nodeConfig;
        this.leaderService = leaderService;
        this.restTemplate = new RestTemplate();

        // Create thread pool with 4 threads (one for each Follower)
        this.executor = Executors.newFixedThreadPool(4);
    }

    // ==========================================
    // R=5 READ LOGIC
    // ==========================================

    /**
     * Read from ALL 5 nodes and return the most recent version (R=5).
     *
     * Steps:
     * 1. Read from Leader (local, no network call)
     * 2. Read from all 4 Followers in parallel (network calls)
     * 3. Compare versions
     * 4. Return KeyValue with highest version
     *
     * @param key The key to read
     * @return The most recent KeyValue, or null if key doesn't exist anywhere
     */
    public KeyValue readFromAllNodes(String key) {
        List<KeyValue> results = new ArrayList<>();

        System.out.println("R=5 Read starting for key: " + key);

        // Step 1: Read from Leader (local - fast, no network)
        KeyValue leaderValue = leaderService.getLocal(key);
        if (leaderValue != null) {
            results.add(leaderValue);
            System.out.println("Leader returned: version=" + leaderValue.getVersion());
        } else {
            System.out.println("Leader: key not found");
        }

        // Step 2: Read from all 4 Followers in parallel
        List<Future<KeyValue>> futures = new ArrayList<>();

        for (String followerUrl : nodeConfig.getFollowerUrls()) {
            // Submit a task to read from this Follower
            Future<KeyValue> future = executor.submit(() ->
                readFromFollower(followerUrl, key)
            );
            futures.add(future);
        }

        // Step 3: Collect results from all Followers
        for (int i = 0; i < futures.size(); i++) {
            try {
                // Wait up to 2 seconds for this Follower to respond
                KeyValue result = futures.get(i).get(2, TimeUnit.SECONDS);

                if (result != null) {
                    results.add(result);
                    System.out.println("Follower " + (i+1) + " returned: version=" + result.getVersion());
                } else {
                    System.out.println("Follower " + (i+1) + ": key not found");
                }

            } catch (TimeoutException e) {
                System.err.println("⏱Follower " + (i+1) + " timeout (took > 2 seconds)");
            } catch (Exception e) {
                System.err.println("Follower " + (i+1) + " error: " + e.getMessage());
            }
        }

        // Step 4: Find the KeyValue with highest version
        if (results.isEmpty()) {
            System.out.println("Key not found on any node");
            return null;  // Key doesn't exist anywhere
        }

        // Find newest version
        KeyValue newest = results.stream()
            .max(Comparator.comparing(KeyValue::getVersion))
            .orElse(null);

        System.out.println("R=5 Read complete. Returning version=" + newest.getVersion());

        return newest;
    }

    /**
     * Read from a single Follower via HTTP.
     * This method makes the network call to the Follower's /local_read endpoint.
     *
     * @param followerUrl Base URL of the Follower (e.g., http://localhost:8081)
     * @param key The key to read
     * @return KeyValue from this Follower, or null if not found or error
     */
    private KeyValue readFromFollower(String followerUrl, String key) {
        try {
            // For now, since we're testing with Leader only, this will fail
            // But in real deployment with Followers running, this works
            String url = followerUrl + "/api/follower/local_read?key=" + key;

            KeyValue result = restTemplate.getForObject(url, KeyValue.class);
            return result;

        } catch (Exception e) {
            // Follower might not be running (expected in tests)
            // In production, this could be a network error or timeout
            return null;
        }
    }

    /**
     * Shutdown the thread pool when application stops.
     * This is called automatically by Spring.
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}