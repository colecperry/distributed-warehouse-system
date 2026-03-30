package com.cs6650.creditcardauthorizer.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/credit-card-authorizer")
public class CreditCardAuthorizerController {

  private static final Pattern CARD_PATTERN =
      Pattern.compile("^[0-9]{4}-[0-9]{4}-[0-9]{4}-[0-9]{4}$");

  private final Random random = new Random();

  @PostMapping("/authorize")
  public ResponseEntity<Map<String, String>> authorizePayment(@RequestBody Map<String, String> body) {
    String cardNumber = body.get("credit_card_number");

    // Validate input
    if (cardNumber == null || !CARD_PATTERN.matcher(cardNumber).matches()) {
      return ResponseEntity
          .status(HttpStatus.BAD_REQUEST)
          .body(Map.of(
              "error", "INVALID_FORMAT",
              "message", "Credit card number must be in the form ****-****-****-**** where * is a digit."
          ));
    }

    // Random 90% authorized, 10% declined
    if (random.nextInt(10) == 0) { // ~10% chance
      return ResponseEntity
          .status(HttpStatus.PAYMENT_REQUIRED)
          .body(Map.of("status", "Declined"));
    }

    return ResponseEntity
        .ok(Map.of("status", "Authorized"));
  }

  @GetMapping("/hello")
  public ResponseEntity<String> hello() {
    return ResponseEntity.ok("Hello from Credit Card Authorizer!");
  }

  /**
   * Health check endpoint for AWS ALB
   */
  @GetMapping("/health")
  public ResponseEntity<Map<String, String>> health() {
    return ResponseEntity.ok(Map.of(
        "status", "UP",
        "service", "credit-card-authorizer"
    ));
  }

}