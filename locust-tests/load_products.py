"""
Pre-load 100 products into the system before running load tests.
Run this ONCE before starting Locust.

Usage:
    python load_products.py
"""
import requests
import sys
import time

ALB_URL = "http://ecommerce-a5-alb-1658988954.us-east-1.elb.amazonaws.com"

def load_products():
  """Load 100 products via the Product service."""
  print("=" * 60)
  print("LOADING 100 PRODUCTS INTO PRODUCT SERVICE")
  print("=" * 60)

  successful = 0
  failed = 0

  for i in range(1, 101):
    product_data = {
      "product_id": i,
      "sku": f"PRODUCT-{i:03d}",
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
        if i % 25 == 0:
          print(f"✓ {i}/100 products loaded ({successful} successful)")
      else:
        failed += 1
        print(f"⚠ Product {i} failed: HTTP {response.status_code}")
        if i <= 5:  # Show first few errors for debugging
          print(f"  Response: {response.text[:200]}")

    except Exception as e:
      failed += 1
      print(f"❌ Product {i} error: {e}")

  print("\n" + "=" * 60)
  print(f"PRODUCT LOADING COMPLETE!")
  print(f"✓ Successful: {successful}")
  print(f"✗ Failed: {failed}")
  print("=" * 60)

  if successful == 0:
    print("\n❌ ERROR: No products loaded successfully!")
    print("Check that your services are running and ALB is accessible.")
    sys.exit(1)

  # Wait for database replication
  print("\nWaiting 5 seconds for database replication...")
  time.sleep(5)

  # Verify a sample of products
  print("\nVerifying product creation...")
  test_ids = [1, 25, 50, 75, 100]
  verified = 0

  for test_id in test_ids:
    try:
      response = requests.get(
        f"{ALB_URL}/products/{test_id}",
        timeout=10
      )
      if response.status_code == 200:
        verified += 1
        print(f"✓ Product {test_id} exists")
      else:
        print(f"⚠ Product {test_id} not found (HTTP {response.status_code})")
    except Exception as e:
      print(f"❌ Product {test_id} verification error: {e}")

  print("\n" + "=" * 60)
  if verified == len(test_ids):
    print("✅ ALL PRODUCTS VERIFIED - READY FOR LOAD TESTING!")
  else:
    print(f"⚠ WARNING: Only {verified}/{len(test_ids)} products verified")
    print("You may experience failures during load testing.")
  print("=" * 60)

  return successful > 0

if __name__ == "__main__":
  success = load_products()
  sys.exit(0 if success else 1)