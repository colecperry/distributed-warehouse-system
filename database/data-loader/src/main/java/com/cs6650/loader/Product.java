package com.cs6650.loader;

/**
 * Simple Product class for data loading
 */
public class Product {
    private int productId;
    private String name;
    private String category;
    private double price;
    private int stock;

    public Product(int productId, String name, String category, double price, int stock) {
        this.productId = productId;
        this.name = name;
        this.category = category;
        this.price = price;
        this.stock = stock;
    }

    // Getters
    public int getProductId() { return productId; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public double getPrice() { return price; }
    public int getStock() { return stock; }

    @Override
    public String toString() {
        return "Product{id=" + productId + ", name='" + name + "', price=$" + price + "}";
    }
}