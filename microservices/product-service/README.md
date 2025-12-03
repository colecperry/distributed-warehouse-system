# Product Service

RESTful API for managing products in the eCommerce system. Deployed on AWS ECS as a Docker container.

## Database Integration
- **Type**: Distributed Leader-Follower (W=1, R=5)
- **Connection**: Configured via `database.url` in `application.properties`
- **Storage**: Products stored as JSON with keys: `product_{id}`

## Configuration

**Local Development** (`application.properties`):
```properties
server.port=8085
database.url=http://localhost:8080  # Database Leader node
```

**Production**:
- Update `database.url` to point to deployed database service
- Configure via environment variables or AWS Parameter Store

## Running Locally

**Prerequisites:**
- Database service must be running (see database/README.md)
```bash
# Start database first
cd /path/to/database
docker-compose up -d

# Start product service
cd product-service
mvn clean package -DskipTests
mvn spring-boot:run
```

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
- Jackson (JSON serialization)
- Lombok (optional, for cleaner code)
