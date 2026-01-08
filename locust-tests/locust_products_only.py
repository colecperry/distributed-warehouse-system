from locust import HttpUser, task, between, events
import random
import logging

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


@events.test_start.add_listener
def on_test_start(environment, **kwargs):
  logger.info("Starting Products-only load test (host=%s)", environment.host)

@events.test_stop.add_listener
def on_test_stop(environment, **kwargs):
  logger.info("Products-only load test complete.")


