package com.cs6650.productservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis Configuration
 *
 * Configures RedisTemplate for caching Product data.
 * Uses JSON serialization to store Product objects as JSON strings in Redis.
 */
@Configuration
public class RedisConfig {

  /**
   * Configure RedisTemplate with JSON serialization
   *
   * This template is used by ProductCacheService to store and retrieve
   * Product objects as JSON strings in Redis.
   *
   * @param connectionFactory Redis connection factory (auto-configured by Spring Boot)
   * @return Configured RedisTemplate
   */
  @Bean
  public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);

    // Use String serializer for keys (e.g., "product_123")
    template.setKeySerializer(new StringRedisSerializer());
    template.setHashKeySerializer(new StringRedisSerializer());

    // Use JSON serializer for values (Product objects stored as JSON)
    template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
    template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

    template.afterPropertiesSet();
    return template;
  }
}
