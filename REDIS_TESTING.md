# Redis Cache Testing Guide

This guide walks through testing the Redis cache implementation for the Product Service.

## Prerequisites

1. Docker installed and running
2. Database service running (see `database/leader-follower/README.md`)
3. Java 17+ and Maven installed
4. `curl` or `httpie` for API testing (or use Postman)

## Step 13: Local Testing

### 13.1: Start Local Redis

```bash
# Start Redis in Docker (runs on port 6379)
docker run -d --name redis-test -p 6379:6379 redis:latest

# Verify Redis is running
docker ps | grep redis
```

### 13.2: Start Database Service (if not already running)

```bash
cd database/leader-follower
docker-compose up -d
```

### 13.3: Start Product Service with Redis

```bash
cd microservices/product-service

# Set Redis environment variables
export REDIS_HOST=localhost
export REDIS_PORT=6379
export DATABASE_URL=http://localhost:9080

# Build and run
mvn clean package -DskipTests
mvn spring-boot:run
```

The service should start on port 8083. Look for logs like:
```
Started ProductServiceApplication
```

### 13.4: Test Cache Miss (First Read)

**Create a product:**
```bash
curl -X POST http://localhost:8083/products \
  -H "Content-Type: application/json" \
  -d '{
    "product_id": 1,
    "sku": "SKU-001",
    "manufacturer": "TestCorp",
    "category_id": 1,
    "weight": 500,
    "some_other_id": 42
  }'
```

Expected response: `{"product_id":1}`

**Read the product (First time - Cache MISS):**
```bash
curl http://localhost:8083/products/1
```

Check the logs - you should see:
```
Cache MISS for product: 1, querying database
Cached product: 1 with TTL: 3600 seconds
```

Expected response:
```json
{
  "product_id": 1,
  "sku": "SKU-001",
  "manufacturer": "TestCorp",
  "category_id": 1,
  "weight": 500,
  "some_other_id": 42
}
```

### 13.5: Test Cache Hit (Second Read)

**Read the same product again:**
```bash
curl http://localhost:8083/products/1
```

Check the logs - you should see:
```
Cache HIT for product: 1
```

The second request should be **much faster** (should complete in <100ms vs 100-1000ms).

### 13.6: Verify Redis Contains the Key

Connect to Redis and check:
```bash
# Connect to Redis CLI
docker exec -it redis-test redis-cli

# Check if key exists
EXISTS product_1
# Should return: (integer) 1

# Get the cached value
GET product_1
# Should show the JSON product data

# Check TTL (Time To Live)
TTL product_1
# Should show seconds remaining (less than 3600)

# Exit Redis CLI
exit
```

## Step 14: Verify Cache Behavior

### 14.1: Check Cache Statistics

```bash
curl http://localhost:8083/products/cache/stats
```

Expected response:
```json
{
  "cacheHits": 1,
  "cacheMisses": 1,
  "hitRate": "50.00%",
  "totalRequests": 2
}
```

### 14.2: Test Multiple Reads (Should see mostly hits)

```bash
# Read the same product 10 times
for i in {1..10}; do
  echo "Request $i:"
  curl -s -o /dev/null -w "Time: %{time_total}s\n" http://localhost:8083/products/1
done
```

Expected: Most requests should be very fast (<0.01s) because of cache hits.

### 14.3: Test Cache Invalidation

**Create a new product (invalidates cache):**
```bash
curl -X POST http://localhost:8083/products \
  -H "Content-Type: application/json" \
  -d '{
    "product_id": 2,
    "sku": "SKU-002",
    "manufacturer": "NewCorp",
    "category_id": 2,
    "weight": 600,
    "some_other_id": 50
  }'
```

Check logs - should see:
```
Invalidated cache for product: 2
```

**Read the new product:**
```bash
curl http://localhost:8083/products/2
```

Should see cache MISS (first read after creation).

### 14.4: Test TTL Expiration (Optional)

This test requires waiting 1 hour. For faster testing, you can:
1. Manually delete the key in Redis
2. Or modify the TTL in `application.properties` to a shorter value (e.g., 60 seconds)

```bash
# Connect to Redis
docker exec -it redis-test redis-cli

# Manually expire the key
DEL product_1

# Or set a short TTL for testing (10 seconds)
EXPIRE product_1 10

# Wait 10 seconds, then read again - should be cache MISS
```

