package com.cs6650.assignment4.w1r5.controller;

import com.cs6650.assignment4.w1r5.config.NodeConfig;
import com.cs6650.assignment4.w1r5.model.KeyValue;
import com.cs6650.assignment4.w1r5.service.FollowerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * FollowerController handles requests to Follower nodes.
 *
 * Endpoints:
 * - POST /api/replicate - Receive replication from Leader
 * - GET /api/local_read?key={key} - Read local value (with 50ms delay for R=5)
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

    // ==========================================
    // REPLICATION ENDPOINT (Leader → Follower)
    // ==========================================

    /**
     * Receive replication update from Leader.
     * Only Followers should accept this.
     *
     * POST /api/replicate
     * Body: KeyValue object (key, value, version)
     * Response: 200 OK
     */
    @PostMapping("/replicate")
    public ResponseEntity<String> replicate(@RequestBody KeyValue keyValue) {
        // Check if this node is a Follower
        if (!nodeConfig.isFollower()) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body("Error: This is the Leader. Cannot receive replications.");
        }

        // Store the replication (with 100ms delay)
        followerService.storeReplication(keyValue);

        return ResponseEntity.ok("Replicated successfully");
    }

    // ==========================================
    // READ ENDPOINT (for R=5 reads)
    // ==========================================

    /**
     * Read LOCAL value with 50ms delay.
     * Used by ReadCoordinator during R=5 reads.
     *
     * GET /api/local_read?key=username
     * Response: 200 OK with KeyValue, or 404 Not Found
     */
    @GetMapping("/follower/local_read")
    public ResponseEntity<?> localRead(@RequestParam String key) {
        // Read with 50ms delay (assignment requirement for R=5)
        KeyValue result = followerService.getWithDelay(key);

        if (result == null) {
            return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body("Key not found: " + key);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Health check endpoint.
     *
     * GET /api/health
     * Response: 200 OK with status message
     */
    @GetMapping("/follower/health")
    public ResponseEntity<String> health() {
        String status = String.format(
            "Follower W1R5 - Node %d - Status: OK",
            nodeConfig.getId()
        );
        return ResponseEntity.ok(status);
    }
}