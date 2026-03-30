package com.warehouse.database.controller;

import com.warehouse.database.config.NodeConfig;
import com.warehouse.database.model.KeyValue;
import com.warehouse.database.model.ReadStrategy;
import com.warehouse.database.service.LeaderService;
import com.warehouse.database.service.ReadCoordinator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * LeaderController receives client requests and delegates to LeaderService / ReadCoordinator.
 *
 * Endpoints:
 * - POST /api/set                  Write key-value (W=1 strategy)
 * - GET  /api/get?key=&readStrategy=  Read with configurable R=1 or R=5 strategy
 * - GET  /api/leader/local_read    Read from local store only (testing)
 * - GET  /api/leader/health        Health check
 * - POST /api/transaction/*        Transaction boundary markers (logged only, no real 2PC)
 * - POST /api/leader/register      Follower self-registration on startup
 * - GET  /api/leader/followers     List registered followers
 */
@RestController
@RequestMapping("/api")
public class LeaderController {

    private final LeaderService leaderService;
    private final NodeConfig nodeConfig;
    private final ReadCoordinator readCoordinator;
    private static final int EXPECTED_FOLLOWERS = 4;

    @Autowired
    public LeaderController(LeaderService leaderService, NodeConfig nodeConfig,
        ReadCoordinator readCoordinator) {
      this.leaderService = leaderService;
      this.nodeConfig = nodeConfig;
      this.readCoordinator = readCoordinator;
    }

    private ResponseEntity<String> badRequest(String msg) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(msg);
    }

    private ResponseEntity<?> notFound(String msg) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(msg);
    }

    // ==========================================
    // CLIENT ENDPOINTS
    // ==========================================

    /**
     * Handle client WRITE request (W=1).
     * POST /api/set — Body: {"key": "product_1", "value": "{...json...}"}
     */
    @PostMapping("/set")
    public ResponseEntity<?> set(@RequestBody Map<String, String> body) {
        if (!nodeConfig.isLeader()) {
            return badRequest("Error: This is a Follower. Send writes to the Leader.");
        }
        String key = body.get("key");
        String value = body.get("value");
        if (key == null || key.isEmpty()) {
            return badRequest("Error: Key cannot be empty.");
        }
        if (value == null) value = "";
        KeyValue result = leaderService.handleWrite(key, value);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /**
     * Handle client READ with configurable strategy.
     * GET /api/get?key=product_1&readStrategy=R1  (defaults to R5)
     */
    @GetMapping("/get")
    public ResponseEntity<?> get(
            @RequestParam String key,
            @RequestParam(required = false, defaultValue = "R5") String readStrategy) {
        ReadStrategy strategy;
        try {
            strategy = ReadStrategy.valueOf(readStrategy.toUpperCase());
        } catch (IllegalArgumentException e) {
            return badRequest("Error: Invalid readStrategy. Must be 'R1' or 'R5', got: " + readStrategy);
        }
        KeyValue result = readCoordinator.read(key, strategy);
        return result != null ? ResponseEntity.ok(result) : notFound("Key not found: " + key);
    }

    /** Read from this node's local store only (for testing the replication window). */
    @GetMapping("/leader/local_read")
    public ResponseEntity<?> localRead(@RequestParam String key) {
        KeyValue result = leaderService.getLocal(key);
        return result != null ? ResponseEntity.ok(result) : notFound("Key not found: " + key);
    }

    @GetMapping("/leader/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Leader Database - Node " + nodeConfig.getId() + " - Status: OK");
    }

    // ==========================================
    // TRANSACTION ENDPOINTS (logged only — no real 2PC implementation)
    // ==========================================

    @PostMapping("/transaction/begin")
    public ResponseEntity<String> beginTransaction() {
        System.out.println("Transaction STARTED");
        return ResponseEntity.ok("Transaction started");
    }

    @PostMapping("/transaction/end")
    public ResponseEntity<String> endTransaction() {
        System.out.println("Transaction COMMITTED");
        return ResponseEntity.ok("Transaction committed");
    }

    @PostMapping("/transaction/abort")
    public ResponseEntity<String> abortTransaction() {
        System.out.println("Transaction ABORTED");
        return ResponseEntity.ok("Transaction aborted");
    }

    // ==========================================
    // FOLLOWER REGISTRATION
    // ==========================================

    /**
     * Followers call this at startup to register their IP/port.
     * POST /api/leader/register — Body: {"nodeId": 2, "ipAddress": "10.0.x.x", "port": 9081}
     */
    @PostMapping("/leader/register")
    public ResponseEntity<String> registerFollower(@RequestBody Map<String, Object> body) {
        if (!nodeConfig.isLeader()) {
            return badRequest("Error: Only the Leader can accept registrations.");
        }
        try {
            Integer nodeId = (Integer) body.get("nodeId");
            String ipAddress = (String) body.get("ipAddress");
            Integer port = (Integer) body.get("port");
            if (nodeId == null || ipAddress == null || port == null) {
                return badRequest("Error: Missing nodeId, ipAddress, or port");
            }
            String followerUrl = "http://" + ipAddress + ":" + port;
            leaderService.registerFollower(nodeId, followerUrl);
            System.out.printf("Registered Follower %d at %s (%d/%d followers)%n",
                nodeId, followerUrl, leaderService.getRegisteredFollowerCount(), EXPECTED_FOLLOWERS);
            return ResponseEntity.ok("Follower " + nodeId + " registered successfully");
        } catch (Exception e) {
            return badRequest("Error registering follower: " + e.getMessage());
        }
    }

    @GetMapping("/leader/followers")
    public ResponseEntity<?> getRegisteredFollowers() {
        Map<String, Object> response = new HashMap<>();
        response.put("expectedFollowers", EXPECTED_FOLLOWERS);
        response.put("registeredFollowers", leaderService.getRegisteredFollowerCount());
        response.put("followers", leaderService.getRegisteredFollowersMap());
        return ResponseEntity.ok(response);
    }
}
