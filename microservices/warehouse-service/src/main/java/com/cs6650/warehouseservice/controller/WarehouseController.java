package com.cs6650.warehouseservice.controller;

import java.util.Map;
import java.util.Random;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("")
public class WarehouseController {

  private final Random random = new Random();

  // Utility method to add delay
  private void simulateDelay() {
    int delay = 100 + random.nextInt(902);  // 0–901 → total 100–1001 ms
    try {
      Thread.sleep(delay);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @PostMapping("/reserve")
  public ResponseEntity<Map<String, String>> reserveItem(@RequestBody Map<String, Integer> request) {
    simulateDelay();

    Integer productId = request.get("product_id");
    Integer quantity = request.get("quantity");

    if (productId == null || quantity == null || productId <= 0 || quantity <= 0) {
      return new ResponseEntity<>(Map.of("error", "INVALID_INPUT"), HttpStatus.BAD_REQUEST);
    }

    boolean available = random.nextInt(10) < 9; // 90% chance yes

    return ResponseEntity.ok(Map.of(
        "product_id", productId.toString(),
        "quantity", quantity.toString(),
        "available", available ? "yes" : "no"
    ));
  }

  @PostMapping("/ship")
  public ResponseEntity<Map<String, String>> shipItem(@RequestBody Map<String, Integer> request) {
    simulateDelay();

    Integer productId = request.get("product_id");
    Integer quantity = request.get("quantity");

    if (productId == null || quantity == null || productId <= 0 || quantity <= 0) {
      return new ResponseEntity<>(Map.of("error", "INVALID_INPUT"), HttpStatus.BAD_REQUEST);
    }

    return ResponseEntity.ok(Map.of(
        "product_id", productId.toString(),
        "quantity", quantity.toString(),
        "status", "shipped"
    ));
  }
  @PostMapping("/check-inventory")
  public ResponseEntity<Map<String, Object>> checkInventory(@RequestBody Map<String, Integer> request) {
    simulateDelay();

    Integer productId = request.get("product_id");
    Integer quantity = request.get("quantity");

    if (productId == null || quantity == null || productId <= 0 || quantity <= 0) {
      return new ResponseEntity<>(Map.of("error", "INVALID_INPUT"), HttpStatus.BAD_REQUEST);
    }

    // Simulate inventory check - always return available for now
    // In a real system, this would check actual inventory levels
    boolean available = true;

    if (available) {
      return ResponseEntity.ok(Map.of(
          "product_id", productId,
          "quantity", quantity,
          "available", true
      ));
    } else {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(Map.of(
              "product_id", productId,
              "quantity", quantity,
              "available", false,
              "error", "INSUFFICIENT_INVENTORY"
          ));
    }
  }

  @GetMapping("/hello")
  public String hello() {
    return "Hello from Warehouse Service!";
  }

  /**
   * Health check endpoint for AWS ALB
   */
  @GetMapping("/warehouse/health")
  public ResponseEntity<Map<String, String>> health() {
    return ResponseEntity.ok(Map.of(
        "status", "UP",
        "service", "warehouse-service"
    ));
  }
}