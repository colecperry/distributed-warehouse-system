# Shopping Cart Service

Handles shopping cart operations and checkout flow for the eCommerce system.

## Database Integration
- **Type**: Distributed Leader-Follower (W=1, R=5)
- **Read Strategy**: R=5 (strong consistency, all-node reads) - optimized for write-heavy cart operations
- **Connection**: Configured via `database.url` in `application.properties`
- **Storage**: Carts stored as JSON with keys: `cart_{id}`

## Configuration

**Local Development** (`application.properties` and `application.yml`):
```properties
server.port=8084
database.url=http://localhost:9080  # Database Leader node
database.readStrategy=R5  # Strong consistency, all-node reads for cart operations
```
```yaml
spring:
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USER:guest}
    password: ${RABBITMQ_PASS:guest}

credit:
  card:
    authorizer:
      url: ${CCA_URL:http://localhost:8082/credit-card-authorizer/authorize}
```

## Running Locally

**Prerequisites:**
- Database service running
- (Optional) RabbitMQ for warehouse orders
- (Optional) Credit Card Authorizer service
```bash
# Start database first
cd /path/to/database
docker-compose up -d

# Start shopping cart service
cd shopping-cart-service
mvn clean package -DskipTests
mvn spring-boot:run
```

## Endpoints

### Create Shopping Cart
```bash
POST /shopping-cart
Body: {"customer_id": 100}
Returns: {"shopping_cart_id": 1}
```

### Add Item to Cart
```bash
POST /shopping-carts/{id}/addItem
Body: {"productId": 42, "quantity": 3}
Returns: 204 No Content
Errors: 404 if cart doesn't exist, 400 if invalid input
```

### Checkout Cart
```bash
POST /shopping-carts/{id}/checkout
Body: {"credit_card_number": "1234567890"}
Returns: {"order_id": 1}
Errors: 404 if cart not found, 402 if payment declined
```

### Health Check
```bash
GET /hello
Returns: "Hello from Shopping Cart Service!"
```

## Features
- Simulated delay: 100-1000ms per request (for autoscaling testing)
- Persistent cart storage via distributed database
- Credit card authorization integration
- RabbitMQ integration for warehouse orders (fire-and-forget)
- Full error handling with proper HTTP status codes
- Input validation (quantity 1-10,000, valid product IDs)

## Docker Deployment

**TODO:** RabbitMQ integration is implemented but needs configuration in production.

**Run with Docker:**
```bash
docker run -d \
  --name shopping-cart-service \
  --network my-microservices \
  -p 8084:8084 \
  -e DATABASE_URL=http://w1r5-leader:9080 \
  -e DATABASE_READ_STRATEGY=R5 \
  -e CCA_URL=http://credit-card-authorizer:8082/credit-card-authorizer/authorize \
  -e RABBITMQ_HOST=rabbitmq \
  shopping-cart-service
```

## Requirements
- Java 17
- Spring Boot 3.5.6
- Maven 3.9+
- Database service running on configured port

## Dependencies
- Spring Boot Starter Web
- Spring Boot Starter AMQP (RabbitMQ)
- Jackson (JSON serialization)
- Lombok