#!/bin/bash

# ==========================================
# Gradual Ramp-Up Load Test
# Goal: Find the breaking point by gradually increasing load
# ==========================================

echo "================================================"
echo "Gradual Ramp-Up Test"
echo "Goal: Gradually increase load until auto-scaling"
echo "================================================"

# Configuration
ALB_URL="${ALB_URL:-http://your-alb-url.amazonaws.com}"
USERS="${USERS:-150}"
SPAWN_RATE="${SPAWN_RATE:-5}"
DURATION="${DURATION:-10m}"

echo ""
echo "Configuration:"
echo "  Target: $ALB_URL"
echo "  Max Users: $USERS"
echo "  Spawn Rate: $SPAWN_RATE users/second"
echo "  Duration: $DURATION"
echo ""
echo "Expected Behavior:"
echo "  - Services start with 1 instance each"
echo "  - At ~70-80 users, services start scaling to 2 instances"
echo "  - At ~120-150 users, services scale to 3 instances (max)"
echo ""

read -p "Press Enter to start test (Ctrl+C to cancel)..."

# Run Locust in headless mode
locust -f locustfile.py \
    --host "$ALB_URL" \
    --users "$USERS" \
    --spawn-rate "$SPAWN_RATE" \
    --run-time "$DURATION" \
    --headless \
    --html reports/gradual_ramp_$(date +%Y%m%d_%H%M%S).html \
    --csv reports/gradual_ramp_$(date +%Y%m%d_%H%M%S)

echo ""
echo "================================================"
echo "Test Complete!"
echo "Check reports/ directory for results"
echo "================================================"