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

- **Product Service** (Port 8083) - Manages product catalog (uses R=1 read strategy)
- **Shopping Cart Service** (Port 8084) - Manages carts and checkout (uses R=5 read strategy)
- **Credit Card Service** (Port 8082) - Authorizes payments (90% accept, 10% decline)
- **Warehouse Service** (Port 8081) - Handles inventory and shipping
- **Database Service** (Ports 9080-9084) - Leader-Follower distributed database (5 nodes)

### Database Strategy

- **W=1**: Writes go to Leader only (fast writes)
- **R=1 or R=5**: Configurable read strategy per microservice
  - **R=1**: Product Service uses single-node reads (fast, ~550ms median)
  - **R=5**: Shopping Cart Service uses all-node reads (strong consistency, ~670ms median)
- Products stored with keys: `product_{id}`
- Shopping carts stored with keys: `cart_{id}`

#### Read Strategy Configuration

Each microservice configures its read strategy based on workload:

| Service | Strategy | Reason | Performance |
|---------|----------|--------|-------------|
| Product Service | R=1 | Read-heavy, speed > consistency | ~550ms median |
| Shopping Cart Service | R=5 | Write-heavy, consistency critical | ~670ms median |

**Configuration:**
- Product Service: `database.readStrategy=R1` in `application.properties`
- Shopping Cart Service: `database.readStrategy=R5` in `application.properties`

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

**Recommended: Use Docker Compose (starts all 10 services)**

```bash
# From project root
docker-compose up --build -d

# Check status
docker-compose ps

# View logs
docker-compose logs -f
```

This starts:
- 5 Database nodes (leader + 4 followers on ports 9080-9084)
- 4 Microservices (ports 8081-8084)
- 1 RabbitMQ (ports 5672, 15672)

**Alternative: Start Individual Services**

See `DOCKER-SETUP.md` for detailed instructions on starting services individually.

## API Examples

### Create Product
```bash
curl -X POST http://localhost:8083/products \
  -H "Content-Type: application/json" \
  -d '{
    "product_id": 1,
    "sku": "TEST-001",
    "manufacturer": "TestCorp",
    "category_id": 1,
    "weight": 100,
    "some_other_id": 1
  }'
```

### Create Cart
```bash
curl -X POST http://localhost:8084/shopping-cart \
  -H "Content-Type: application/json" \
  -d '{"customer_id": 100}'
```

### Add Item to Cart
```bash
curl -X POST http://localhost:8084/shopping-carts/1/addItem \
  -H "Content-Type: application/json" \
  -d '{"productId": 1, "quantity": 5}'
```

### Checkout
```bash
curl -X POST http://localhost:8084/shopping-carts/1/checkout \
  -H "Content-Type: application/json" \
  -d '{"credit_card_number": "1234-5678-9012-3456"}'
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

- Product Service: `http://localhost:8083` (R=1 read strategy)
- Shopping Cart Service: `http://localhost:8084` (R=5 read strategy)
- Credit Card Service: `http://localhost:8082`
- Warehouse Service: `http://localhost:8081`
- Database Leader: `http://localhost:9080`
- Database Followers: `http://localhost:9081-9084`
- RabbitMQ: `localhost:5672` (Management UI: `localhost:15672`)

### Read Strategy Configuration

Each microservice configures its read strategy in `application.properties`:

**Product Service** (`microservices/product-service/src/main/resources/application.properties`):
```properties
database.readStrategy=R1  # Fast, single-node reads
```

**Shopping Cart Service** (`microservices/shopping-cart-service/src/main/resources/application.properties`):
```properties
database.readStrategy=R5  # Strong consistency, all-node reads
```

## Testing

### Load Testing

For load testing, use the Locust scripts in `locust-tests/` directory.

**Test Read Strategies Locally:**

1. **Preload products:**
```bash
for i in {1..100}; do
  curl -s -X POST http://localhost:8083/products \
    -H "Content-Type: application/json" \
    -d "{\"product_id\": $i, \"sku\": \"PRODUCT-$i\", \"manufacturer\": \"TestCo\", \"category_id\": 1, \"weight\": 100, \"some_other_id\": $i}" \
    > /dev/null
done
```

2. **Test Product Service (R=1):**
```bash
cd locust-tests
locust -f locust_products_only.py --host=http://localhost:8083 --headless -u 10 -r 2 -t 60s
```

3. **Test Shopping Cart Service (R=5):**
```bash
cd locust-tests
locust -f locust_carts_only.py --host=http://localhost:8084 --headless -u 10 -r 2 -t 60s
```

**Expected Results:**
- Product Service (R=1): Median ~550ms, 0 failures
- Shopping Cart Service (R=5): Median ~670ms, 0 failures
- R=5 is ~22% slower but provides strong consistency

### Verify Read Strategies

Check database logs to confirm strategies are working:

```bash
# Watch for R=1 reads (Product Service)
docker-compose logs w1r5-leader | grep "R=1"

# Watch for R=5 reads (Shopping Cart Service)
docker-compose logs w1r5-leader | grep "R=5"
```

## Notes

- Product IDs and Cart IDs are auto-generated
- Credit Card Service randomly accepts 90% and declines 10% of payments
- Warehouse Service has no database (always succeeds)
- All delays are random (100-1000ms) to simulate realistic processing times
