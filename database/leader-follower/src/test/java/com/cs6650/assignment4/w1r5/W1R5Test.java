package com.cs6650.assignment4.w1r5;

import com.cs6650.assignment4.w1r5.config.NodeConfig;
import com.cs6650.assignment4.w1r5.model.KeyValue;
import com.cs6650.assignment4.w1r5.service.FollowerService;
import com.cs6650.assignment4.w1r5.service.LeaderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

public class W1R5Test {

    private LeaderService leaderService;
    private NodeConfig nodeConfig;

    @BeforeEach
    public void setup() {
        // Create a test NodeConfig
        nodeConfig = new NodeConfig();
        nodeConfig.setId(1);
        nodeConfig.setType("leader");
        // Mock follower URLs (won't actually connect in tests)
        nodeConfig.setNodes(java.util.List.of(
            "http://localhost:8080",
            "http://localhost:8081",
            "http://localhost:8082",
            "http://localhost:8083",
            "http://localhost:8084"
        ));

        leaderService = new LeaderService(nodeConfig);
    }

    @Test
    public void testKeyValueCreation() {
        KeyValue kv = new KeyValue("username", "Ryan", 5);

        assertEquals("username", kv.getKey());
        assertEquals("Ryan", kv.getValue());
        assertEquals(5, kv.getVersion());
    }

    @Test
    public void testIsNewerThan() {
        KeyValue old = new KeyValue("score", "80", 3);
        KeyValue newer = new KeyValue("score", "100", 5);

        assertTrue(newer.isNewerThan(old));
        assertFalse(old.isNewerThan(newer));
    }

    @Test
    public void testEquality() {
        KeyValue kv1 = new KeyValue("key", "value", 1);
        KeyValue kv2 = new KeyValue("key", "value", 1);
        KeyValue kv3 = new KeyValue("key", "value", 2);

        assertEquals(kv1, kv2);
        assertNotEquals(kv1, kv3);
    }

    @Test
    public void testToString() {
        KeyValue kv = new KeyValue("username", "Ryan", 5);
        String str = kv.toString();

        assertTrue(str.contains("username"));
        assertTrue(str.contains("Ryan"));
        assertTrue(str.contains("5"));
    }

    @Test
    public void testFollowerStorage() {
        FollowerService follower = new FollowerService();

        // Store a value
        KeyValue kv = new KeyValue("username", "Ryan", 5);
        follower.storeReplication(kv);

        // Retrieve it
        KeyValue retrieved = follower.getLocal("username");

        assertNotNull(retrieved);
        assertEquals("Ryan", retrieved.getValue());
        assertEquals(5, retrieved.getVersion());
    }

    @Test
    public void testFollowerReplicationDelay() {
        FollowerService follower = new FollowerService();
        KeyValue kv = new KeyValue("test", "value", 1);

        long start = System.currentTimeMillis();
        follower.storeReplication(kv);
        long end = System.currentTimeMillis();

        // Should take at least 100ms
        long duration = end - start;
        assertTrue(duration >= 100, "Replication should take at least 100ms, took: " + duration);
    }

    @Test
    public void testFollowerReadDelay() {
        FollowerService follower = new FollowerService();

        // Store first (so we have data to read)
        KeyValue kv = new KeyValue("test", "value", 1);
        follower.storeReplication(kv);

        // Now test read with delay
        long start = System.currentTimeMillis();
        follower.getWithDelay("test");
        long end = System.currentTimeMillis();

        // Should take at least 50ms
        long duration = end - start;
        assertTrue(duration >= 50, "Read should take at least 50ms, took: " + duration);
    }

    @Test
    public void testFollowerNonExistentKey() {
        FollowerService follower = new FollowerService();

        KeyValue result = follower.getLocal("doesnotexist");

        assertNull(result);
    }

    @Test
    public void testLeaderWriteIncrementsVersion() {
        KeyValue kv1 = leaderService.handleWrite("key1", "value1");
        KeyValue kv2 = leaderService.handleWrite("key2", "value2");

        assertEquals(1, kv1.getVersion());
        assertEquals(2, kv2.getVersion());
        assertTrue(kv2.getVersion() > kv1.getVersion());
    }

    @Test
    public void testLeaderWriteIsFast() {
        // Test that write completes quickly (W=1 - doesn't wait for followers)
        long start = System.currentTimeMillis();

        leaderService.handleWrite("test", "value");

        long end = System.currentTimeMillis();
        long duration = end - start;

        // Should complete in under 100ms (not waiting for replication)
        assertTrue(duration < 100, "Write should be fast (W=1), took: " + duration + "ms");
    }

    @Test
    public void testLeaderLocalRead() {
        // Write
        KeyValue written = leaderService.handleWrite("username", "Ryan");

        // Read locally
        KeyValue read = leaderService.getLocal("username");

        assertNotNull(read);
        assertEquals("Ryan", read.getValue());
        assertEquals(written.getVersion(), read.getVersion());
    }

    @Test
    public void testLeaderStoresLocally() {
        leaderService.handleWrite("key1", "value1");
        leaderService.handleWrite("key2", "value2");
        leaderService.handleWrite("key3", "value3");
        leaderService.handleWrite("key4", "value5");
        leaderService.handleWrite("key5", "value6");
        assertEquals(3, leaderService.size());
    }

    @Test
    public void testVersionUpdatesOnOverwrite() {
        KeyValue kv1 = leaderService.handleWrite("username", "Alice");
        KeyValue kv2 = leaderService.handleWrite("username", "Bob");
        KeyValue kv3 = leaderService.handleWrite("username", "Ryan");
        KeyValue kv4 = leaderService.handleWrite("username", "test");
        // Versions should increase
        assertTrue(kv2.getVersion() > kv1.getVersion());
        assertTrue(kv3.getVersion() > kv2.getVersion());

        // Latest value should be stored
        KeyValue current = leaderService.getLocal("username");
        assertEquals("Ryan", current.getValue());
        assertEquals(kv3.getVersion(), current.getVersion());
    }
}

