#!/bin/bash

# ==========================================
# Sustained Load Test
# Goal: Test system stability under sustained high load
# ==========================================

echo "================================================"
echo "Sustained Load Test"
echo "Goal: Maintain high load to verify stability"
echo "================================================"

# Configuration
ALB_URL="${ALB_URL:-http://your-alb-url.amazonaws.com}"
USERS="${USERS:-180}"
SPAWN_RATE="${SPAWN_RATE:-10}"
DURATION="${DURATION:-15m}"

echo ""
echo "Configuration:"
echo "  Target: $ALB_URL"
echo "  Max Users: $USERS"
echo "  Spawn Rate: $SPAWN_RATE users/second"
echo "  Duration: $DURATION (LONG!)"
echo ""
echo "Expected Behavior:"
echo "  - All services should scale to 3 instances"
echo "  - Response times should stabilize"
echo "  - No memory leaks or degradation over time"
echo "  - ~15% expected failure rate (10% payment + 10% warehouse - some overlap)"
echo ""

read -p "Press Enter to start sustained test (Ctrl+C to cancel)..."

# Run Locust in headless mode
locust -f locustfile.py \
    --host "$ALB_URL" \
    --users "$USERS" \
    --spawn-rate "$SPAWN_RATE" \
    --run-time "$DURATION" \
    --headless \
    --html reports/sustained_test_$(date +%Y%m%d_%H%M%S).html \
    --csv reports/sustained_test_$(date +%Y%m%d_%H%M%S)

echo ""
echo "================================================"
echo "Sustained Test Complete!"
echo "Check reports/ directory for results"
echo "================================================"