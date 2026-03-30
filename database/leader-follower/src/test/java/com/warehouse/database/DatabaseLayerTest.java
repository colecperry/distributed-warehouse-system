package com.warehouse.database;

import com.warehouse.database.model.KeyValue;
import com.warehouse.database.service.FollowerService;
import com.warehouse.database.service.LeaderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DatabaseLayerTest {

    private LeaderService leaderService;

    @BeforeEach
    public void setup() {
        leaderService = new LeaderService();
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
    public void testKeyValueEquality() {
        KeyValue kv1 = new KeyValue("key", "value", 1);
        KeyValue kv2 = new KeyValue("key", "value", 1);
        KeyValue kv3 = new KeyValue("key", "value", 2);
        assertEquals(kv1, kv2);
        assertNotEquals(kv1, kv3);
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
        // W=1 means writes return immediately without waiting for replication
        long start = System.currentTimeMillis();
        leaderService.handleWrite("test", "value");
        long duration = System.currentTimeMillis() - start;
        assertTrue(duration < 100, "Write should be fast (W=1), took: " + duration + "ms");
    }

    @Test
    public void testLeaderLocalRead() {
        KeyValue written = leaderService.handleWrite("username", "Ryan");
        KeyValue read = leaderService.getLocal("username");
        assertNotNull(read);
        assertEquals("Ryan", read.getValue());
        assertEquals(written.getVersion(), read.getVersion());
    }

    @Test
    public void testFollowerReplicationDelay() {
        FollowerService follower = new FollowerService();
        KeyValue kv = new KeyValue("test", "value", 1);
        long start = System.currentTimeMillis();
        follower.storeReplication(kv);
        long duration = System.currentTimeMillis() - start;
        assertTrue(duration >= 100, "Replication delay should be >=100ms, took: " + duration);
    }

    @Test
    public void testFollowerReadDelay() {
        FollowerService follower = new FollowerService();
        follower.storeReplication(new KeyValue("test", "value", 1));
        long start = System.currentTimeMillis();
        follower.getWithDelay("test");
        long duration = System.currentTimeMillis() - start;
        assertTrue(duration >= 50, "Read delay should be >=50ms, took: " + duration);
    }

    @Test
    public void testFollowerNonExistentKey() {
        FollowerService follower = new FollowerService();
        assertNull(follower.getLocal("doesnotexist"));
    }
}
