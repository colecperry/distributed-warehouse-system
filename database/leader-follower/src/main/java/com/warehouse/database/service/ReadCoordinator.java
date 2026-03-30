package com.warehouse.database.service;

import com.warehouse.database.config.NodeConfig;
import com.warehouse.database.model.KeyValue;
import com.warehouse.database.model.ReadStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;

/**
 * ReadCoordinator implements configurable read strategies (R=1 and R=5).
 *
 * R=1: Read from a single node (fast, eventual consistency)
 *      - Best for read-heavy workloads (e.g., Product Service)
 *      - Reads from Leader first (local, no network), falls back to one Follower
 *
 * R=5: Read from ALL 5 nodes and return the value with highest version (strong consistency)
 *      - Best for write-heavy workloads where consistency is critical (e.g., Shopping Cart)
 *      - Ensures you always get the most recent data even during the replication window
 */
@Service
public class ReadCoordinator {

    private final NodeConfig nodeConfig;
    private final LeaderService leaderService;
    private final RestTemplate restTemplate;
    private final ExecutorService executor;

    @Autowired
    public ReadCoordinator(NodeConfig nodeConfig, LeaderService leaderService) {
        this.nodeConfig = nodeConfig;
        this.leaderService = leaderService;
        this.restTemplate = new RestTemplate();
        this.executor = Executors.newFixedThreadPool(4); // one thread per follower
    }

    // ==========================================
    // R=1 READ LOGIC (Fast, Single Node)
    // ==========================================

    /**
     * Read from a single node (R=1 strategy).
     *
     * 1. Try Leader first (local, no network — fastest)
     * 2. If Leader doesn't have it, fall back to first Follower
     *
     * Trade-off: Fast but may return slightly stale data during the replication window.
     */
    public KeyValue readFromSingleNode(String key) {
        System.out.println("R=1 Read starting for key: " + key);

        KeyValue leaderValue = leaderService.getLocal(key);
        if (leaderValue != null) {
            System.out.println("R=1 Read complete from Leader: version=" + leaderValue.getVersion());
            return leaderValue;
        }

        List<String> followerUrls = nodeConfig.getFollowerUrls();
        if (!followerUrls.isEmpty()) {
            KeyValue followerValue = readFromFollower(followerUrls.get(0), key);
            if (followerValue != null) {
                System.out.println("R=1 Read complete from Follower: version=" + followerValue.getVersion());
                return followerValue;
            }
        }

        System.out.println("R=1 Read: key not found on any node");
        return null;
    }

    // ==========================================
    // R=5 READ LOGIC (Strong Consistency)
    // ==========================================

    /**
     * Read from ALL 5 nodes and return the most recent version (R=5).
     *
     * 1. Read from Leader (local, no network)
     * 2. Read from all 4 Followers in parallel
     * 3. Return the KeyValue with the highest version number
     *
     * Guarantees zero stale reads — even if some Followers haven't been updated yet,
     * the Leader always has the latest version.
     */
    public KeyValue readFromAllNodes(String key) {
        List<KeyValue> results = new ArrayList<>();
        System.out.println("R=5 Read starting for key: " + key);

        KeyValue leaderValue = leaderService.getLocal(key);
        if (leaderValue != null) {
            results.add(leaderValue);
            System.out.println("Leader returned: version=" + leaderValue.getVersion());
        }

        List<Future<KeyValue>> futures = new ArrayList<>();
        for (String followerUrl : nodeConfig.getFollowerUrls()) {
            futures.add(executor.submit(() -> readFromFollower(followerUrl, key)));
        }

        for (int i = 0; i < futures.size(); i++) {
            try {
                KeyValue result = futures.get(i).get(2, TimeUnit.SECONDS);
                if (result != null) {
                    results.add(result);
                    System.out.println("Follower " + (i + 1) + " returned: version=" + result.getVersion());
                }
            } catch (TimeoutException e) {
                System.err.println("Follower " + (i + 1) + " timeout");
            } catch (Exception e) {
                System.err.println("Follower " + (i + 1) + " error: " + e.getMessage());
            }
        }

        if (results.isEmpty()) return null;

        KeyValue newest = results.stream()
            .max(Comparator.comparing(KeyValue::getVersion))
            .orElse(null);

        System.out.println("R=5 Read complete. Returning version=" + newest.getVersion());
        return newest;
    }

    private KeyValue readFromFollower(String followerUrl, String key) {
        try {
            return restTemplate.getForObject(
                followerUrl + "/api/follower/local_read?key=" + key, KeyValue.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** Route to the appropriate read strategy based on the enum parameter. */
    public KeyValue read(String key, ReadStrategy strategy) {
        return strategy == ReadStrategy.R1 ? readFromSingleNode(key) : readFromAllNodes(key);
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}
