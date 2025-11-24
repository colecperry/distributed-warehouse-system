package com.cs6650.shoppingcartservice.model;

import lombok.Data;

@Data
public class CartItem {
  private Integer productId;
  private Integer quantity;
}
