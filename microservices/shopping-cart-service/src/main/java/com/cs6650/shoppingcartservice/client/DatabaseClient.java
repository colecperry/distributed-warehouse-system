package com.cs6650.shoppingcartservice.client;

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
 * Database Client for Shopping Cart Service
 *
 * This client talks to our distributed Leader-Follower database system.
 * The database uses a W=1, R=5 strategy for Shopping Cart Service:
 * - W=1: Writes go to the Leader only (fast writes, returns immediately)
 * - R=5: Reads from all 5 nodes (Leader + 4 Followers) and return the newest version
 *        - Best for write-heavy workloads where consistency is critical
 *        - Ensures strong consistency for cart operations (~200ms vs ~5ms)
 *        - Prevents showing stale cart data during checkout
 *
 * We just call the database's REST API - the database handles all the replication
 * and consistency logic behind the scenes.
 */
@Component
public class DatabaseClient {

  private static final Logger log = LoggerFactory.getLogger(DatabaseClient.class);

  @Value("${database.url:http://localhost:8080}")
  private String databaseUrl;

  @Value("${database.readStrategy:R5}")
  private String readStrategy;

  private final RestTemplate restTemplate = new RestTemplate();
  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Save a shopping cart to the database (a key-value pair) using a W=1 write strategy
   *
   * The database expects JSON like: {"key": "cart_1", "value": "{...cart json...}"}
   *
   * @param key The key to store (e.g., "cart_123")
   * @param value The value to store (JSON string)
   * @throws RuntimeException if the database write fails
   */
  public void put(String key, String value) {
    try {
      String url = databaseUrl + "/api/set";
      Map<String, String> request = Map.of("key", key, "value", value);

      // Send POST request to database Leader
      restTemplate.postForEntity(url, request, String.class); // serialize request to JSON string
      log.debug("Stored key: {}", key);

    } catch (Exception e) {
      log.error("Failed to store key: {}", key, e);
      throw new RuntimeException("Database write failed", e);
    }
  }

  /**
   * Retrieve a shopping cart from the database by its key using a R=5 read strategy.
   *
   * This uses the R=5 read strategy - reads from all 5 nodes (Leader + 4 Followers)
   * and returns the value with the highest version number. This ensures we always get
   * the most recent cart data, which is critical for checkout operations where consistency
   * matters more than speed. Prevents issues like showing stale cart contents or incorrect totals.
   *
   * The database returns JSON like: {"key": "cart_1", "value": "{...cart json...}", "version": 3}
   * We just extract and return the "value" field.
   *
   * @param key The key to look up (e.g., "cart_123")
   * @return The value as a string, or null if the key doesn't exist
   */
  public String get(String key) {
    try {
      // Append read strategy parameter (R5 for strong consistency)
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
      log.debug("Key not found or error: {}", key); // Database returns null
      return null; // Also return null for network errors
    }
  }

  /**
   * Begin a transaction (prints message in database).
   *
   * Marks the start of an atomic operation in the checkout flow.
   * The database will print "Transaction STARTED" but doesn't implement real 2-phase commit.
   */
  public void beginTransaction() {
    try {
      restTemplate.postForEntity(databaseUrl + "/api/transaction/begin", null, String.class);
      log.info("Transaction STARTED");
    } catch (Exception e) {
      log.error("Failed to begin transaction", e);
    }
  }

  /**
   * End a transaction (prints message in database).
   *
   * Marks successful completion of an atomic operation.
   * Called when checkout completes successfully (payment authorized, order created).
   */
  public void endTransaction() {
    try {
      restTemplate.postForEntity(databaseUrl + "/api/transaction/end", null, String.class);
      log.info("Transaction COMMITTED");
    } catch (Exception e) {
      log.error("Failed to end transaction", e);
    }
  }

  /**
   * Abort a transaction (prints message in database).
   *
   * Marks failed operation that should be rolled back.
   * Called when checkout fails (payment declined, cart not found, errors).
   */
  public void abortTransaction() {
    try {
      restTemplate.postForEntity(databaseUrl + "/api/transaction/abort", null, String.class);
      log.info("Transaction ABORTED");
    } catch (Exception e) {
      log.error("Failed to abort transaction", e);
    }
  }
}