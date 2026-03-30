package com.cs6650.shoppingcartservice.controller;

import com.cs6650.shoppingcartservice.model.CartItem;
import com.cs6650.shoppingcartservice.model.ShoppingCart;
import com.cs6650.shoppingcartservice.service.CartService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Shopping Cart Service Controller
 *
 * Handles HTTP routing for cart operations. Business logic lives in CartService.
 * Carts are stored as JSON in the distributed database with keys like "cart_1", "cart_2", etc.
 */
@RestController
@RequestMapping("")
public class ShoppingCartController {

  private static final Logger logger = LoggerFactory.getLogger(ShoppingCartController.class);

  @Autowired
  private CartService cartService;

  @PostMapping("/shopping-cart")
  public ResponseEntity<Map<String, Object>> createCart(@RequestBody Map<String, Integer> request) {
    Integer customerId = request.get("customer_id");
    if (customerId == null || customerId <= 0) {
      return errorResponse(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Invalid customer ID");
    }
    try {
      int cartId = cartService.createCart(customerId);
      return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("shopping_cart_id", cartId));
    } catch (Exception e) {
      logger.error("Failed to create cart", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * Use Case 1 - Add an item to a shopping cart.
   * Validates product exists, checks inventory, then persists the updated cart.
   */
  @PostMapping("/shopping-carts/{shoppingCartId}/addItem")
  public ResponseEntity<Map<String, Object>> addItem(
      @PathVariable Integer shoppingCartId,
      @RequestBody CartItem item) {

    ResponseEntity<Map<String, Object>> validationError = validateCartItem(item);
    if (validationError != null) return validationError;

    try {
      cartService.addItem(shoppingCartId, item);
      return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    } catch (CartService.ProductNotFoundException e) {
      return errorResponse(HttpStatus.NOT_FOUND, "NOT_FOUND", "Product not found");
    } catch (CartService.InsufficientInventoryException e) {
      return errorResponse(HttpStatus.CONFLICT, "INSUFFICIENT_INVENTORY", "Not enough inventory available");
    } catch (Exception e) {
      logger.error("Failed to add item to cart", e);
      return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Could not add item to cart");
    }
  }

  /**
   * Use Case 2 - Checkout with simulated ACID transaction boundaries.
   * Reads cart, authorizes payment, publishes order to warehouse via RabbitMQ.
   */
  @PostMapping("/shopping-carts/{shoppingCartId}/checkout")
  public ResponseEntity<Map<String, Object>> checkout(
      @PathVariable Integer shoppingCartId,
      @RequestBody Map<String, String> request) {

    String creditCardNumber = request.get("credit_card_number");
    if (creditCardNumber == null || creditCardNumber.isEmpty()) {
      return errorResponse(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Credit card number is required");
    }

    try {
      int orderId = cartService.checkout(shoppingCartId, creditCardNumber);
      return ResponseEntity.ok(Map.of("order_id", orderId));
    } catch (CartService.CartNotFoundException e) {
      return errorResponse(HttpStatus.NOT_FOUND, "NOT_FOUND", "Cart not found");
    } catch (CartService.PaymentDeclinedException e) {
      return errorResponse(HttpStatus.PAYMENT_REQUIRED, "PAYMENT_DECLINED", "Credit card declined");
    } catch (Exception e) {
      logger.error("Error during checkout: {}", e.getMessage());
      return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "SERVICE_ERROR", "Could not complete checkout");
    }
  }

  @GetMapping("/shopping-carts/{shoppingCartId}")
  public ResponseEntity<ShoppingCart> getCart(@PathVariable Integer shoppingCartId) {
    try {
      ShoppingCart cart = cartService.getCart(shoppingCartId);
      if (cart == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
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

  @GetMapping("/cart/health")
  public ResponseEntity<Map<String, String>> health() {
    return ResponseEntity.ok(Map.of("status", "UP", "service", "shopping-cart-service"));
  }

  private ResponseEntity<Map<String, Object>> validateCartItem(CartItem item) {
    if (item.getProductId() == null || item.getProductId() <= 0) {
      return errorResponse(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Invalid product ID");
    }
    if (item.getQuantity() == null || item.getQuantity() < 1 || item.getQuantity() > 10000) {
      return errorResponse(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Quantity must be between 1 and 10000");
    }
    return null;
  }

  private ResponseEntity<Map<String, Object>> errorResponse(HttpStatus status, String error, String message) {
    return ResponseEntity.status(status).body(Map.of("error", error, "message", message));
  }
}
