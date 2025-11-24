package com.cs6650.loader;

import com.google.gson.Gson;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Loads 1000 random products into the database
 */
public class ProductLoader {

    private static final String DATABASE_URL = "http://localhost:8080/api/set";
    private static final int NUM_PRODUCTS = 1000;

    // Product categories
    private static final String[] CATEGORIES = {
        "Electronics", "Clothing", "Books", "Home & Garden",
        "Sports", "Toys", "Food", "Beauty", "Automotive", "Music"
    };

    // Sample product names
    private static final String[] PRODUCT_NAMES = {
        "Wireless Mouse", "Laptop Stand", "Coffee Maker", "Running Shoes",
        "Desk Lamp", "Bluetooth Speaker", "Water Bottle", "Notebook",
        "Phone Case", "Headphones", "Keyboard", "Monitor", "Backpack",
        "T-Shirt", "Jeans", "Novel", "Guitar", "Camera", "Watch", "Sunglasses"
    };

    private static final Gson gson = new Gson();
    private static final Random random = new Random();

    public static void main(String[] args) {
        System.out.println("Starting to load " + NUM_PRODUCTS + " products...");
        System.out.println("Target database: " + DATABASE_URL);
        System.out.println();

        int successCount = 0;
        int failCount = 0;

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {

            for (int i = 1; i <= NUM_PRODUCTS; i++) {
                Product product = generateRandomProduct(i);

                if (storeProduct(httpClient, product)) {
                    successCount++;
                    if (i % 100 == 0) {
                        System.out.println("Loaded " + i + " products...");
                    }
                } else {
                    failCount++;
                    System.err.println("Failed to load product " + i);
                }
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println();
        System.out.println("========================================");
        System.out.println("Successfully loaded: " + successCount + " products");
        System.out.println("Failed: " + failCount + " products");
        System.out.println("========================================");
    }

    /**
     * Generate a random product
     */
    private static Product generateRandomProduct(int id) {
        String name = PRODUCT_NAMES[random.nextInt(PRODUCT_NAMES.length)] + " #" + id;
        String category = CATEGORIES[random.nextInt(CATEGORIES.length)];
        double price = 10 + (random.nextDouble() * 990); // $10 to $1000
        int stock = random.nextInt(500); // 0 to 500 items

        return new Product(id, name, category, price, stock);
    }

    /**
     * Store a product in the database
     */
    private static boolean storeProduct(CloseableHttpClient httpClient, Product product) {
        try {
            // Create the key-value pair
            // Key: "product:{id}"
            // Value: JSON string of the product
            String key = "product:" + product.getProductId();
            String value = gson.toJson(product);

            // Create request body
            Map<String, String> body = new HashMap<>();
            body.put("key", key);
            body.put("value", value);
            String jsonBody = gson.toJson(body);

            // Create HTTP POST request
            HttpPost request = new HttpPost(DATABASE_URL);
            request.setHeader("Content-Type", "application/json");
            request.setEntity(new StringEntity(jsonBody));

            // Execute request
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                return response.getCode() == 201;
            }

        } catch (Exception e) {
            System.err.println("Error storing product " + product.getProductId() + ": " + e.getMessage());
            return false;
        }
    }
}