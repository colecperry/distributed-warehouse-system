package com.cs6650.assignment4.w1r5;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
public class W1R5Application {
    public static void main(String[] args) {
        SpringApplication.run(W1R5Application.class, args);
        System.out.println("W1R5 Application started!");
    }
}