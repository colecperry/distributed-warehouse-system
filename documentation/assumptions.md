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

## Database Choice: Why W=1, R=5 Leader-Follower?

### The Setup
- 1 Leader node (handles all writes)
- 4 Follower nodes (replicate data from Leader)
- W=1: Write to Leader, respond immediately, replicate in background
- R=5: Read from all 5 nodes, return newest version

### Why this works for us

**For Product Service (read-heavy):**
- R=5 spreads read load across all 5 nodes → handles high traffic
- W=1 makes rare product additions fast
- 200ms replication delay is fine - products don't change during a browsing session

**For Shopping Cart Service (write-heavy):**
- W=1 keeps cart operations fast (critical for user experience)
- R=5 ensures checkout always reads the latest cart state
- Carts are user-specific, so inconsistency between nodes doesn't matter

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
- **Lots of browsing** (handled well by R=5 reads across 5 nodes)
- **Lots of cart modifications** (handled well by W=1 fast writes)
- **Some checkouts** (R=5 ensures we read the latest cart data)

The W=1, R=5 strategy gives us fast writes when we need them (cart operations) and distributed reads when we need them (product browsing), with an acceptable 200ms inconsistency window that doesn't hurt the user experience.