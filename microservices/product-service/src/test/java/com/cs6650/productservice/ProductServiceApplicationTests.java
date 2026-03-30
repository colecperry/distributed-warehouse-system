package com.cs6650.productservice;

import com.cs6650.productservice.client.DatabaseClient;
import com.cs6650.productservice.controller.ProductController;
import com.cs6650.productservice.model.Product;
import com.cs6650.productservice.service.ProductCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceApplicationTests {

    @Mock DatabaseClient database;
    @Mock ProductCacheService cacheService;
    @InjectMocks ProductController controller;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(controller, "objectMapper", new ObjectMapper());
    }

    @Test
    void createProduct_withNullId_returns400() {
        var response = controller.createProduct(new Product());
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void createProduct_withValidProduct_returns201() {
        Product product = new Product(1, "SKU-001", "TestCorp", 1, 100, 1);
        var response = controller.createProduct(product);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(1, response.getBody().get("product_id"));
    }

    @Test
    void getProduct_whenNotFound_returns404() {
        when(cacheService.getProduct(999)).thenReturn(null);
        var response = controller.getProduct(999);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void getProduct_whenFound_returns200() {
        Product product = new Product(1, "SKU-001", "TestCorp", 1, 100, 1);
        when(cacheService.getProduct(1)).thenReturn(product);
        var response = controller.getProduct(1);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("SKU-001", response.getBody().getSku());
    }

    @Test
    void healthEndpoint_returnsUp() {
        var response = controller.health();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("UP", response.getBody().get("status"));
    }
}
