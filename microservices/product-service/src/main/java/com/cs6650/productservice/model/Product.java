package com.cs6650.productservice.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Product {
    @JsonProperty("product_id")
    private Integer productId;

    private String sku;

    private String manufacturer;

    @JsonProperty("category_id")
    private Integer categoryId;

    private Integer weight;

    @JsonProperty("some_other_id")
    private Integer someOtherId;
}