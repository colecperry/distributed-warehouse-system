package com.cs6650.productservice.controller;

import com.cs6650.productservice.client.DatabaseClient;
import com.cs6650.productservice.model.Product;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Random;

/**
 * Product Service Controller
 *
 * Handles creating and retrieving products from our distributed database.
 * Products are stored as JSON strings in the database with keys like "product_1", "product_2", etc.
 *
 * Each endpoint includes a random delay (100-1000ms) to simulate real business logic processing.
 */
@Slf4j
@RestController
@RequestMapping("/products")
public class ProductController {

  private final Random random = new Random();

  @Autowired
  private DatabaseClient database;

  @Autowired
  private ObjectMapper objectMapper;

  /**
   * Adds a random delay between 100-1000ms to simulate business logic.
   * This helps trigger autoscaling during load testing.
   */
  private void simulateDelay() {
    int delay = 100 + random.nextInt(901);
    try {
      Thread.sleep(delay);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Health check endpoint for AWS ALB
   * GET /products/health
   */
  @GetMapping("/health")
  public ResponseEntity<Map<String, String>> health() {
    return ResponseEntity.ok(Map.of(
        "status", "UP",
        "service", "product-service"
    ));
  }

  /**
   * Create a new product and store it in the database.
   *
   * FIXED: Now uses the product_id from the request body instead of generating one.
   * This prevents ID collisions when the service restarts.
   *
   * @param product The product details from the request body (must include product_id)
   * @return 201 Created with the product_id, or 400 if product_id is missing, or 500 if database fails
   */
  @PostMapping("")
  public ResponseEntity<Map<String, Integer>> createProduct(@RequestBody Product product) {
    simulateDelay();

    // FIXED: Use the product_id from the request body
    Integer productId = product.getProductId();

    // Validate that product_id was provided
    if (productId == null) {
      log.error("Product ID is required but was not provided");
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(Map.of("error", -1)); // Indicate error
    }

    try {
      // Convert product object to JSON string for storage
      String productJson = objectMapper.writeValueAsString(product);

      // Store in database with key format: "product_123"
      // Using the product_id from the request, not a generated one
      database.put("product_" + productId, productJson);

      log.info("Successfully created product with ID: {}", productId);

      return ResponseEntity.status(HttpStatus.CREATED)
          .body(Map.of("product_id", productId));
    } catch (Exception e) {
      log.error("Failed to store product with ID: {}", productId, e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * Retrieve a product by its ID from the database.
   *
   * We look up the product in the database, deserialize the JSON back into
   * a Product object, and return it to the client.
   *
   * @param productId The ID of the product to retrieve
   * @return 200 OK with product data, 404 if not found, or 500 if database fails
   */
  @GetMapping("/{productId}")
  public ResponseEntity<Product> getProduct(@PathVariable Integer productId) {
    simulateDelay();

    try {
      // Look up product in database by key "product_123"
      String productJson = database.get("product_" + productId);

      // If key doesn't exist, product not found
      if (productJson == null) {
        log.debug("Product not found: {}", productId);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
      }

      // Convert JSON string back to Product object
      Product product = objectMapper.readValue(productJson, Product.class);
      return ResponseEntity.ok(product);
    } catch (Exception e) {
      log.error("Failed to read product: {}", productId, e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }
}