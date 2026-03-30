package com.cs6650.productservice.controller;

import com.cs6650.productservice.client.DatabaseClient;
import com.cs6650.productservice.model.Product;
import com.cs6650.productservice.service.ProductCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Product Service Controller
 *
 * Handles creating and retrieving products from our distributed database with Redis caching.
 * Products are stored as JSON strings in the database with keys like "product_1", "product_2", etc.
 * Reads use Redis cache for improved performance (cache-aside pattern).
 */
@Slf4j
@RestController
@RequestMapping("/products")
public class ProductController {

  @Autowired
  private DatabaseClient database;

  @Autowired
  private ProductCacheService cacheService;

  @Autowired
  private ObjectMapper objectMapper;

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
   * Get cache statistics
   * GET /products/cache/stats
   *
   * Returns cache hit/miss statistics for monitoring and debugging.
   */
  @GetMapping("/cache/stats")
  public ResponseEntity<Map<String, Object>> getCacheStats() {
    ProductCacheService.CacheStats stats = cacheService.getCacheStats();
    Map<String, Object> response = new HashMap<>();
    response.put("cacheHits", stats.hits);
    response.put("cacheMisses", stats.misses);
    response.put("hitRate", String.format("%.2f%%", stats.hitRate));
    response.put("totalRequests", stats.hits + stats.misses);
    return ResponseEntity.ok(response);
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

      // Invalidate cache for this product (if it exists) to ensure consistency
      // After creation, the next read will populate cache from database
      cacheService.invalidateProduct(productId);

      log.info("Successfully created product with ID: {}", productId);

      return ResponseEntity.status(HttpStatus.CREATED)
          .body(Map.of("product_id", productId));
    } catch (Exception e) {
      log.error("Failed to store product with ID: {}", productId, e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * Retrieve a product by its ID using Redis cache.
   *
   * This endpoint uses the cache-aside pattern:
   * 1. Check Redis cache first (fast, <10ms)
   * 2. If cache miss, query database (slower, ~100-1000ms)
   * 3. Store in Redis for future requests
   * 4. Return product to client
   *
   * @param productId The ID of the product to retrieve
   * @return 200 OK with product data, 404 if not found, or 500 if database fails
   */
  @GetMapping("/{productId}")
  public ResponseEntity<Product> getProduct(@PathVariable Integer productId) {
    try {
      // Use cache service (implements cache-aside pattern)
      Product product = cacheService.getProduct(productId);

      if (product == null) {
        log.debug("Product not found: {}", productId);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
      }

      return ResponseEntity.ok(product);
    } catch (Exception e) {
      log.error("Failed to read product: {}", productId, e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }
}