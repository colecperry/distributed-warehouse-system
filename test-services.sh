#!/bin/bash

# ==========================================
# Local Service Testing Script
# Tests all microservices health endpoints
# ==========================================

echo "=========================================="
echo "Testing All Microservices Locally"
echo "=========================================="
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test counter
PASS=0
FAIL=0

# Function to test an endpoint
test_endpoint() {
    local name=$1
    local url=$2

    echo -n "Testing $name... "

    response=$(curl -s -o /dev/null -w "%{http_code}" "$url" 2>/dev/null)

    if [ "$response" = "200" ]; then
        echo -e "${GREEN}✓ PASS${NC} (HTTP $response)"
        ((PASS++))
    else
        echo -e "${RED}✗ FAIL${NC} (HTTP $response)"
        ((FAIL++))
    fi
}

# Wait for services to be ready
echo "Waiting for services to start (30 seconds)..."
sleep 30
echo ""

# ==========================================
# Test Health Endpoints
# ==========================================

echo "1. Health Check Tests"
echo "----------------------------------------"
echo "Database Nodes:"
test_endpoint "  DB Leader         " "http://localhost:9080/api/leader/health"
test_endpoint "  DB Follower 1     " "http://localhost:9081/api/follower/health"
test_endpoint "  DB Follower 2     " "http://localhost:9082/api/follower/health"
test_endpoint "  DB Follower 3     " "http://localhost:9083/api/follower/health"
test_endpoint "  DB Follower 4     " "http://localhost:9084/api/follower/health"
echo ""
echo "Microservices:"
test_endpoint "  Warehouse Service " "http://localhost:8081/warehouse/health"
test_endpoint "  Credit Card Service" "http://localhost:8082/credit-card-authorizer/health"
test_endpoint "  Product Service   " "http://localhost:8083/products/health"
test_endpoint "  Shopping Cart Service" "http://localhost:8084/cart/health"
echo ""

# ==========================================
# Test Basic Functionality
# ==========================================

echo "2. Functional Tests"
echo "----------------------------------------"

# Test 1: Create a product
echo -n "Creating test product... "
response=$(curl -s -X POST http://localhost:8083/products \
    -H "Content-Type: application/json" \
    -d '{
        "sku": "TEST-001",
        "manufacturer": "TestCorp",
        "category_id": 1,
        "weight": 100,
        "some_other_id": 999
    }' \
    -w "%{http_code}" -o /tmp/product_response.txt 2>/dev/null)

if [ "$response" = "201" ]; then
    product_id=$(cat /tmp/product_response.txt | grep -o '"product_id":[0-9]*' | grep -o '[0-9]*')
    echo -e "${GREEN}✓ PASS${NC} (Product ID: $product_id)"
    ((PASS++))
else
    echo -e "${RED}✗ FAIL${NC} (HTTP $response)"
    ((FAIL++))
fi

# Test 2: Get the product
if [ ! -z "$product_id" ]; then
    echo -n "Retrieving product $product_id... "
    response=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:8083/products/$product_id" 2>/dev/null)

    if [ "$response" = "200" ]; then
        echo -e "${GREEN}✓ PASS${NC}"
        ((PASS++))
    else
        echo -e "${RED}✗ FAIL${NC} (HTTP $response)"
        ((FAIL++))
    fi
fi

# Test 3: Check warehouse reserve
echo -n "Checking warehouse reserve... "
response=$(curl -s -X POST http://localhost:8081/reserve \
    -H "Content-Type: application/json" \
    -d '{
        "product_id": 1,
        "quantity": 5
    }' \
    -w "%{http_code}" -o /tmp/warehouse_response.txt 2>/dev/null)

if [ "$response" = "200" ]; then
    available=$(cat /tmp/warehouse_response.txt | grep -o '"available":"[^"]*"' | cut -d'"' -f4)
    echo -e "${GREEN}✓ PASS${NC} (Available: $available)"
    ((PASS++))
else
    echo -e "${RED}✗ FAIL${NC} (HTTP $response)"
    ((FAIL++))
fi

# Test 4: Authorize payment
echo -n "Testing credit card auth... "
response=$(curl -s -X POST http://localhost:8082/credit-card-authorizer/authorize \
    -H "Content-Type: application/json" \
    -d '{
        "credit_card_number": "1234-5678-9012-3456"
    }' \
    -w "%{http_code}" -o /tmp/cc_response.txt 2>/dev/null)

if [ "$response" = "200" ] || [ "$response" = "402" ]; then
    status=$(cat /tmp/cc_response.txt | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
    echo -e "${GREEN}✓ PASS${NC} (Status: $status)"
    ((PASS++))
else
    echo -e "${RED}✗ FAIL${NC} (HTTP $response)"
    ((FAIL++))
fi

# Test 5: Create shopping cart
echo -n "Creating shopping cart... "
response=$(curl -s -X POST http://localhost:8084/shopping-cart \
    -H "Content-Type: application/json" \
    -d '{
        "customer_id": 1
    }' \
    -w "%{http_code}" -o /tmp/cart_response.txt 2>/dev/null)

if [ "$response" = "201" ]; then
    cart_id=$(cat /tmp/cart_response.txt | grep -o '"shopping_cart_id":[0-9]*' | grep -o '[0-9]*')
    echo -e "${GREEN}✓ PASS${NC} (Cart ID: $cart_id)"
    ((PASS++))
else
    echo -e "${RED}✗ FAIL${NC} (HTTP $response)"
    ((FAIL++))
fi

# Cleanup temp files
rm -f /tmp/product_response.txt /tmp/warehouse_response.txt /tmp/cc_response.txt /tmp/cart_response.txt

# ==========================================
# Summary
# ==========================================

echo ""
echo "=========================================="
echo "Test Summary"
echo "=========================================="
echo -e "Passed: ${GREEN}$PASS${NC}"
echo -e "Failed: ${RED}$FAIL${NC}"
echo "=========================================="

if [ $FAIL -eq 0 ]; then
    echo -e "${GREEN}All tests passed! ✓${NC}"
    echo ""
    echo "System is ready for Locust load testing!"
    exit 0
else
    echo -e "${RED}Some tests failed! ✗${NC}"
    echo ""
    echo "Check docker-compose logs for errors:"
    echo "  docker-compose logs [service-name]"
    exit 1
fi