package com.warehouse.database.controller;

import com.warehouse.database.config.NodeConfig;
import com.warehouse.database.model.KeyValue;
import com.warehouse.database.service.FollowerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * FollowerController handles requests to Follower nodes.
 *
 * Endpoints:
 * - POST /api/replicate              Receive replication from Leader
 * - GET  /api/follower/local_read    Return local value (used by ReadCoordinator for R=5)
 * - GET  /api/follower/health        Health check
 */
@RestController
@RequestMapping("/api")
public class FollowerController {

    private final FollowerService followerService;
    private final NodeConfig nodeConfig;

    @Autowired
    public FollowerController(FollowerService followerService, NodeConfig nodeConfig) {
        this.followerService = followerService;
        this.nodeConfig = nodeConfig;
    }

    /**
     * Receive replication update from Leader.
     * POST /api/replicate — Body: KeyValue (key, value, version)
     */
    @PostMapping("/replicate")
    public ResponseEntity<String> replicate(@RequestBody KeyValue keyValue) {
        if (!nodeConfig.isFollower()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body("Error: This is the Leader. Cannot receive replications.");
        }
        followerService.storeReplication(keyValue);
        return ResponseEntity.ok("Replicated successfully");
    }

    /**
     * Return local value with 50ms delay (used by ReadCoordinator during R=5 reads).
     * GET /api/follower/local_read?key=product_1
     */
    @GetMapping("/follower/local_read")
    public ResponseEntity<?> localRead(@RequestParam String key) {
        KeyValue result = followerService.getWithDelay(key);
        if (result == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Key not found: " + key);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/follower/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Follower Database - Node " + nodeConfig.getId() + " - Status: OK");
    }
}
