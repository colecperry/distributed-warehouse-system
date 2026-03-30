package com.cs6650.shoppingcartservice;

import com.cs6650.shoppingcartservice.controller.ShoppingCartController;
import com.cs6650.shoppingcartservice.model.CartItem;
import com.cs6650.shoppingcartservice.model.ShoppingCart;
import com.cs6650.shoppingcartservice.service.CartService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShoppingCartServiceApplicationTests {

    @Mock CartService cartService;
    @InjectMocks ShoppingCartController controller;

    @Test
    void createCart_withInvalidCustomerId_returns400() {
        var response = controller.createCart(Map.of("customer_id", 0));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verifyNoInteractions(cartService);
    }

    @Test
    void createCart_withValidCustomerId_returns201() throws Exception {
        when(cartService.createCart(42)).thenReturn(1);
        var response = controller.createCart(Map.of("customer_id", 42));
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(1, response.getBody().get("shopping_cart_id"));
    }

    @Test
    void checkout_withoutCreditCard_returns400() {
        var response = controller.checkout(1, Map.of());
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verifyNoInteractions(cartService);
    }

    @Test
    void checkout_withDeclinedPayment_returns402() throws Exception {
        when(cartService.checkout(1, "1234-5678-9012-3456"))
            .thenThrow(new CartService.PaymentDeclinedException());
        var response = controller.checkout(1, Map.of("credit_card_number", "1234-5678-9012-3456"));
        assertEquals(HttpStatus.PAYMENT_REQUIRED, response.getStatusCode());
    }

    @Test
    void getCart_whenNotFound_returns404() throws Exception {
        when(cartService.getCart(999)).thenReturn(null);
        var response = controller.getCart(999);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void getCart_whenFound_returns200() throws Exception {
        ShoppingCart cart = new ShoppingCart();
        cart.setShoppingCartId(1);
        when(cartService.getCart(1)).thenReturn(cart);
        var response = controller.getCart(1);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void healthEndpoint_returnsUp() {
        var response = controller.health();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("UP", response.getBody().get("status"));
    }
}
