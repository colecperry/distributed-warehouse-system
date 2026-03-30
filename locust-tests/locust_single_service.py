"""
Single-service load testing for Product or Shopping Cart services.

Usage:
  # Test Product Service (R=1)
  LOCUST_SERVICE=product locust -f locust_single_service.py --host=http://localhost:8083

  # Test Shopping Cart Service (R=5)
  LOCUST_SERVICE=cart locust -f locust_single_service.py --host=http://localhost:8084

  # With additional options
  LOCUST_SERVICE=product locust -f locust_single_service.py \
    --host=http://localhost:8083 \
    --headless \
    --users=10 \
    --spawn-rate=2 \
    --run-time=60s
"""

from locust import HttpUser, task, between, events
import random
import logging
import os

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class ProductReadsOnly(HttpUser):
    """
    Products-only load test.
    - Hits GET /products/{id} with ids 1..100
    - Use this with host=http://localhost:8083
    """
    wait_time = between(0.2, 0.8)

    @task
    def read_product(self):
        product_id = random.randint(1, 100)
        with self.client.get(
            f"/products/{product_id}",
            name="GET /products/[id]",
            catch_response=True,
        ) as response:
            if response.status_code != 200:
                response.failure(f"Product {product_id} not found")


class CartReadsOnly(HttpUser):
    """
    Carts-only load test.
    - Creates a cart once per user
    - Repeatedly hits GET /shopping-carts/{id}
    - Use this with host=http://localhost:8084
    """
    wait_time = between(0.2, 0.8)

    def on_start(self):
        # Create a cart for this user session
        customer_id = random.randint(1000, 9999)
        with self.client.post(
            "/shopping-cart",
            json={"customer_id": customer_id},
            name="POST /shopping-cart (create)",
            catch_response=True,
        ) as response:
            if response.status_code not in (200, 201):
                response.failure(f"Failed to create cart: {response.status_code}")
                self.cart_id = None
                return
            try:
                data = response.json()
                self.cart_id = data.get("shopping_cart_id")
            except Exception:
                response.failure("Failed to parse cart response")
                self.cart_id = None

    @task
    def read_cart(self):
        if not getattr(self, "cart_id", None):
            return
        with self.client.get(
            f"/shopping-carts/{self.cart_id}",
            name="GET /shopping-carts/[id]",
            catch_response=True,
        ) as response:
            if response.status_code != 200:
                response.failure(f"Cart {self.cart_id} not found")


# Determine which service to test based on environment variable
service_type = os.environ.get("LOCUST_SERVICE", "product").lower()

# Set the user class based on service type
if service_type == "cart":
    # Use CartReadsOnly for cart service testing
    class SingleServiceUser(CartReadsOnly):
        pass
    service_name = "Shopping Cart Service (R=5)"
else:
    # Default to ProductReadsOnly for product service testing
    class SingleServiceUser(ProductReadsOnly):
        pass
    service_name = "Product Service (R=1)"


@events.test_start.add_listener
def on_test_start(environment, **kwargs):
    logger.info("=" * 60)
    logger.info(f"Starting {service_name} load test")
    logger.info(f"Target host: {environment.host}")
    logger.info(f"Service type: {service_type} (set LOCUST_SERVICE=cart for cart testing)")
    logger.info("=" * 60)


@events.test_stop.add_listener
def on_test_stop(environment, **kwargs):
    logger.info("=" * 60)
    logger.info(f"{service_name} load test complete.")
    logger.info("=" * 60)
