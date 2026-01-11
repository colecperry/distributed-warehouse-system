package com.cs6650.productservice.service;

import com.cs6650.productservice.client.DatabaseClient;
import com.cs6650.productservice.model.Product;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Product Cache Service
 *
 * Implements the cache-aside pattern for Product caching:
 * 1. Check Redis first
 * 2. If miss, query database
 * 3. Store in Redis with TTL
 * 4. Return value
 *
 * This improves performance for read-heavy Product Service workloads
 * by reducing database load and response time.
 */
@Slf4j
@Service
public class ProductCacheService {

  private static final String CACHE_KEY_PREFIX = "product_";
  
  // Cache metrics
  private long cacheHits = 0;
  private long cacheMisses = 0;

  @Autowired
  private RedisTemplate<String, Object> redisTemplate;

  @Autowired
  private DatabaseClient databaseClient;

  @Autowired
  private ObjectMapper objectMapper;

  @Value("${cache.product.ttl:3600}")
  private long cacheTtlSeconds;

  /**
   * Get a product by ID using cache-aside pattern
   *
   * @param productId The product ID
   * @return Product if found, null otherwise
   */
  public Product getProduct(Integer productId) {
    String cacheKey = CACHE_KEY_PREFIX + productId;
    
    try {
      // Step 1: Check Redis first
      Product cachedProduct = (Product) redisTemplate.opsForValue().get(cacheKey);
      
      if (cachedProduct != null) {
        // Cache hit
        cacheHits++;
        log.info("Cache HIT for product: {}", productId);
        return cachedProduct;
      }
      
      // Step 2: Cache miss - query database
      cacheMisses++;
      log.info("Cache MISS for product: {}, querying database", productId);
      
      String productJson = databaseClient.get(cacheKey);
      
      if (productJson == null) {
        log.info("Product not found in database: {}", productId);
        return null;
      }
      
      // Deserialize from database JSON
      Product product = objectMapper.readValue(productJson, Product.class);
      
      // Step 3: Store Product object in Redis with TTL (Redis will serialize as JSON)
      redisTemplate.opsForValue().set(cacheKey, product, cacheTtlSeconds, TimeUnit.SECONDS);
      log.info("Cached product: {} with TTL: {} seconds", productId, cacheTtlSeconds);
      
      return product;
      
    } catch (Exception e) {
      log.error("Error getting product from cache/database: {}", productId, e);
      // Fallback to database only on cache error
      try {
        String productJson = databaseClient.get(cacheKey);
        if (productJson != null) {
          return objectMapper.readValue(productJson, Product.class);
        }
      } catch (Exception dbException) {
        log.error("Database fallback also failed for product: {}", productId, dbException);
      }
      return null;
    }
  }

  /**
   * Invalidate cache entry for a product
   *
   * This should be called when a product is updated or deleted
   * to ensure cache consistency.
   *
   * @param productId The product ID to invalidate
   */
  public void invalidateProduct(Integer productId) {
    String cacheKey = CACHE_KEY_PREFIX + productId;
    try {
      Boolean deleted = redisTemplate.delete(cacheKey);
      if (Boolean.TRUE.equals(deleted)) {
        log.info("Invalidated cache for product: {}", productId);
      } else {
        log.debug("Cache entry not found for product: {} (may have already expired)", productId);
      }
    } catch (Exception e) {
      log.error("Error invalidating cache for product: {}", productId, e);
      // Don't throw - cache invalidation failure shouldn't break the request
    }
  }

  /**
   * Get cache hit rate statistics
   *
   * @return Cache hit rate as a percentage (0-100)
   */
  public double getCacheHitRate() {
    long total = cacheHits + cacheMisses;
    if (total == 0) {
      return 0.0;
    }
    return (double) cacheHits / total * 100.0;
  }

  /**
   * Get cache statistics
   *
   * @return Map containing cache metrics
   */
  public CacheStats getCacheStats() {
    return new CacheStats(cacheHits, cacheMisses, getCacheHitRate());
  }

  /**
   * Cache statistics data class
   */
  public static class CacheStats {
    public final long hits;
    public final long misses;
    public final double hitRate;

    public CacheStats(long hits, long misses, double hitRate) {
      this.hits = hits;
      this.misses = misses;
      this.hitRate = hitRate;
    }
  }
}
