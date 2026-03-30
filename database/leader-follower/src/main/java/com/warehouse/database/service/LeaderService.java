package com.warehouse.database.service;

import com.warehouse.database.model.KeyValue;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * LeaderService handles writes for the Leader node (W=1 strategy).
 *
 * Flow:
 * 1. Followers register at startup via POST /api/leader/register
 * 2. Client writes via POST /api/set → handleWrite() stores locally, returns immediately
 * 3. Thread pool replicates to all registered Followers in the background
 * 4. Reads go through getLocal() (used by ReadCoordinator)
 */
@Service
public class LeaderService {

  private final ConcurrentHashMap<String, KeyValue> store = new ConcurrentHashMap<>();
  private final AtomicLong versionCounter = new AtomicLong(0);
  private final ConcurrentHashMap<Integer, String> registeredFollowers = new ConcurrentHashMap<>();
  private final RestTemplate restTemplate = new RestTemplate();
  // Fixed pool of 4 threads — one per follower, prevents unbounded thread creation under load
  private final ExecutorService replicationExecutor = Executors.newFixedThreadPool(4);

  // ==========================================
  // 1. WRITE FLOW (Client → Leader → Background Replication)
  // ==========================================

  /**
   * Handle write from client (W=1).
   * Stores locally, returns immediately, replicates in background.
   */
  public KeyValue handleWrite(String key, String value) {
    long newVersion = versionCounter.incrementAndGet();
    KeyValue kv = new KeyValue(key, value, newVersion);
    store.put(key, kv);
    replicateToFollowersAsync(kv);
    return kv;
  }

  /**
   * Replicate to all Followers using a bounded thread pool (does not block client).
   */
  private void replicateToFollowersAsync(KeyValue kv) {
    replicationExecutor.submit(() -> {
      for (String followerUrl : getRegisteredFollowerUrls()) {
        try {
          restTemplate.postForObject(followerUrl + "/api/replicate", kv, String.class);
          Thread.sleep(200);
        } catch (Exception e) {
          System.err.println("Failed to replicate to " + followerUrl + ": " + e.getMessage());
        }
      }
    });
  }

  // ==========================================
  // 2. READ (used by ReadCoordinator for R=1 and R=5)
  // ==========================================

  public KeyValue getLocal(String key) {
    return store.get(key);
  }

  // ==========================================
  // 3. FOLLOWER REGISTRATION
  // ==========================================

  public void registerFollower(Integer nodeId, String followerUrl) {
    registeredFollowers.put(nodeId, followerUrl);
  }

  public List<String> getRegisteredFollowerUrls() {
    return new ArrayList<>(registeredFollowers.values());
  }

  public int getRegisteredFollowerCount() {
    return registeredFollowers.size();
  }

  public Map<Integer, String> getRegisteredFollowersMap() {
    return new HashMap<>(registeredFollowers);
  }

  // ==========================================
  // 4. UTILITY (testing/debugging)
  // ==========================================

  public long getCurrentVersion() { return versionCounter.get(); }
  public int size() { return store.size(); }
  public void clear() { store.clear(); versionCounter.set(0); }
}
