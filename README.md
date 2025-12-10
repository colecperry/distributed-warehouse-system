# CS6650 Assignment 5

## Team Members
- Hyunmin (Ryan) Kim
- Cole Perry
- Mustafa Oguz Duman
- Yi-Chia Chu (Erica)

## Overview

This is an eCommerce microservices system with four services: Product, Shopping Cart, Credit Card Authorizer, and Warehouse. The system uses a distributed Leader-Follower database (W=1, R=5) for data persistence and implements simulated ACID transactions.

## Architecture

### Services

- **Product Service** (Port 8085) - Manages product catalog
- **Shopping Cart Service** (Port 8081) - Manages carts and checkout
- **Credit Card Service** (Port 8082) - Authorizes payments (90% accept, 10% decline)
- **Warehouse Service** (Port 8089) - Handles inventory and shipping
- **Database Service** (Port 8080) - Leader-Follower distributed database

### Database Strategy

- **W=1**: Writes go to Leader only (fast writes)
- **R=5**: Reads query all 5 nodes, return newest version (strong consistency)
- Products stored with keys: `product_{id}`
- Shopping carts stored with keys: `cart_{id}`

## Read/Write Ratios

### Add-to-Cart: 2:1 Read-to-Write
- Product Service: 1 READ (validate product)
- Shopping Cart Service: 1 READ (get cart) + 1 WRITE (save cart)
- **Total: 2 Reads, 1 Write**

### Checkout: Pure Read
- Shopping Cart Service: 1 READ (get cart for processing)
- **Total: 1 Read, 0 Writes**

Transaction boundaries (`beginTransaction`, `endTransaction`, `abortTransaction`) only log messages - no actual database modifications.

## Quick Start

### Prerequisites
- Java 17+
- Maven 3.9+
- Docker (for database and RabbitMQ)

### Start Services

1. **Database** (Leader-Follower W1R5):
```bash
cd database/leader-follower-w1r5
docker-compose up -d
```

2. **RabbitMQ**:
```bash
docker run -d --name rabbitmq -p 5672:5672 rabbitmq:3-management
```

3. **Credit Card Service**:
```bash
cd microservices/credit-card-service
mvn spring-boot:run
```

4. **Warehouse Service**:
```bash
cd microservices/warehouse-service
mvn spring-boot:run
```

5. **Product Service**:
```bash
cd microservices/product-service
mvn spring-boot:run
```

6. **Shopping Cart Service**:
```bash
cd microservices/shopping-cart-service
mvn spring-boot:run
```

## API Examples

### Create Product
```bash
curl -X POST http://localhost:8085/product \
  -H "Content-Type: application/json" \
  -d '{"sku":"TEST-001","manufacturer":"TestCorp","category_id":1,"weight":100,"some_other_id":1}'
```

### Create Cart
```bash
curl -X POST http://localhost:8081/shopping-cart \
  -H "Content-Type: application/json" \
  -d '{"customer_id":100}'
```

### Add Item to Cart
```bash
curl -X POST http://localhost:8081/shopping-carts/1/addItem \
  -H "Content-Type: application/json" \
  -d '{"productId":1,"quantity":5}'
```

### Checkout
```bash
curl -X POST http://localhost:8081/shopping-carts/1/checkout \
  -H "Content-Type: application/json" \
  -d '{"credit_card_number":"1234567890"}'
```

## Key Features

- **Simulated Delays**: All endpoints have 100-1000ms random delays for autoscaling simulation
- **Auto-Create Cart**: Carts are automatically created when adding items to non-existent carts
- **Transaction Boundaries**: Simulated ACID transactions with logging (no actual 2PC)
- **Fire-and-Forget**: Orders sent to warehouse via RabbitMQ without blocking checkout
- **Error Handling**: Comprehensive validation and error responses

## Project Structure

```
Assignment5/
├── database/
│   ├── leader-follower-w1r5/     # Distributed database (W=1, R=5)
│   └── data-loader/              # Product data loader
├── microservices/
│   ├── product-service/           # Product catalog service
│   ├── shopping-cart-service/     # Shopping cart and checkout
│   ├── credit-card-service/       # Payment authorization
│   └── warehouse-service/         # Inventory and shipping
├── locust-tests/                  # Load testing scripts
└── terraform/                     # AWS infrastructure as code
```

## Configuration

Service URLs are configured via environment variables or `application.yml`/`application.properties`:

- Product Service: `http://localhost:8085`
- Shopping Cart Service: `http://localhost:8081`
- Credit Card Service: `http://localhost:8082`
- Warehouse Service: `http://localhost:8089`
- Database: `http://localhost:8080`
- RabbitMQ: `localhost:5672`

## Testing

See `TESTING_GUIDE.md` for detailed testing instructions.

For load testing, use the Locust scripts in `locust-tests/` directory.

## Notes

- Product IDs and Cart IDs are auto-generated
- Credit Card Service randomly accepts 90% and declines 10% of payments
- Warehouse Service has no database (always succeeds)
- All delays are random (100-1000ms) to simulate realistic processing times
