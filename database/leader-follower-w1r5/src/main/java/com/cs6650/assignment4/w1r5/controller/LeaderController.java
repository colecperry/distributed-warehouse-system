package com.cs6650.assignment4.w1r5.controller;

import com.cs6650.assignment4.w1r5.config.NodeConfig;
import com.cs6650.assignment4.w1r5.model.KeyValue;
import com.cs6650.assignment4.w1r5.service.LeaderService;
import com.cs6650.assignment4.w1r5.service.ReadCoordinator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * LeaderController handles client requests to the Leader node.
 *
 * Endpoints:
 * - POST /api/set - Write key-value (W=1 strategy)
 * - GET /api/get?key={key} - Read key-value (will use ReadCoordinator later for R=5)
 * - GET /api/local_read?key={key} - Read local value only (for testing)
 */
@RestController
@RequestMapping("/api")
public class LeaderController {

    private final LeaderService leaderService;
    private final NodeConfig nodeConfig;
    private final ReadCoordinator readCoordinator;

    @Autowired
    public LeaderController(LeaderService leaderService, NodeConfig nodeConfig,
        ReadCoordinator readCoordinator) {
        this.leaderService = leaderService;
        this.nodeConfig = nodeConfig;
        this.readCoordinator = readCoordinator;
    }

    // ==========================================
    // CLIENT ENDPOINTS
    // ==========================================

    /**
     * Handle client WRITE request (W=1).
     * Only the Leader can accept writes.
     *
     * POST /api/set
     * Body: {"key": "username", "value": "Ryan"}
     * Response: 201 Created with KeyValue
     */
    @PostMapping("/set")
    public ResponseEntity<?> set(@RequestBody Map<String, String> body) {
        // Check if this node is the Leader
        if (!nodeConfig.isLeader()) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body("Error: This is a Follower. Send writes to the Leader.");
        }

        // Get key and value from request body
        String key = body.get("key");
        String value = body.get("value");

        // Validate input
        if (key == null || key.isEmpty()) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body("Error: Key cannot be empty.");
        }

        // Value can be empty string (assignment says this is valid)
        if (value == null) {
            value = "";
        }

        // Handle write (W=1)
        KeyValue result = leaderService.handleWrite(key, value);

        // Return 201 Created
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(result);
    }

    /**
     * Handle client READ request.
     * For now, just read from Leader locally.
     * Later, we'll implement R=5 with ReadCoordinator.
     *
     * GET /api/get?key=username
     * Response: 200 OK with KeyValue, or 404 Not Found
     */
    @GetMapping("/get")
    public ResponseEntity<?> get(@RequestParam String key) {
        // Use ReadCoordinator for R=5 reads
        KeyValue result = readCoordinator.readFromAllNodes(key);

        if (result == null) {
            return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body("Key not found: " + key);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Read LOCAL value only (for testing inconsistency window).
     * This endpoint reads ONLY from this node's local storage.
     * Does NOT query other nodes.
     *
     * GET /api/local_read?key=username
     * Response: 200 OK with KeyValue, or 404 Not Found
     */
    @GetMapping("/leader/local_read")
    public ResponseEntity<?> localRead(@RequestParam String key) {
        KeyValue result = leaderService.getLocal(key);

        if (result == null) {
            return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body("Key not found: " + key);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Health check endpoint (useful for testing).
     *
     * GET /api/health
     * Response: 200 OK with status message
     */
    @GetMapping("/leader/health")
    public ResponseEntity<String> health() {
        String status = String.format(
            "Leader W1R5 - Node %d - Status: OK",
            nodeConfig.getId()
        );
        return ResponseEntity.ok(status);
    }
}