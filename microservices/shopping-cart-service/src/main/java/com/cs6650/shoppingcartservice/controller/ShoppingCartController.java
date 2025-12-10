package com.cs6650.shoppingcartservice.controller;

import com.cs6650.shoppingcartservice.client.DatabaseClient;
import com.cs6650.shoppingcartservice.model.CartItem;
import com.cs6650.shoppingcartservice.model.ShoppingCart;
import com.cs6650.shoppingcartservice.service.RabbitMQService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shopping Cart Service Controller
 *
 * Handles creating carts, adding items, and checkout flow with our distributed database.
 * Carts are stored as JSON strings in the database with keys like "cart_1", "cart_2", etc.
 *
 * Each endpoint includes a random delay (100-1000ms) to simulate real business logic processing.
 */
@RestController
@RequestMapping("")
public class ShoppingCartController {
  private static final Logger logger = LoggerFactory.getLogger(ShoppingCartController.class);
  private final AtomicInteger idCounter = new AtomicInteger(1);
  private final AtomicInteger orderIdCounter = new AtomicInteger(1);
  private final Random random = new Random();

  @Value("${credit.card.authorizer.url}")
  private String CCA_URL;

  @Value("${product.service.url}")
  private String PRODUCT_SERVICE_URL;

  @Value("${warehouse.service.url}")
  private String WAREHOUSE_SERVICE_URL;

  @Autowired
  private RabbitMQService rabbitMQService;

  @Autowired
  private DatabaseClient database;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private RestTemplate restTemplate;

