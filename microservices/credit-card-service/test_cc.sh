#!/bin/bash

echo "======================================"
echo "Credit Card Service Test (10 requests)"
echo "======================================"

authorized=0
declined=0

for i in {1..10}; do
  echo -n "Request $i: "

  # Use Python for timing (works on macOS)
  start=$(python3 -c 'import time; print(int(time.time() * 1000))')

  response=$(curl -s -w "\n%{http_code}" -X POST http://localhost:8082/credit-card-authorizer/authorize \
    -H "Content-Type: application/json" \
    -d '{"credit_card_number": "1234-5678-9012-3456"}')

  end=$(python3 -c 'import time; print(int(time.time() * 1000))')

  http_code=$(echo "$response" | tail -n1)
  body=$(echo "$response" | head -n1)
  elapsed=$((end - start))

  if [ "$http_code" = "200" ]; then
    authorized=$((authorized + 1))
    echo "✓ Authorized (${elapsed}ms)"
  else
    declined=$((declined + 1))
    echo "✗ Declined (${elapsed}ms)"
  fi
done

echo ""
echo "======================================"
echo "Results:"
echo "  Authorized: $authorized/10 ($(($authorized * 10))%)"
echo "  Declined: $declined/10 ($(($declined * 10))%)"
echo "======================================"