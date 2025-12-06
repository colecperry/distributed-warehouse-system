package com.cs6650.shoppingcartservice.config;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

  public static final String QUEUE_NAME = "warehouse-orders";

  @Bean
  public Queue warehouseQueue() {
    // Create durable queue (survives broker restart)
    return new Queue(QUEUE_NAME, true);
  }
}