from locust import HttpUser, task, between, events
import random
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


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


@events.test_start.add_listener
def on_test_start(environment, **kwargs):
  logger.info("Starting Carts-only load test (host=%s)", environment.host)

@events.test_stop.add_listener
def on_test_stop(environment, **kwargs):
  logger.info("Carts-only load test complete.")


