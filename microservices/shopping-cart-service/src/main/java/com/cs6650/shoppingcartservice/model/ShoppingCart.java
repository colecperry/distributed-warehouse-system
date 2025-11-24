package com.cs6650.shoppingcartservice.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class ShoppingCart {
  private Integer shoppingCartId;
  private Integer customerId;
  private List<CartItem> items = new ArrayList<>();
}

