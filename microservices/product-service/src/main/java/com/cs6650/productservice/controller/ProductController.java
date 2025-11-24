package com.cs6650.productservice.controller;

import com.cs6650.productservice.model.Product;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@RestController
public class ProductController {

    private final Map<Integer, Product> productStore = new ConcurrentHashMap<>();
    private final AtomicInteger productIdCounter = new AtomicInteger(1);

    @PostMapping("/product")
    public ResponseEntity<Map<String, Integer>> createProduct(@RequestBody Product product) {
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
        log.info("GET request for product ID: {}", productId);
        Product product = productStore.get(productId);

        if (product == null) {
            log.warn("Product not found: {}", productId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        return ResponseEntity.ok(product);
    }
}