# Assumptions Document - Product & Shopping Cart Services

## Use Case Frequency

### How often does each operation happen?

**Adding items to cart: Very Common (~85% of traffic)**

People browse a lot before buying. A typical customer might:
- Look at 20 products
- Add 5 items to their cart
- Maybe checkout (or maybe abandon the cart)

**Checkout: Less Common (~15% of traffic)**

Most carts get abandoned. Industry average is 60-70% abandonment rate. Checkout requires payment info and commitment,
so it happens way less often than browsing.

**Our estimate: 10-15 add-to-cart operations for every 1 checkout**

---

## Read vs Write Patterns

### Product Service: Heavy on Reads (10:1 ratio)

**Why mostly reads?**
- Thousands of customers browse products simultaneously
- New products get added rarely (maybe batch uploads weekly)
- Once a product is created, it doesn't change much
- Same product can be viewed by 100 different customers at once

**Example:**
- 1,000 customers browsing → 10,000 GET requests
- Store adds new inventory → 100 POST requests
- **Result: 10,000 reads vs 100 writes**

### Shopping Cart Service: Heavy on Writes (1:5 ratio)

**Why mostly writes?**
- Creating a cart: write
- Adding each item: write (we read the cart, modify it, write it back)
- Checkout: read
- People rarely just "view" their cart without changing it

**Example customer session:**
- Creates cart: 1 write
- Adds 5 items: 5 writes
- Checks out: 1 read
- **Result: 1 read vs 6 writes**

---

## Database Choice: Why W=1, R=1 or R=5 (Configurable) Leader-Follower?

### The Setup
- 1 Leader node (handles all writes)
- 4 Follower nodes (replicate data from Leader)
- W=1: Write to Leader, respond immediately, replicate in background
- R=1 or R=5: Configurable read strategy per microservice
  - **R=1**: Product Service uses single-node reads (fast, ~550ms median)
  - **R=5**: Shopping Cart Service uses all-node reads (strong consistency, ~670ms median)

### Why this works for us

**For Product Service (read-heavy):**
- **R=1** provides fast reads from single node → optimized for high-frequency browsing
- W=1 makes rare product additions fast
- Slight staleness acceptable - products don't change during a browsing session
- Much faster than R=5 (~550ms vs ~670ms median)

**For Shopping Cart Service (write-heavy):**
- W=1 keeps cart operations fast (critical for user experience)
- **R=5** ensures checkout always reads the latest cart state from all nodes
- Strong consistency critical for cart operations where accuracy > speed
- Prevents showing stale cart contents or incorrect totals

### CAP Theorem Trade-offs

**We chose: Availability + Partition Tolerance**  
**We gave up: Strong Consistency**

**What this means:**
- System stays fast and responsive even during network issues ✓
- There's a ~200ms window where different nodes might have slightly different data
- This is totally fine for our use case:
    - Products: Don't change mid-session
    - Carts: User-specific, not shared
    - Checkout: R=5 ensures we read the latest version

**In plain English:** We'd rather have a fast, always-available system with a tiny delay in data syncing, rather than a slow system that waits for perfect consistency.

---

## Key Assumptions

### Traffic Patterns
- **Browse : Add : Checkout** = roughly **20 : 10 : 1**
- Peak load: ~1,000 concurrent shoppers
- Most carts (60-70%) get abandoned without checkout

### Data Patterns
- Starting with 1,000 products in the catalog
- Average cart has 3-5 items
- Carts only last for one session (no saving for later)

### Performance
- Each request has a random 100-1000ms delay (to trigger autoscaling in testing)
- Database replication takes ~200ms
- This delay is acceptable for our business logic

### Simplifications (for this assignment)
- No user authentication (we trust the customer_id from the client)
- Products use simple auto-incrementing IDs
- Quantity limits: 1-10,000 items per product

---

## Bottom Line

Our system is optimized for what actually happens in eCommerce:
- **Lots of browsing** (handled well by **R=1** fast single-node reads for Product Service)
- **Lots of cart modifications** (handled well by W=1 fast writes)
- **Some checkouts** (**R=5** ensures we read the latest cart data from all nodes)

The W=1, configurable R=1/R=5 strategy gives us:
- **Fast writes** when we need them (cart operations) - W=1
- **Fast reads** for product browsing - R=1 (~550ms median)
- **Strong consistency** for cart operations - R=5 (~670ms median)
- Each microservice optimized for its workload (speed vs consistency trade-off)