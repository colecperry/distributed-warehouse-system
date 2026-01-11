# Product Service

RESTful API for managing products in the eCommerce system. Deployed on AWS ECS as a Docker container. Uses Redis cache for improved read performance.

## Database Integration
- **Type**: Distributed Leader-Follower (W=1, R=1)
- **Read Strategy**: R=1 (fast, single-node reads) - optimized for read-heavy product browsing
- **Connection**: Configured via `database.url` in `application.properties`
- **Storage**: Products stored as JSON with keys: `product_{id}`

## Redis Caching
- **Pattern**: Cache-aside (lazy loading)
- **TTL**: 1 hour (3600 seconds)
- **Storage**: Product objects cached in Redis with JSON serialization
- **Metrics**: Cache hit/miss tracking via `/products/cache/stats` endpoint
- **Connection**: Configured via `REDIS_HOST` and `REDIS_PORT` environment variables

## Configuration

**Local Development** (`application.properties`):
```properties
server.port=8083
database.url=http://localhost:9080  # Database Leader node
database.readStrategy=R1  # Fast, single-node reads for product browsing
```

**Production**:
- Update `database.url` to point to deployed database service
- Configure via environment variables or AWS Parameter Store

## Running Locally

**Prerequisites:**
- Database service must be running (see database/README.md)
- Redis must be running for caching (optional but recommended)

```bash
# Start database first
cd database/leader-follower
docker-compose up -d

# Start Redis (for caching)
docker run -d --name redis-test -p 6379:6379 redis:latest

# Start product service with Redis environment variables
cd microservices/product-service
export REDIS_HOST=localhost
export REDIS_PORT=6379
export DATABASE_URL=http://localhost:9080
mvn clean package -DskipTests
mvn spring-boot:run
```

**Note:** Redis is optional - service will fallback to database only if Redis is unavailable.

## Endpoints

**Create Product:**
```bash
POST /product
Body: {
  "sku": "SKU-001",
  "manufacturer": "TestCorp",
  "category_id": 1,
  "weight": 500,
  "some_other_id": 42
}
Returns: {"product_id": 1}
```

**Get Product:**
```bash
GET /products/{productId}
Returns: Full product JSON or 404 if not found
# First read: Cache MISS (queries database)
# Subsequent reads: Cache HIT (returns from Redis)
```

**Get Cache Statistics:**
```bash
GET /products/cache/stats
Returns: Cache hit/miss statistics and hit rate
```

## Features
- Simulated delay: 100-1000ms per request (for autoscaling testing)
- Persistent storage via distributed database
- Thread-safe ID generation
- Full error handling with proper HTTP status codes

## AWS Deployment

### ECR Image URL
```bash
aws ecr describe-repositories \
  --repository-names product-service \
  --region us-east-1 \
  --query 'repositories[0].repositoryUri' \
  --output text
```

**Current ECR URL:**
```
637423169516.dkr.ecr.us-east-1.amazonaws.com/product-service
```

### Load Testing
Update the server URL in `LoadTestClient.java`:
```java
private static final String SERVER_URL = "http://54.227.106.63:8080";
```

Run tests:
```bash
mvn clean compile
mvn exec:java -Dexec.mainClass="com.cs6650.client.LoadTestClient"
```

## Requirements
- Java 17 or 21
- Spring Boot 3.5.6
- Maven 3.9+
- Database service running on configured port

## Dependencies
- Spring Boot Starter Web
- Spring Data Redis (for caching)
- Lettuce (Redis client)
- Jackson (JSON serialization)
- Lombok (optional, for cleaner code)