## Step 15: Load Testing

### 15.1: Use Locust (Already Set Up)

The project already has Locust tests in `locust-tests/`.

**Start Locust:**
```bash
cd locust-tests

# Activate virtual environment (if exists)
source venv/bin/activate  # On macOS/Linux
# OR
.\venv\Scripts\activate  # On Windows

# Install dependencies (if needed)
pip install -r requirements.txt

# Start Locust
locust -f locust_products_only.py --host=http://localhost:8083
```

**Or use the existing load test script:**
```bash
python load_products.py
```

### 15.2: Test Cache Warm-Up

1. **First Run (Cold Cache - All Misses):**
   - Run load test with 1000 requests
   - Check cache stats: Should show ~0% hit rate initially
   - As the test runs, hit rate should increase

2. **Second Run (Warm Cache - Mostly Hits):**
   - Run the same load test immediately after
   - Check cache stats: Should show >80% hit rate
   - Response times should be much faster

**Check cache stats during/after load test:**
```bash
curl http://localhost:8083/products/cache/stats
```

Expected after warm-up:
- Hit rate: >80%
- Average response time: Much lower for cached requests

## Step 16: Cache Invalidation Test

### 16.1: Create and Cache a Product

```bash
# Create product
curl -X POST http://localhost:8083/products \
  -H "Content-Type: application/json" \
  -d '{
    "product_id": 99,
    "sku": "SKU-099",
    "manufacturer": "TestCorp",
    "category_id": 1,
    "weight": 500,
    "some_other_id": 42
  }'

# Read it (cache miss, then cached)
curl http://localhost:8083/products/99

# Read again (cache hit)
curl http://localhost:8083/products/99
```

Check logs - second read should be cache HIT.

### 16.2: Invalidate Cache

**Update the product (triggers cache invalidation):**
```bash
# Create again with same ID (simulates update)
curl -X POST http://localhost:8083/products \
  -H "Content-Type: application/json" \
  -d '{
    "product_id": 99,
    "sku": "SKU-099-UPDATED",
    "manufacturer": "UpdatedCorp",
    "category_id": 2,
    "weight": 700,
    "some_other_id": 99
  }'
```

Check logs - should see:
```
Invalidated cache for product: 99
```

### 16.3: Verify Fresh Data

```bash
# Read product again
curl http://localhost:8083/products/99
```

Should show:
- Cache MISS (because we invalidated it)
- Fresh data from database (with updated values)

Expected response:
```json
{
  "product_id": 99,
  "sku": "SKU-099-UPDATED",
  "manufacturer": "UpdatedCorp",
  ...
}
```

## Cleanup

```bash
# Stop Product Service (Ctrl+C)

# Stop Redis
docker stop redis-test
docker rm redis-test

# Stop Database (if needed)
cd database/leader-follower
docker-compose down
```

## Troubleshooting

### Redis Connection Errors

**Error: Unable to connect to Redis**
- Check if Redis is running: `docker ps | grep redis`
- Check Redis logs: `docker logs redis-test`
- Verify port 6379 is not in use: `lsof -i :6379`

### Cache Not Working

**Check:**
1. Redis is accessible: `docker exec -it redis-test redis-cli PING` (should return `PONG`)
2. Environment variables are set: `echo $REDIS_HOST`
3. Logs show cache operations (look for "Cache HIT" or "Cache MISS")
4. Product Service can connect: Check logs for Redis connection errors

### High Cache Miss Rate

**Possible causes:**
1. TTL too short - check `application.properties`: `cache.product.ttl=3600`
2. Redis memory full - check: `docker exec -it redis-test redis-cli INFO memory`
3. Keys being evicted - check Redis logs

## Success Criteria

✅ **Step 13:**
- Redis starts successfully
- Product Service connects to Redis
- First read shows cache MISS
- Second read shows cache HIT
- Redis contains the key

✅ **Step 14:**
- Cache stats endpoint works
- Hit rate increases over time
- Response times are faster for cache hits

✅ **Step 15:**
- Load test completes
- Hit rate >80% after warm-up
- Response times improve with cache

✅ **Step 16:**
- Cache invalidation works
- Updated data is fresh after invalidation
- Next read fetches from database and re-caches