  /**
   * Adds a random delay between 100-1000ms to simulate business logic.
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
   * Create a new shopping cart for a customer.
   * Stores cart in database
   */
  @PostMapping("/shopping-cart")
  public ResponseEntity<Map<String, Object>> createCart(@RequestBody Map<String, Integer> request) {
    simulateDelay(); // Simulate a random delay to help trigger autoscaling during load testing

    Integer customerId = request.get("customer_id");
    if (customerId == null || customerId <= 0) { // Check if customer ID is valid
      return errorResponse(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Invalid customer ID");
    }

    int cartId = idCounter.getAndIncrement();
    ShoppingCart cart = new ShoppingCart(); // Create a new shopping cart for the customer
    cart.setShoppingCartId(cartId);
    cart.setCustomerId(customerId);

    try {
      // Store cart in database as JSON
      String cartJson = objectMapper.writeValueAsString(cart);
      database.put("cart_" + cartId, cartJson);

      logger.info("Created cart {} for customer {}", cartId, customerId);
      return ResponseEntity.status(HttpStatus.CREATED)
          .body(Map.of("shopping_cart_id", cartId));
    } catch (Exception e) {
      logger.error("Failed to create cart in database", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * Use Case 1 - Add an item to a shopping cart.
   *
   * Flow:
   * 1. Validate input
   * 2. Call Product Service to get product details
   * 3. Call Warehouse Service to check inventory
   * 4. Add item to cart in database
   *
   * If the cart doesn't exist, we automatically create it (per assignment requirements).
   */
  @PostMapping("/shopping-carts/{shoppingCartId}/addItem")
  public ResponseEntity<Map<String, Object>> addItem(
      @PathVariable Integer shoppingCartId,
      @RequestBody CartItem item) {

    simulateDelay();

    // Validate input
    ResponseEntity<Map<String, Object>> validationError = validateCartItem(item);
    if (validationError != null) {
      return validationError;
    }

    // Check if product exists in Product Service - 1 READ operation
    if (!isProductValid(item.getProductId())) {
      return errorResponse(HttpStatus.NOT_FOUND, "NOT_FOUND", "Product not found");
    }

    // Check if warehouse has inventory -> warehouse has no database (always returns True)
    if (!isInventoryAvailable(item.getProductId(), item.getQuantity())) {
      return errorResponse(HttpStatus.CONFLICT, "INSUFFICIENT_INVENTORY", "Not enough inventory available");
    }

    try {
      // Load cart from database (or create new one if doesn't exist)
      ShoppingCart cart = loadOrCreateCart(shoppingCartId); // Make a GET request to the database to load the cart - 1 READ

      // Add the new item to the cart
      cart.getItems().add(item);

      // Save updated cart back to database
      saveCart(shoppingCartId, cart); // Make a PUT request to the database to save the cart - 1 WRITE

      logger.info("Successfully added {} x product {} to cart {}",
          item.getQuantity(), item.getProductId(), shoppingCartId);

      return ResponseEntity.status(HttpStatus.NO_CONTENT).build();

    } catch (Exception e) {
      logger.error("Failed to add item to cart", e);
      return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Could not add item to cart");
    }
  }

  /**
   * Use Case 2 -Checkout a shopping cart with ACID transaction semantics.
   * Reads cart from database and processes payment with transaction boundaries.
   */
  @PostMapping("/shopping-carts/{shoppingCartId}/checkout")
  public ResponseEntity<Map<String, Object>> checkout(
      @PathVariable Integer shoppingCartId,
      @RequestBody Map<String, String> request) {

    simulateDelay();

    // Validate credit card provided
    String creditCardNumber = request.get("credit_card_number");
    if (creditCardNumber == null || creditCardNumber.isEmpty()) {
      logger.warn("Checkout attempted without credit card");
      return errorResponse(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Credit card number is required");
    }

    // BEGIN TRANSACTION - Start of atomic checkout operation
    database.beginTransaction();
    logger.info("Transaction started for cart {}", shoppingCartId);

    try {
      // Read cart from database - 1 READ operation
      String cartJson = database.get("cart_" + shoppingCartId);

      if (cartJson == null) {
        logger.warn("Checkout attempted on non-existent cart {}", shoppingCartId);
        database.abortTransaction(); // Transaction Boundary: Abort
        return errorResponse(HttpStatus.NOT_FOUND, "NOT_FOUND", "Cart not found");
      }

      ShoppingCart cart = objectMapper.readValue(cartJson, ShoppingCart.class); // Deserialize (JSON to Java)

      // Authorize credit card payment
      boolean paymentAuthorized = authorizeCreditCard(creditCardNumber);

      if (paymentAuthorized) {
        // Payment successful - create order and ship
        int orderId = orderIdCounter.getAndIncrement();
        logger.info("Payment authorized for cart {}, created order {}", shoppingCartId, orderId);

        // Send order to warehouse via RabbitMQ (fire-and-forget)
        sendToWarehouse(cart, orderId);

        // COMMIT TRANSACTION - Successful checkout
        database.endTransaction();
        logger.info("Transaction committed for cart {}", shoppingCartId);

        return ResponseEntity.ok(Map.of("order_id", orderId));

      } else {
        // Payment declined
        logger.warn("Payment declined for cart {}", shoppingCartId);
        database.abortTransaction();
        return errorResponse(HttpStatus.PAYMENT_REQUIRED, "PAYMENT_DECLINED", "Credit card declined");
      }

    } catch (Exception e) {
      logger.error("Error during checkout: {}", e.getMessage());
      database.abortTransaction();
      return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "SERVICE_ERROR", "Could not complete checkout");
    }
  }

  /**
   * Retrieve a shopping cart by ID.
   * This is a read-only operation that helps achieve the 2:1 read/write ratio.
   * Customers frequently view their cart before adding more items or checking out.
   */
  @GetMapping("/shopping-carts/{shoppingCartId}")
  public ResponseEntity<ShoppingCart> getCart(@PathVariable Integer shoppingCartId) {
    simulateDelay();

    try {
      // Read cart from database
      String cartJson = database.get("cart_" + shoppingCartId);

      if (cartJson == null) {
        logger.warn("Cart {} not found", shoppingCartId);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
      }

      // Deserialize and return cart
      ShoppingCart cart = objectMapper.readValue(cartJson, ShoppingCart.class);
      logger.info("Retrieved cart {} for customer {}", shoppingCartId, cart.getCustomerId());
      return ResponseEntity.ok(cart);

    } catch (Exception e) {
      logger.error("Failed to retrieve cart {}", shoppingCartId, e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }


  @GetMapping("/hello")
  public ResponseEntity<String> hello() {
    return ResponseEntity.ok("Hello from Shopping Cart Service!");
  }

  /**
   * Health check endpoint for AWS ALB
   */
  @GetMapping("/cart/health")
  public ResponseEntity<Map<String, String>> health() {
    return ResponseEntity.ok(Map.of(
        "status", "UP",
        "service", "shopping-cart-service"
    ));
  }


  // ==========================================
  // HELPER METHODS - Keep code clean and readable
  // ==========================================

  /**
   * Validate cart item input (product ID and quantity).
   * Returns error response if invalid, or null if valid.
   */
  private ResponseEntity<Map<String, Object>> validateCartItem(CartItem item) {
    if (item.getProductId() == null || item.getProductId() <= 0) {
      logger.warn("Invalid product ID: {}", item.getProductId());
      return errorResponse(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Invalid product ID");
    }

    if (item.getQuantity() == null || item.getQuantity() < 1 || item.getQuantity() > 10000) {
      logger.warn("Invalid quantity: {}", item.getQuantity());
      return errorResponse(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Quantity must be between 1 and 10000");
    }

    return null; // Valid
  }

  /**
   * Check if a product exists by calling the Product Service.
   * Returns true if product exists, false otherwise.
   */
  private boolean isProductValid(Integer productId) {
    try {
      logger.info("Checking if product {} exists", productId);
      String productUrl = PRODUCT_SERVICE_URL + "/products/" + productId;

      // Make a GET request to the Product Service to check if the product exists
      ResponseEntity<Map> response = restTemplate.getForEntity(productUrl, Map.class);
      boolean exists = response.getStatusCode() == HttpStatus.OK;

      if (exists) {
        logger.info("Product {} validated", productId);
      } else {
        logger.warn("Product {} not found", productId);
      }

      return exists;

    } catch (HttpClientErrorException.NotFound e) {
      logger.warn("Product {} does not exist", productId);
      return false;
    } catch (Exception e) {
      logger.error("Failed to check product service: {}", e.getMessage());
      return false;
    }
  }

  /**
   * Check if warehouse has sufficient inventory by calling the Warehouse Service.
   * Returns true if inventory available, false otherwise.
   */
  private boolean isInventoryAvailable(Integer productId, Integer quantity) {
    try {
      logger.info("Checking inventory for product {} quantity {}", productId, quantity);
      String warehouseUrl = WAREHOUSE_SERVICE_URL + "/check-inventory";

      Map<String, Object> request = Map.of("product_id", productId, "quantity", quantity);
      ResponseEntity<Map> response = restTemplate.postForEntity(warehouseUrl, request, Map.class);

      if (response.getStatusCode() != HttpStatus.OK) {
        logger.warn("Warehouse service returned error for product {}", productId);
        return false;
      }

      Map<String, Object> body = response.getBody();
      if (body == null) {
        logger.error("Warehouse service returned null response");
        return false;
      }

      Boolean available = (Boolean) body.get("available");
      boolean hasInventory = available != null && available;

      if (hasInventory) {
        logger.info("Inventory check passed for product {}", productId);
      } else {
        logger.warn("Insufficient inventory for product {}", productId);
      }

      return hasInventory;

    } catch (Exception e) {
      logger.error("Failed to check warehouse service: {}", e.getMessage());
      return false;
    }
  }

  /**
   * Load cart from database, or create a new one if it doesn't exist.
   * This implements the auto-create cart feature required by the assignment.
   */
  private ShoppingCart loadOrCreateCart(Integer shoppingCartId) throws Exception {
    String cartJson = database.get("cart_" + shoppingCartId); // Make a GET request to the database to load the cart - 1 READ

    if (cartJson == null) {
      // Cart doesn't exist - create it automatically
      logger.info("Cart {} doesn't exist, auto-creating cart", shoppingCartId);
      ShoppingCart cart = new ShoppingCart();
      cart.setShoppingCartId(shoppingCartId);
      cart.setCustomerId(0);  // Unknown customer
      return cart;
    } else {
      // Cart exists - deserialize it
      return objectMapper.readValue(cartJson, ShoppingCart.class);
    }
  }

  /**
   * Save cart to database as JSON string.
   */
  private void saveCart(Integer shoppingCartId, ShoppingCart cart) throws Exception {
    String cartJson = objectMapper.writeValueAsString(cart);
    database.put("cart_" + shoppingCartId, cartJson);
  }

  /**
   * Authorize credit card payment by calling the Credit Card Service.
   * Returns true if authorized, false if declined.
   */
  private boolean authorizeCreditCard(String creditCardNumber) {
    try {
      logger.info("Authorizing payment");
      ResponseEntity<Map> response = restTemplate.postForEntity(
          CCA_URL,
          Map.of("credit_card_number", creditCardNumber),
          Map.class
      );

      if (response.getStatusCode() == HttpStatus.OK) {
        return true; // Authorized
      } else if (response.getStatusCode() == HttpStatus.PAYMENT_REQUIRED) {
        return false; // Declined
      } else {
        logger.error("Unexpected response from Credit Card Service: {}", response.getStatusCode());
        return false;
      }

    } catch (Exception e) {
      logger.error("Credit card authorization failed: {}", e.getMessage());
      return false;
    }
  }

  /**
   * Send order to warehouse via RabbitMQ (fire-and-forget pattern).
   * Errors are logged but don't fail the checkout.
   */
  private void sendToWarehouse(ShoppingCart cart, int orderId) {
    try {
      rabbitMQService.sendOrderToWarehouse(cart);
      logger.info("Sent order {} to warehouse queue", orderId);
    } catch (Exception e) {
      logger.error("Failed to send order {} to warehouse: {}", orderId, e.getMessage());
      // Don't fail checkout - warehouse issue can be handled separately
    }
  }

  /**
   * Create a standardized error response.
   * Makes error handling consistent across all endpoints.
   */
  private ResponseEntity<Map<String, Object>> errorResponse(HttpStatus status, String error, String message) {
    return ResponseEntity.status(status).body(Map.of("error", error, "message", message));
  }
}