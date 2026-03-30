# Distributed eCommerce System

## Overview

This is an eCommerce microservices system with four services: Product, Shopping Cart, Credit Card Authorizer, and Warehouse. The system uses a distributed Leader-Follower database (W=1, R=5) for data persistence and implements simulated ACID transactions.

## Architecture

### Services

- **Product Service** (Port 8083) - Manages product catalog (uses R=1 read strategy, Redis cache)
- **Shopping Cart Service** (Port 8084) - Manages carts and checkout (uses R=5 read strategy)
- **Credit Card Service** (Port 8082) - Authorizes payments (90% accept, 10% decline)
- **Warehouse Service** (Port 8081) - Handles inventory and shipping
- **Database Service** (Ports 9080-9084) - Leader-Follower distributed database (5 nodes)
- **Redis Cache** - ElastiCache Redis for Product Service caching (cache-aside pattern)

### Database Strategy

- **W=1**: Writes go to Leader only (fast writes)
- **R=1 or R=5**: Configurable read strategy per microservice
  - **R=1**: Product Service uses single-node reads (fast, ~550ms median)
  - **R=5**: Shopping Cart Service uses all-node reads (strong consistency, ~670ms median)
- Products stored with keys: `product_{id}`
- Shopping carts stored with keys: `cart_{id}`

#### Read Strategy Configuration

Each microservice configures its read strategy based on workload:

| Service               | Strategy |              Reason            | Performance |
|-----------------------|----------|-----------------------------------|--------|
| Product Service       | R=1      | Read-heavy, speed > consistency   | ~550ms |
| Shopping Cart Service | R=5      | Write-heavy, consistency critical | ~670ms |

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
- Docker (for database, RabbitMQ, and Redis)

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

**Note:** Redis cache testing requires separate Docker container:
```bash
docker run -d --name redis-test -p 6379:6379 redis:latest
```

**Alternative: Start Individual Services**

```bash
# Just database
docker-compose up w1r5-leader w1r5-follower1 w1r5-follower2 w1r5-follower3 w1r5-follower4

# Just microservices
docker-compose up warehouse-service credit-card-service product-service shopping-cart-service
```

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

- **Redis Caching**: Product Service uses Redis cache (cache-aside pattern, 1-hour TTL)
- **Auto-Create Cart**: Carts are automatically created when adding items to non-existent carts
- **Transaction Boundaries**: Simulated ACID transactions with logging (no actual 2PC)
- **Fire-and-Forget**: Orders sent to warehouse via RabbitMQ without blocking checkout
- **Error Handling**: Comprehensive validation and error responses

## Project Structure

```
distributed-warehouse-system/
├── database/
│   ├── leader-follower/          # Distributed database (W=1, R=1/R=5)
│   └── data-loader/              # Product data loader
├── microservices/
│   ├── product-service/          # Product catalog (R=1, Redis cache)
│   ├── shopping-cart-service/    # Cart and checkout orchestrator (R=5)
│   ├── credit-card-service/      # Payment authorization
│   └── warehouse-service/        # Inventory and shipping
├── locust-tests/                 # Load testing scripts
├── terraform/                    # AWS ECS infrastructure as code
└── docker-compose.yml            # Local development orchestration
```

## Configuration

Service URLs are configured via environment variables or `application.yml`/`application.properties`:

- Product Service: `http://localhost:8083` (R=1 read strategy, Redis cache)
- Shopping Cart Service: `http://localhost:8084` (R=5 read strategy)
- Credit Card Service: `http://localhost:8082`
- Warehouse Service: `http://localhost:8081`
- Database Leader: `http://localhost:9080`
- Database Followers: `http://localhost:9081-9084`
- RabbitMQ: `localhost:5672` (Management UI: `localhost:15672`)
- Redis: `localhost:6379` (for Product Service caching)

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

### Redis Cache Testing

Start Redis locally and hit the cache stats endpoint:

```bash
docker run -d --name redis-test -p 6379:6379 redis:latest
curl http://localhost:8083/products/cache/stats
```

### Load Testing

See `locust-tests/README.md` for full load testing instructions. Quick start:

```bash
cd locust-tests
pip install -r requirements.txt
locust -f locustfile.py --host=http://localhost:8084
```

## AWS Deployment

Deployed to AWS ECS Fargate with memory-based auto-scaling (1–10 instances per service, 40% threshold). Handles 272 RPS with 99.5% success rate.

See `terraform/README.md` for full deployment instructions.

```bash
cd terraform
./start-services.sh   # Creates infrastructure + deploys (~10-15 min)
./stop-services.sh    # Destroys everything — zero charges after completion
```

### Database Service

The database service is deployed as an ECS service alongside the microservices. It includes:
- **Leader node**: Handles all writes and read coordination
- **R=1/R=5 read strategies**: Configured per microservice
- **ALB routing**: `/api/*` routes to database leader

### Full Deployment Instructions

See `terraform/README.md` for complete deployment instructions and manual steps.

## Notes

- Credit Card Service randomly accepts 90% and declines 10% of payments
- Warehouse Service has no database (always returns available)
- Transaction boundaries (beginTransaction/endTransaction/abortTransaction) are logged but not a real 2PC implementation
