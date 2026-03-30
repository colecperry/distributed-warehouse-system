package com.cs6650.shoppingcartservice.service;

import com.cs6650.shoppingcartservice.client.DatabaseClient;
import com.cs6650.shoppingcartservice.model.CartItem;
import com.cs6650.shoppingcartservice.model.ShoppingCart;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Business logic for shopping cart operations.
 * Coordinates between the database, product service, warehouse service,
 * credit card service, and RabbitMQ.
 */
@Service
public class CartService {

  private static final Logger logger = LoggerFactory.getLogger(CartService.class);

  private final AtomicInteger idCounter = new AtomicInteger(1);
  private final AtomicInteger orderIdCounter = new AtomicInteger(1);

  @Value("${credit.card.authorizer.url}")
  private String CCA_URL;

  @Value("${product.service.url}")
  private String PRODUCT_SERVICE_URL;

  @Value("${warehouse.service.url}")
  private String WAREHOUSE_SERVICE_URL;

  @Autowired
  private DatabaseClient database;

  @Autowired
  private RabbitMQService rabbitMQService;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private RestTemplate restTemplate;

  public int createCart(int customerId) throws Exception {
    int cartId = idCounter.getAndIncrement();
    ShoppingCart cart = new ShoppingCart();
    cart.setShoppingCartId(cartId);
    cart.setCustomerId(customerId);
    database.put("cart_" + cartId, objectMapper.writeValueAsString(cart));
    logger.info("Created cart {} for customer {}", cartId, customerId);
    return cartId;
  }

  public void addItem(int shoppingCartId, CartItem item) throws Exception {
    if (!isProductValid(item.getProductId())) {
      throw new ProductNotFoundException();
    }
    if (!isInventoryAvailable(item.getProductId(), item.getQuantity())) {
      throw new InsufficientInventoryException();
    }
    ShoppingCart cart = loadOrCreateCart(shoppingCartId);
    cart.getItems().add(item);
    saveCart(shoppingCartId, cart);
    logger.info("Added {} x product {} to cart {}", item.getQuantity(), item.getProductId(), shoppingCartId);
  }

  public int checkout(int shoppingCartId, String creditCardNumber) throws Exception {
    database.beginTransaction();
    try {
      String cartJson = database.get("cart_" + shoppingCartId);
      if (cartJson == null) {
        database.abortTransaction();
        throw new CartNotFoundException();
      }
      ShoppingCart cart = objectMapper.readValue(cartJson, ShoppingCart.class);

      if (!authorizeCreditCard(creditCardNumber)) {
        database.abortTransaction();
        throw new PaymentDeclinedException();
      }

      int orderId = orderIdCounter.getAndIncrement();
      sendToWarehouse(cart, orderId);
      database.endTransaction();
      logger.info("Checkout complete for cart {}, order {}", shoppingCartId, orderId);
      return orderId;

    } catch (CartNotFoundException | PaymentDeclinedException e) {
      throw e;
    } catch (Exception e) {
      database.abortTransaction();
      throw e;
    }
  }

  public ShoppingCart getCart(int shoppingCartId) throws Exception {
    String cartJson = database.get("cart_" + shoppingCartId);
    if (cartJson == null) return null;
    return objectMapper.readValue(cartJson, ShoppingCart.class);
  }

  private boolean isProductValid(Integer productId) {
    try {
      ResponseEntity<Map> response = restTemplate.getForEntity(
          PRODUCT_SERVICE_URL + "/products/" + productId, Map.class);
      return response.getStatusCode() == HttpStatus.OK;
    } catch (HttpClientErrorException.NotFound e) {
      return false;
    } catch (Exception e) {
      logger.error("Failed to check product service: {}", e.getMessage());
      return false;
    }
  }

  private boolean isInventoryAvailable(Integer productId, Integer quantity) {
    try {
      ResponseEntity<Map> response = restTemplate.postForEntity(
          WAREHOUSE_SERVICE_URL + "/check-inventory",
          Map.of("product_id", productId, "quantity", quantity),
          Map.class);
      if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) return false;
      Boolean available = (Boolean) response.getBody().get("available");
      return available != null && available;
    } catch (Exception e) {
      logger.error("Failed to check warehouse service: {}", e.getMessage());
      return false;
    }
  }

  private ShoppingCart loadOrCreateCart(Integer shoppingCartId) throws Exception {
    String cartJson = database.get("cart_" + shoppingCartId);
    if (cartJson == null) {
      ShoppingCart cart = new ShoppingCart();
      cart.setShoppingCartId(shoppingCartId);
      cart.setCustomerId(0);
      return cart;
    }
    return objectMapper.readValue(cartJson, ShoppingCart.class);
  }

  private void saveCart(Integer shoppingCartId, ShoppingCart cart) throws Exception {
    database.put("cart_" + shoppingCartId, objectMapper.writeValueAsString(cart));
  }

  private boolean authorizeCreditCard(String creditCardNumber) {
    try {
      ResponseEntity<Map> response = restTemplate.postForEntity(
          CCA_URL, Map.of("credit_card_number", creditCardNumber), Map.class);
      return response.getStatusCode() == HttpStatus.OK;
    } catch (Exception e) {
      logger.error("Credit card authorization failed: {}", e.getMessage());
      return false;
    }
  }

  private void sendToWarehouse(ShoppingCart cart, int orderId) {
    try {
      rabbitMQService.sendOrderToWarehouse(cart);
      logger.info("Sent order {} to warehouse queue", orderId);
    } catch (Exception e) {
      logger.error("Failed to send order {} to warehouse: {}", orderId, e.getMessage());
      // Don't fail checkout — warehouse can process the message later
    }
  }

  // Custom exceptions for clean error signaling to the controller
  public static class ProductNotFoundException extends RuntimeException {}
  public static class InsufficientInventoryException extends RuntimeException {}
  public static class CartNotFoundException extends RuntimeException {}
  public static class PaymentDeclinedException extends RuntimeException {}
}
