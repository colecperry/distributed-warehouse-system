package com.cs6650.shoppingcartservice.service;

import com.cs6650.shoppingcartservice.model.ShoppingCart;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class RabbitMQService {
    private static final Logger logger = LoggerFactory.getLogger(RabbitMQService.class);
    private static final String QUEUE_NAME = "warehouse-orders";

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void sendOrderToWarehouse(ShoppingCart cart) {
        try {
            // Create message map
            Map<String, Object> message = new HashMap<>();
            message.put("cartId", cart.getShoppingCartId());
            message.put("customerId", cart.getCustomerId());
            message.put("items", cart.getItems());

            // Convert to JSON string manually
            String jsonMessage = objectMapper.writeValueAsString(message);

            // Log what we're sending (for debugging)
            logger.info("Sending to warehouse: {}", jsonMessage);

            // Send as String (not Map object!)
            rabbitTemplate.convertAndSend(QUEUE_NAME, jsonMessage);

            logger.info("Successfully sent order for cart {} to warehouse", cart.getShoppingCartId());

        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize order message for cart {}", cart.getShoppingCartId(), e);
            throw new RuntimeException("Failed to send order to warehouse", e);
        }
    }
}