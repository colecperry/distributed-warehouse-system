"""
CS6650 Assignment 5 - eCommerce Load Testing
Simulates realistic customer shopping behavior with two use cases:
1. Add items to cart
2. Checkout (payment + shipping)
"""

from locust import HttpUser, task, between, events
from faker import Faker
import random
import logging
import requests

# Configure logging - Only show warnings and errors
logging.basicConfig(level=logging.WARNING)
logger = logging.getLogger(__name__)

# Initialize Faker for generating test data
fake = Faker()


class ECommerceCustomer(HttpUser):
  """
  Simulates a realistic eCommerce customer session.

  Customer Journey:
  1. Browse products (view product details)
  2. Add 2-5 items to cart (realistic shopping behavior)
  3. Sometimes checkout (70% complete purchase, 30% abandon cart)

  Assignment 5 Requirements:
  - Simulated business logic delays (100-1000ms per service)
  - 90% credit card approval, 10% decline
  - 90% warehouse availability, 10% out of stock
  """

  # Wait time between actions (realistic thinking time)
  wait_time = between(1, 3)  # 1-3 seconds between tasks

  def on_start(self):
    """
    Initialize customer session when user starts.
    Called once per simulated user.
    """
    self.customer_id = random.randint(1000, 9999)
    self.cart_id = None
    self.items_in_cart = []
    self.total_items_added = 0

    logger.info(f"New customer session started: {self.customer_id}")

  def on_stop(self):
    """
    Cleanup when user stops.
    Called once when simulated user ends.
    """
    logger.info(f"Customer {self.customer_id} ended session. "
                f"Items added: {self.total_items_added}, "
                f"Completed checkout: {self.cart_id is None}")

  @task(7)  # Weight: 7 (happens more frequently)
  def add_item_to_cart(self):
    """
    USE CASE 1: Customer Adds Item to Shopping Cart

    Steps (from Assignment 5):
    1. Customer selects a product (random from 1-100)
    2. Customer chooses quantity (log-normal: mostly 1-3, rarely 10+)
    3. System checks warehouse has sufficient quantity
    4. System adds item to cart

    Expected Results:
    - 90% success (warehouse has stock)
    - 10% failure (warehouse out of stock)
    """

    # Generate realistic quantity using log-normal distribution
    # Most customers buy 1-3 items, but occasionally someone buys 10+
    quantity = max(1, int(random.lognormvariate(0.5, 0.5)))
    quantity = min(quantity, 20)  # Cap at 20 for sanity

    # Random product from 100 products (pre-loaded in test setup)
    product_id = random.randint(1, 100)

    # STEP 1: Get product details
    with self.client.get(
        f"/products/{product_id}",
        catch_response=True,
        name="GET /products/[id]"
    ) as response:
      if response.status_code != 200:
        response.failure(f"Product {product_id} not found")
        return

      try:
        product_data = response.json()
        product_name = product_data.get("sku", f"Product {product_id}")
      except:
        product_name = f"Product {product_id}"

    # STEP 2: Check warehouse inventory (reserve)
    with self.client.post(
        "/reserve",
        json={
          "product_id": product_id,
          "quantity": quantity
        },
        catch_response=True,
        name="POST /reserve"
    ) as response:
      if response.status_code == 200:
        # Warehouse has inventory - proceed to add to cart
        try:
          result = response.json()
          available = result.get("available", "no")
          if available == "no":
            response.success()  # Don't count as failure
            logger.info(f"Out of stock: {product_name}")
            return
          logger.debug(f"✓ Warehouse reserved {quantity}x {product_name}")
        except:
          logger.debug(f"✓ Warehouse check passed for {product_name}")
      else:
        response.failure(f"Warehouse check failed: {response.status_code}")
        return

    # STEP 3: Add to cart
    if not self.cart_id:
      # Create new cart
      cart_data = {
        "customer_id": self.customer_id
      }

      with self.client.post(
          "/shopping-cart",
          json=cart_data,
          catch_response=True,
          name="POST /shopping-cart (create)"
      ) as response:
        if response.status_code == 201:
          try:
            result = response.json()
            self.cart_id = result.get("shopping_cart_id")
            self.total_items_added += 1
            logger.info(f"Created cart {self.cart_id}, "
                        f"adding {quantity}x {product_name}")
          except:
            response.failure("Failed to parse cart response")
        else:
          response.failure(f"Failed to create cart: {response.status_code}")
          return

    # Add to existing cart
    with self.client.post(
        f"/shopping-carts/{self.cart_id}/addItem",
        json={
          "productId": product_id,
          "quantity": quantity
        },
        catch_response=True,
        name="POST /shopping-carts/[id]/addItem"
    ) as response:
      if response.status_code == 204:
        self.items_in_cart.append({
          "product_id": product_id,
          "quantity": quantity,
          "name": product_name
        })
        self.total_items_added += 1
        logger.info(f"Added {quantity}x {product_name} to cart {self.cart_id}")
      else:
        response.failure(f"Failed to add to cart: {response.status_code}")

  @task(3)  # Weight: 3 (happens less frequently)
  def checkout(self):
    """
    USE CASE 2: Customer Checks-Out Shopping Cart

    Prerequisites:
    - Customer has items in cart

    Steps (from Assignment 5):
    1. Customer adds credit card info
    2. Customer clicks "Checkout"
    3. Credit Card Company authorizes charge (90% success)
    4. Warehouse prepares shipment

    Realistic Behavior:
    - 70% of customers with carts will checkout
    - 30% abandon cart (realistic eCommerce behavior)

    Expected Results:
    - ~63% success (90% payment × 70% attempt)
    - ~7% payment declined (10% of 70% who try)
    - ~30% cart abandonment
    """

    # Only checkout if we have items in cart
    if not self.cart_id or len(self.items_in_cart) == 0:
      return

    # Realistic cart abandonment (30%)
    if random.random() < 0.3:
      logger.info(f"Customer {self.customer_id} abandoned cart {self.cart_id} "
                  f"with {len(self.items_in_cart)} items")
      # Reset for new session
      self.cart_id = None
      self.items_in_cart = []
      return

    # Calculate total (simplified: $10 per item)
    total_amount = sum(item['quantity'] * 10.0 for item in self.items_in_cart)

    # STEP 1: Authorize credit card
    credit_card_data = {
      "credit_card_number": "1234-5678-9012-3456"  # Test card format
    }

    with self.client.post(
        "/credit-card-authorizer/authorize",
        json=credit_card_data,
        catch_response=True,
        name="POST /credit-card-authorizer/authorize"
    ) as response:
      if response.status_code == 200:
        # Payment approved
        logger.info(f"Payment approved: ${total_amount:.2f}")
      elif response.status_code == 402:
        # Payment declined (expected ~10% of time)
        response.success()  # Don't count as failure
        logger.info(f"Payment declined for cart {self.cart_id} "
                    f"(${total_amount:.2f})")
        # Keep cart for retry (realistic behavior)
        return
      else:
        response.failure(f"Payment processing failed: {response.status_code}")
        return

    # STEP 2: Ship items from warehouse
    for item in self.items_in_cart:
      with self.client.post(
          "/ship",
          json={
            "product_id": item["product_id"],
            "quantity": item["quantity"]
          },
          catch_response=True,
          name="POST /ship"
      ) as response:
        if response.status_code == 200:
          logger.debug(f"Shipped {item['quantity']}x {item['name']}")
        else:
          response.failure(f"Shipping failed: {response.status_code}")
          return

    # STEP 3: Complete checkout (update cart status)
    with self.client.post(
        f"/shopping-carts/{self.cart_id}/checkout",
        json={"credit_card_number": "1234-5678-9012-3456"},
        catch_response=True,
        name="POST /shopping-carts/[id]/checkout"
    ) as response:
      if response.status_code == 200:
        logger.info(f"Checkout complete! Cart {self.cart_id}, "
                    f"{len(self.items_in_cart)} items, "
                    f"${total_amount:.2f}")
      else:
        response.failure(f"Checkout completion failed: {response.status_code}")

    # Reset for new shopping session
    self.cart_id = None
    self.items_in_cart = []


