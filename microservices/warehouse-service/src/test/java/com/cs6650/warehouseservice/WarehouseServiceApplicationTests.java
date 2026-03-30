package com.cs6650.warehouseservice;

import com.cs6650.warehouseservice.controller.WarehouseController;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WarehouseServiceApplicationTests {

    private final WarehouseController controller = new WarehouseController();

    @Test
    void reserve_withValidInput_returns200() {
        var response = controller.reserveItem(Map.of("product_id", 1, "quantity", 5));
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void reserve_withMissingProductId_returns400() {
        var response = controller.reserveItem(Map.of("quantity", 5));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void checkInventory_withValidInput_returnsAvailable() {
        var response = controller.checkInventory(Map.of("product_id", 1, "quantity", 10));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().get("available"));
    }

    @Test
    void checkInventory_withMissingProductId_returns400() {
        var response = controller.checkInventory(Map.of("quantity", 5));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void healthEndpoint_returnsUp() {
        var response = controller.health();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("UP", response.getBody().get("status"));
    }
}
