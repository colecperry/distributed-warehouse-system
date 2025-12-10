# Docker Compose Setup Guide

Complete guide for running all 10 services locally for testing.

---

## What Gets Started

When you run `docker-compose up`, you start:

### Database Cluster (5 containers):
1. **w1r5-leader** - Database leader node (port 9080)
2. **w1r5-follower1** - Follower 1 (port 9081)
3. **w1r5-follower2** - Follower 2 (port 9082)
4. **w1r5-follower3** - Follower 3 (port 9083)
5. **w1r5-follower4** - Follower 4 (port 9084)

### Microservices (4 containers):
6. **warehouse-service** - Inventory (port 8081)
7. **credit-card-service** - Payments (port 8082)
8. **product-service** - Catalog (port 8083)
9. **shopping-cart-service** - Cart/Checkout (port 8084)

### Message Broker (1 container):
10. **rabbitmq** - Message queue (port 5672, UI: 15672)

---

## Starting the System

### Method 1: Foreground (See All Logs)
```bash
# From project root
docker-compose up --build

# Press Ctrl+C to stop
```

### Method 2: Background (Detached)
```bash
docker-compose up --build -d

# View logs
docker-compose logs -f

# Stop when done
docker-compose down
```

### Method 3: Start Specific Services Only
```bash
# Just database
docker-compose up w1r5-leader w1r5-follower1 w1r5-follower2 w1r5-follower3 w1r5-follower4

# Just microservices
docker-compose up warehouse-service credit-card-service product-service shopping-cart-service

# Just one service for debugging
docker-compose up credit-card-service
```

---

## Monitoring & Debugging

### Check Service Status
```bash
# See all running containers
docker-compose ps

# Should show 10 services with "Up" and "healthy" status
```

### View Logs
```bash
# All services
docker-compose logs

# Specific service
docker-compose logs credit-card-service

# Follow logs (real-time)
docker-compose logs -f shopping-cart-service

# Last 50 lines
docker-compose logs --tail=50 product-service
```

### Test Health Endpoints
```bash
# Database
curl http://localhost:9080/api/leader/health
curl http://localhost:9081/api/follower/health

# Microservices
curl http://localhost:8081/warehouse/health
curl http://localhost:8082/credit-card-authorizer/health
curl http://localhost:8083/products/health
curl http://localhost:8084/cart/health
```

### RabbitMQ Management UI
```bash
# Open in browser
open http://localhost:15672

# Login credentials:
# Username: admin
# Password: admin

# Check the "Queues" tab for warehouse-orders queue
```

---

## Testing Workflows

### Test 1: Create Product
```bash
curl -X POST http://localhost:8083/products \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "TEST-123",
    "manufacturer": "TestCorp",
    "category_id": 1,
    "weight": 500,
    "some_other_id": 1
  }'

# Returns: {"product_id":1}
```

### Test 2: Check Warehouse
```bash
curl -X POST http://localhost:8081/reserve \
  -H "Content-Type: application/json" \
  -d '{"product_id": 1, "quantity": 5}'

# Returns: {"product_id":"1","quantity":"5","available":"yes"}
# (or "no" 10% of the time)
```

### Test 3: Create Shopping Cart
```bash
curl -X POST http://localhost:8084/shopping-cart \
  -H "Content-Type: application/json" \
  -d '{"customer_id": 100}'

# Returns: {"shopping_cart_id":1}
```

### Test 4: Add Item to Cart
```bash
curl -X POST http://localhost:8084/shopping-carts/1/addItem \
  -H "Content-Type: application/json" \
  -d '{"productId": 1, "quantity": 2}'

# Returns: 204 No Content
```

### Test 5: Checkout
```bash
curl -X POST http://localhost:8084/shopping-carts/1/checkout \
  -H "Content-Type: application/json" \
  -d '{"credit_card_number": "1234-5678-9012-3456"}'

# Returns: {"order_id":1}
# (or 402 Payment Required if declined)
```

### Watch Database Replication
After any database write, check logs:
```bash
docker-compose logs w1r5-leader | grep "Replication complete"

# Should see:
# Leader stored locally: KeyValue{...}
# Replicated to: http://w1r5-follower1:9081
# Replicated to: http://w1r5-follower2:9082
# Replicated to: http://w1r5-follower3:9083
# Replicated to: http://w1r5-follower4:9084
# Replication complete
```

---


---

## Building Individual Services

If you need to build just one service:

```bash
cd microservices/credit-card-service

# Build
docker build -t credit-card-service:test .

# Run standalone
docker run -p 8082:8082 credit-card-service:test

# Test
curl http://localhost:8082/credit-card-authorizer/health
```

---

## Cleanup

### Stop All Services
```bash
docker-compose down
```

### Stop and Remove Volumes
```bash
docker-compose down -v
```

### Remove All Images (Fresh Start)
```bash
docker-compose down -v
docker system prune -a -f
```



---

## Success Criteria

When everything is working correctly, you should see:

```bash
docker-compose ps

# Output:
NAME                    STATUS
w1r5-leader            Up (healthy)
w1r5-follower1         Up (healthy)
w1r5-follower2         Up (healthy)
w1r5-follower3         Up (healthy)
w1r5-follower4         Up (healthy)
credit-card-service    Up (healthy)
warehouse-service      Up (healthy)
product-service        Up (healthy)
shopping-cart-service  Up (healthy)
rabbitmq               Up (healthy)
```


---

## Understanding the System

### Why 10 Services?
- **5 Database nodes**: Demonstrates distributed W=1, R=5 strategy
- **4 Microservices**: Core eCommerce business logic
- **1 RabbitMQ**: Asynchronous message processing

### Why Different Ports?
- **9080-9084 (Database)**: Separate from microservices to avoid conflicts
- **8081-8084 (Microservices)**: Assignment-specified ports
- **5672 (RabbitMQ)**: Standard AMQP port

### Why Docker Compose Locally?
- Easy to test full system integration
- Fast iteration during development
- Proves everything works before AWS deployment
- No AWS costs during development

---