# Locust Load Testing for Assignment 5

Realistic load testing that simulates customer shopping behavior and drives auto-scaling.

## 📁 File Structure

```
locust-tests/
├── locustfile.py              # Main test for AWS ALB (all services)
├── locust_products_only.py    # Product Service only (R=1 testing)
├── locust_carts_only.py       # Shopping Cart Service only (R=5 testing)
├── load_products.py           # Preload products script
├── requirements.txt           # Python dependencies
└── README.md                  # This file
```

## 🎯 Test Scenarios

### Use Case 1: Add Item to Cart (70% of actions)
**Realistic shopping behavior:**
1. Browse product (GET /products/{id})
2. Check warehouse inventory (POST /warehouse/reserve)
3. Add to cart (POST /cart or POST /cart/{id}/items)

**Expected Results:**
- 90% success (warehouse has stock)
- 10% out of stock

### Use Case 2: Checkout (30% of actions)
**Complete purchase flow:**
1. Customer adds credit card info
2. Authorize payment (POST /credit-card-authorizer/authorize)
3. Ship items (POST /warehouse/ship)
4. Complete checkout (POST /cart/{id}/checkout)

**Expected Results:**
- 70% of customers with carts attempt checkout
- 30% abandon cart (realistic behavior)
- 90% payment approval rate
- Final success rate: ~63% (90% payment × 70% attempt rate)

## 🚀 Quick Start

### 1. Install Dependencies

```bash
cd locust-tests

# Create virtual environment (recommended)
python3 -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate

# Install requirements
pip install -r requirements.txt
```

### 2. Get Your ALB URL

```bash
cd ../terraform
ALB_URL=$(terraform output -raw alb_dns_name)
echo "ALB URL: http://${ALB_URL}"
```

Example: `ecommerce-a5-alb-1234567890.us-west-2.elb.amazonaws.com`

**Note:** The system is deployed in `us-west-2` (Oregon) region.

### 3. Preload Products

Before running load tests, preload products (1-100 minimum):

```bash
cd locust-tests
ALB_URL=$(cd ../terraform && terraform output -raw alb_dns_name)
export ALB_URL="http://${ALB_URL}"

# Run product loader
python3 load_products.py
```

The script will:
- Create products 1-1000 (takes ~2-3 minutes)
- Verify products were created
- Wait for database replication

### 4. Run Load Tests

**Option A: Full System Test (AWS ALB)**
```bash
cd locust-tests
ALB_URL=$(cd ../terraform && terraform output -raw alb_dns_name)

locust -f locustfile.py \
  --host=http://${ALB_URL} \
  --headless \
  --users=500 \
  --spawn-rate=20 \
  --run-time=10m \
  --html=load_test_report.html
```

**Option B: Local Testing (Single Service)**

For testing individual services locally:

```bash
# Test Product Service only (R=1)
locust -f locust_products_only.py \
  --host=http://localhost:8083 \
  --headless \
  --users=10 \
  --spawn-rate=2 \
  --run-time=60s

# Test Shopping Cart Service only (R=5)
locust -f locust_carts_only.py \
  --host=http://localhost:8084 \
  --headless \
  --users=10 \
  --spawn-rate=2 \
  --run-time=60s
```

**Expected Results (AWS):**
- Total RPS: ~272 requests/second
- Success Rate: 99.5% (payment declines are expected failures)
- Average Response Time: ~946ms

See `SCALING_AND_PERFORMANCE.md` for detailed performance analysis.