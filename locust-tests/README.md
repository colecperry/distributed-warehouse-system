# Locust Load Testing for Assignment 5

Realistic load testing that simulates customer shopping behavior and drives auto-scaling.

## 📁 File Structure

```
locust-tests/
├── locustfile.py              # Main test configuration
├── requirements.txt           # Python dependencies
├── run_gradual_test.sh        # Gradual ramp-up test
├── run_sustained_test.sh      # Long-duration test
├── reports/                   # Generated test reports
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
terraform output alb_dns_name
```

Example: `ecommerce-a5-alb-1234567890.us-east-1.elb.amazonaws.com`

### 3. Set Environment Variable

```bash
export ALB_URL="http://ecommerce-a5-alb-1234567890.us-east-1.elb.amazonaws.com"
```

### 4. Run Tests

```bash
# Make scripts executable
chmod +x *.sh

# Run gradual ramp-up test (recommended first)
./run_gradual_test.sh
```
