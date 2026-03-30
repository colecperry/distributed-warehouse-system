package com.cs6650.productservice.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Database Client for Product Service
 *
 * This client talks to our distributed Leader-Follower database system.
 * The database uses a W=1, R=1 strategy for Product Service:
 *
 * Note: Shopping Cart Service has its own DatabaseClient with the same put/get methods
 * plus transaction support (beginTransaction/endTransaction/abortTransaction). The
 * duplication is intentional — each microservice owns its dependencies independently,
 * which is a standard microservice boundary trade-off.
 * - W=1: Writes go to the Leader only (fast writes, returns immediately)
 * - R=1: Reads from a single node (fast reads, eventual consistency)
 *        - Best for read-heavy workloads like product browsing
 *        - Trades strict consistency for speed (~5ms vs ~200ms)
 *
 * We just call the database's REST API - the database handles all the replication
 * and consistency logic behind the scenes.
 */
@Component
public class DatabaseClient {

  private static final Logger log = LoggerFactory.getLogger(DatabaseClient.class);

  @Value("${database.url:http://localhost:8080}")
  private String databaseUrl;

  @Value("${database.readStrategy:R1}")
  private String readStrategy;

  private final RestTemplate restTemplate = new RestTemplate();
  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Store a key-value pair in the database.
   *
   * This uses the W=1 write strategy - it writes to the Leader node and returns
   * immediately. The Leader handles replication to the 4 Followers in the background.
   *
   * The database expects JSON like: {"key": "product_1", "value": "{...product json...}"}
   *
   * @param key The key to store (e.g., "product_123")
   * @param value The value to store (JSON string)
   * @throws RuntimeException if the database write fails
   */
  public void put(String key, String value) {
    try {
      String url = databaseUrl + "/api/set";
      Map<String, String> request = Map.of("key", key, "value", value);

      restTemplate.postForEntity(url, request, String.class); // Send POST request to database Leader
      log.debug("Stored key: {}", key);

    } catch (Exception e) {
      log.error("Failed to store key: {}", key, e);
      throw new RuntimeException("Database write failed", e);
    }
  }

  /**
   * Retrieve a value from the database by its key.
   *
   * This uses the R=1 read strategy - reads from a single node (Leader or first Follower).
   * This is optimized for read-heavy workloads like product browsing where speed matters
   * more than strict consistency. May return slightly stale data if replication hasn't completed.
   *
   * The database returns JSON like: {"key": "product_1", "value": "{...product json...}", "version": 3}
   * We just extract and return the "value" field.
   *
   * @param key The key to look up (e.g., "product_123")
   * @return The value as a string, or null if the key doesn't exist
   */
  public String get(String key) {
    try {
      // Append read strategy parameter (R1 for fast reads)
      String url = databaseUrl + "/api/get?key=" + key + "&readStrategy=" + readStrategy;
      log.debug("Reading from database with strategy: {}", readStrategy);
      ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

      if (response.getBody() == null) {
        return null;
      }

      // Database returns: {"key": "...", "value": "...", "version": 1}
      // We just need the "value" part
      JsonNode json = objectMapper.readTree(response.getBody());
      JsonNode valueNode = json.get("value");
      return valueNode != null ? valueNode.asText() : null;

    } catch (Exception e) {
      // Key doesn't exist or database is down
      log.debug("Key not found or error: {}", key);
      return null;
    }
  }
}