"""
Pre-load 1000 products into the system before running load tests.
Run this ONCE before starting Locust.

Usage:
    python load_products.py
"""
import requests
import sys
import time

# Replace with your actual ALB URL
# Or set via environment variable: export ALB_URL=http://your-alb-url
import os
ALB_URL = os.getenv("ALB_URL", "http://ecommerce-a5-alb-580142330.us-east-1.elb.amazonaws.com")

def load_products():
  """Load 1000 products via the Product service."""
  print("=" * 60)
  print("LOADING 1000 PRODUCTS INTO PRODUCT SERVICE")
  print("=" * 60)

  successful = 0
  failed = 0

  for i in range(1, 1001):  # 1 to 1000 inclusive
    product_data = {
      "product_id": i,
      "sku": f"PRODUCT-{i:04d}",  # Use 4 digits for 1000 products
      "manufacturer": "TestCo",
      "category_id": 1,
      "weight": 100,
      "some_other_id": i
    }

    try:
      response = requests.post(
        f"{ALB_URL}/products",
        json=product_data,
        timeout=10
      )

      if response.status_code in [200, 201]:
        successful += 1
        if i % 100 == 0:  # Print every 100 products
          print(f"✓ {i}/1000 products loaded ({successful} successful)")
          print("  Pausing 2 seconds for database replication...")
          time.sleep(2)  # Let database replicate after every 100 products
      else:
        failed += 1
        print(f"⚠ Product {i} failed: HTTP {response.status_code}")
        if i <= 5:  # Show first few errors for debugging
          print(f"  Response: {response.text[:200]}")

    except Exception as e:
      failed += 1
      print(f"Product {i} error: {e}")

  print("\n" + "=" * 60)
  print(f"PRODUCT LOADING COMPLETE!")
  print(f"✓ Successful: {successful}")
  print(f"✗ Failed: {failed}")
  print("=" * 60)

  if successful == 0:
    print("\nERROR: No products loaded successfully!")
    print("Check that your services are running and ALB is accessible.")
    sys.exit(1)

  # Wait for database replication
  print("\nWaiting 10 seconds for database replication...")
  time.sleep(10)  # Longer wait for 1000 products

  # Verify a sample of products
  print("\nVerifying product creation...")
  test_ids = [1,250, 500, 750, 1000 ]  # Sample across range
  verified = 0

  for test_id in test_ids:
    try:
      response = requests.get(
        f"{ALB_URL}/products/{test_id}",
        timeout=10
      )
      if response.status_code == 200:
        verified += 1
        print(f"Product {test_id} exists")
      else:
        print(f"Product {test_id} not found (HTTP {response.status_code})")
    except Exception as e:
      print(f"Product {test_id} verification error: {e}")

  print("\n" + "=" * 60)
  if verified == len(test_ids):
    print("ALL PRODUCTS VERIFIED - READY FOR LOAD TESTING!")
  else:
    print(f"⚠ WARNING: Only {verified}/{len(test_ids)} products verified")
    print("You may experience failures during load testing.")
  print("=" * 60)

  return successful > 0

if __name__ == "__main__":
  success = load_products()
  sys.exit(0 if success else 1)