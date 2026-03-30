package com.example.creditcardauthorizer;

import com.cs6650.creditcardauthorizer.controller.CreditCardAuthorizerController;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CreditCardAuthorizerApplicationTests {

    private final CreditCardAuthorizerController controller = new CreditCardAuthorizerController();

    @Test
    void invalidCardFormat_returns400() {
        var response = controller.authorizePayment(Map.of("credit_card_number", "not-a-card"));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void missingCreditCard_returns400() {
        var response = controller.authorizePayment(Map.of());
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void validCardFormat_returnsAuthorizationDecision() {
        // 90% approved, 10% declined — both are valid outcomes
        var response = controller.authorizePayment(Map.of("credit_card_number", "1234-5678-9012-3456"));
        assertTrue(
            response.getStatusCode() == HttpStatus.OK ||
            response.getStatusCode() == HttpStatus.PAYMENT_REQUIRED
        );
    }

    @Test
    void healthEndpoint_returnsUp() {
        var response = controller.health();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("UP", response.getBody().get("status"));
    }
}