# ==========================================
# Locust Event Handlers (for monitoring)
# ==========================================

@events.request.add_listener
def on_request(request_type, name, response_time, response_length, exception, **kwargs):
  """
  Log slow requests for debugging.
  """
  if response_time > 2000:  # > 2 seconds
    logger.warning(f"SLOW REQUEST: {name} took {response_time:.0f}ms")


@events.test_start.add_listener
def on_test_start(environment, **kwargs):
  """
  Called when load test starts.
  NOTE: Products must be pre-loaded using load_products.py before running this test!
  """
  logger.info("=" * 60)
  logger.info("STARTING LOAD TEST...")
  logger.info(f"Target: {environment.host}")
  logger.warning("⚠ IMPORTANT: Ensure load_products.py was run first!")
  logger.info("=" * 60)


@events.test_stop.add_listener
def on_test_stop(environment, **kwargs):
  """
  Called when load test stops.
  Display summary statistics.
  """
  stats = environment.stats

  logger.info("=" * 60)
  logger.info("Load test completed!")
  logger.info(f"Total requests: {stats.total.num_requests}")
  logger.info(f"Failed requests: {stats.total.num_failures}")
  logger.info(f"Requests/sec: {stats.total.total_rps:.2f}")
  logger.info(f"Average response time: {stats.total.avg_response_time:.0f}ms")
  logger.info(f"Median response time: {stats.total.median_response_time:.0f}ms")
  logger.info("=" * 60)