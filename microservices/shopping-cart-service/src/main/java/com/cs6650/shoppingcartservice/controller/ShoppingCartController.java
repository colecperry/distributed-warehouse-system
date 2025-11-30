package com.cs6650.shoppingcartservice.controller;

import com.cs6650.shoppingcartservice.model.CartItem;
import com.cs6650.shoppingcartservice.model.ShoppingCart;
import com.cs6650.shoppingcartservice.service.RabbitMQService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ShoppingCartController implements:
 * - POST /shopping-cart
 * - POST /shopping-carts/{id}/addItem
 * - POST /shopping-carts/{id}/checkout (calls CCA)
 *
 * //ADD RABBITMQ HERE → This service will eventually publish checkout or order events.
 */
@RestController
@RequestMapping("")
public class ShoppingCartController {
  private static final Logger logger = LoggerFactory.getLogger(ShoppingCartController.class);

  private final Map<Integer, ShoppingCart> carts = new HashMap<>();
  private final AtomicInteger idCounter = new AtomicInteger(1);
  private final AtomicInteger orderIdCounter = new AtomicInteger(1);

  private final Random random = new Random();

  @Value("${credit.card.authorizer.url}")
  private String CCA_URL;

  @Autowired
  private RabbitMQService rabbitMQService;

  // Utility delay: 100–1000ms
  private void simulateDelay() {
    int delay = 100 + random.nextInt(902);
    try {
      Thread.sleep(delay);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @PostMapping("/shopping-cart")
  public ResponseEntity<Map<String, Object>> createCart(@RequestBody Map<String, Integer> request) {

    simulateDelay();
    
    Integer customerId = request.get("customer_id");
    if (customerId == null || customerId <= 0) {
      return new ResponseEntity<>(Map.of("error", "INVALID_INPUT"), HttpStatus.BAD_REQUEST);
    }

    int cartId = idCounter.getAndIncrement();
    ShoppingCart cart = new ShoppingCart();
    cart.setShoppingCartId(cartId);
    cart.setCustomerId(customerId);
    carts.put(cartId, cart);

    logger.info("Created cart {} for customer {}", cartId, customerId);
    return new ResponseEntity<>(Map.of("shopping_cart_id", cartId), HttpStatus.CREATED);
  }

  @PostMapping("/shopping-carts/{shoppingCartId}/addItem")
  public ResponseEntity<Void> addItem(@PathVariable Integer shoppingCartId, @RequestBody CartItem item) {

    simulateDelay();

    ShoppingCart cart = carts.get(shoppingCartId);
    if (cart == null) {
      logger.warn("Attempted to add item to non-existent cart {}", shoppingCartId);
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    // Validate product ID
    if (item.getProductId() == null || item.getProductId() <= 0) {
      logger.warn("Invalid product ID: {}", item.getProductId());
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

    // Validate quantity (1 to 10,000 per assignment)
    if (item.getQuantity() == null || item.getQuantity() < 1 || item.getQuantity() > 10000) {
      logger.warn("Invalid quantity: {}", item.getQuantity());
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

    cart.getItems().add(item);
    logger.info("Added {} x product {} to cart {}", item.getQuantity(), item.getProductId(), shoppingCartId);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @PostMapping("/shopping-carts/{shoppingCartId}/checkout")
  public ResponseEntity<Map<String, Object>> checkout(
      @PathVariable Integer shoppingCartId,
      @RequestBody Map<String, String> request) {

    simulateDelay();

    // Validate cart exists
    ShoppingCart cart = carts.get(shoppingCartId);
    if (cart == null) {
      logger.warn("Checkout attempted on non-existent cart {}", shoppingCartId);
      return new ResponseEntity<>(
          Map.of("error", "NOT_FOUND", "message", "Cart not found"),
          HttpStatus.NOT_FOUND);
    }

    // Validate credit card provided
    String creditCardNumber = request.get("credit_card_number");
    if (creditCardNumber == null || creditCardNumber.isEmpty()) {
      logger.warn("Checkout attempted without credit card");
      return new ResponseEntity<>(
          Map.of("error", "INVALID_INPUT", "message", "Credit card number is required"),
          HttpStatus.BAD_REQUEST);
    }

    try {
      // Step 1: Authorize credit card
      logger.info("Authorizing payment for cart {}", shoppingCartId);
      RestTemplate restTemplate = new RestTemplate();
      ResponseEntity<Map> response = restTemplate.postForEntity(
          CCA_URL,
          Map.of("credit_card_number", creditCardNumber),
          Map.class
      );

      // Step 2: Check authorization result
      if (response.getStatusCode() == HttpStatus.OK) {
        // Payment authorized - proceed with order
        int orderId = orderIdCounter.getAndIncrement();
        logger.info("Payment authorized for cart {}, created order {}", shoppingCartId, orderId);

        // Step 3: Send order to warehouse (fire-and-forget)
        try {
          rabbitMQService.sendOrderToWarehouse(cart);
          logger.info("Sent order {} to warehouse queue", orderId);
        } catch (Exception e) {
          // Log error but don't fail checkout (fire-and-forget pattern)
          logger.error("Failed to send order {} to warehouse: {}", orderId, e.getMessage());
          // Continue - order still created, will handle warehouse issue separately
        }

        return ResponseEntity.ok(Map.of("order_id", orderId));

      } else if (response.getStatusCode() == HttpStatus.PAYMENT_REQUIRED) {
        // Payment declined
        logger.warn("Payment declined for cart {}", shoppingCartId);
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
            .body(Map.of("error", "PAYMENT_DECLINED", "message", "Credit card declined"));

      } else {
        // Unexpected response
        logger.error("Unexpected response from CCA: {}", response.getStatusCode());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("error", "INTERNAL_ERROR", "message", "Unexpected error during checkout"));
      }

    } catch (Exception e) {
      logger.error("Error calling Credit Card Authorizer: {}", e.getMessage());
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("error", "SERVICE_UNAVAILABLE",
              "message", "Could not reach Credit Card Authorizer"));
    }
  }

  @GetMapping("/hello")
  public ResponseEntity<String> hello() {
    return ResponseEntity.ok("Hello from Shopping Cart Service!");
  }
}
