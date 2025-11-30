package com.cs6650.productservice.controller;

import com.cs6650.productservice.model.Product;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@RestController
public class ProductController {

    private final Map<Integer, Product> productStore = new ConcurrentHashMap<>();
    private final AtomicInteger productIdCounter = new AtomicInteger(1);
    private final Random random = new Random();

    // Utility method for adding delay (100–1000 ms)
    private void simulateDelay() {
        int delay = 100 + random.nextInt(901);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @PostMapping("/product")
    public ResponseEntity<Map<String, Integer>> createProduct(@RequestBody Product product) {
        
        simulateDelay();
        
        log.info("POST request to create product");

        // Generate a new unique product ID
        Integer newProductId = productIdCounter.getAndIncrement();
        product.setProductId(newProductId);

        // Store the product
        productStore.put(newProductId, product);
        log.info("Product created with ID: {}", newProductId);

        // Return 201 with the generated product_id
        Map<String, Integer> response = Map.of("product_id", newProductId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/products/{productId}")
    public ResponseEntity<Product> getProduct(@PathVariable Integer productId) {
        
        simulateDelay();
        
        log.info("GET request for product ID: {}", productId);
        Product product = productStore.get(productId);

        if (product == null) {
            log.warn("Product not found: {}", productId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        return ResponseEntity.ok(product);
    }
}
