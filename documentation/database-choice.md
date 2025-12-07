# Database Architecture Decision

## Executive Summary

**Choice:** Single Leader-Follower Cluster (W=1, R=5, N=5)

We chose a single database cluster with 5 nodes using Leader-Follower architecture where writes go to 1 node (W=1) and reads query all 5 nodes (R=5). This provides fast writes for customer actions while ensuring strong read consistency for accurate product and cart information.

---

## Use Cases Analysis

### Use Case 1: Add Item to Cart
- **Reads:** Product info (price, name, stock) from Product database (VERY frequent)
- **Writes:** Create/update shopping cart (FREQUENT)
- **Note:** Inventory availability is checked via Product database stock field, not Warehouse service

### Use Case 2: Checkout
- **Reads:** Shopping cart contents, product details (FREQUENT)
- **Writes:** Update cart status (FREQUENT)
- **External Services:** Credit card authorization via Credit Card service (90% approve, 10% decline)
- **External Services:** Warehouse ship request via RabbitMQ (always succeeds, has no data storage)

### Read/Write Patterns

**Products:**
- **Ratio:** 100:1 (100 customers browsing for every 1 admin adding products)
- **Frequency:** Reads happen constantly (browse, add to cart, checkout), writes are rare
- **Stock checks:** Done by reading Product data, not calling Warehouse service

**Shopping Carts:**
- **Ratio:** 2:1 (2 reads for every 1 write - view cart twice, then add/modify once)
- **Frequency:** Both reads and writes are frequent during shopping sessions

---

## Database Architecture Decision

### Configuration
- **Type:** Leader-Follower
- **Nodes:** 5 (1 Leader + 4 Followers)
- **Write Strategy:** W=1 (write to Leader only)
- **Read Strategy:** R=5 (read from all 5 nodes)
- **Scope:** Single cluster for both Products and Shopping Carts

### Data Storage
- **Products:** Stored as `"product:ID" → {JSON with stock field}`
- **Shopping Carts:** Stored as `"cart:ID" → {JSON}`
- **Capacity:** Sufficient for 1,000-5,000 products and active carts
- **Important:** Warehouse and Credit Card services have NO database storage (per assignment requirements)

---

## Why W=1, R=5?

### W=1 (Write to 1 node first)

**How it works:**
1. Customer clicks "Add to Cart"
2. Leader saves cart data immediately
3. Customer sees "Item Added!" in 50ms
4. Leader replicates to 4 Followers in background (200ms each)

**Why this is good:**
- Fast customer experience (50ms vs 250ms for W=5)
- No waiting for replication
- Customer actions feel instant
- Critical for conversions - slow add to cart = lost sales

### R=5 (Read from all 5 nodes)

**How it works:**
1. Customer views cart, product, or checks stock availability
2. System queries all 5 nodes in parallel
3. Returns the newest version (highest version number)

**Why this is good:**
- Always shows accurate prices
- Always shows current cart contents
- Always shows correct stock levels (prevents overselling)
- Eliminates stale reads after 800ms maximum

---

## Why Leader-Follower (Not Leaderless)?

### Advantages of Leader-Follower

**Simpler consistency model:**
- One Leader handles all writes
- Four Followers replicate from Leader
- Clear authority prevents write conflicts

**Prevents race conditions:**
- Two customers can't modify the same cart simultaneously
- Shopping cart updates are serialized through Leader
- No conflict resolution needed

**Easier to debug:**
- Single source of truth (Leader)
- Clear replication flow
- Proven in Assignment 4 testing

### Why NOT Leaderless?

- **Complexity:** Requires conflict resolution (vector clocks, CRDTs)
- **Overhead:** More complex for our simple use case
- **Overkill:** We don't need multi-datacenter writes

---

## CAP Theorem Trade-offs

### What is CAP?
- **C**onsistency: All nodes see the same data at the same time
- **A**vailability: System responds to requests even if nodes fail
- **P**artition tolerance: System works even if network splits

**CAP Theorem:** You can only guarantee 2 out of 3

### Our Choice: AP (Availability + Partition Tolerance)

**What we prioritized:**
-  **Availability:** Website stays up even if 1-2 nodes fail
-  **Partition Tolerance:** System works during network issues
-  **Speed:** Fast responses for customers

**What we deprioritized:**
-  **Strong Consistency:** Accept brief stale data (up to 800ms)

### The Trade-off Explained

**What we gave up:**
- For about 0.8 seconds, Follower nodes might have slightly old data
- **Example:** Customer adds item to cart at 2:00:00 PM
  - Leader knows immediately (2:00:00 PM)
  - Follower 1 knows at 2:00:00.2 PM
  - Follower 2 knows at 2:00:00.4 PM
  - Follower 3 knows at 2:00:00.6 PM
  - Follower 4 knows at 2:00:00.8 PM
- After 0.8 seconds, ALL nodes have the same data

**Why this is acceptable:**
- 0.8 seconds is too fast for customers to notice
- Much better than website being down
- eCommerce prioritizes availability over brief staleness
- Our R=5 reads always return the newest version anyway

---

## Assignment 4 Learnings

### Performance Testing Results

From our Assignment 4 experiments, we learned:

**W=1 Write Performance:**
- Average latency: 50ms per write
- Throughput: 1,000+ writes/second
- Background replication doesn't block clients

**R=5 Read Performance:**
- Average latency: 120ms per read
- Always returns most recent version
- Parallel queries to 5 nodes complete quickly

**Comparison with Other Strategies:**

| Strategy | Write Latency | Read Latency | Best For |
|----------|--------------|--------------|----------|
| **W=1, R=5** | 50ms  | 120ms | Fast writes, consistent reads |
| W=5, R=1 | 250ms  | 50ms | Critical consistency, slow writes OK |
| W=3, R=3 | 150ms | 100ms | Balanced (no clear advantage) |

### Key Insights

1. **W=1 provides best write performance** - Critical for customer experience
2. **R=5 eliminates stale reads after 800ms** - Acceptable delay for our use case
3. **Leader-Follower handled 10,000 operations** - No failures or data loss
4. **Replication delays are predictable** - 200ms per node, total 800ms

---

## Why NOT Other Database Options?

### W=5, R=1 (Write to all 5, Read from 1)
**Why NOT:** Customers wait 1+ second every time they add to cart (too slow, frustrating experience).

### W=3, R=3 (Write to 3, Read from 3)
**Why NOT:** Middle ground that's slower than W=1 for writes but less consistent than R=5 for reads (no clear advantage).

### Leaderless Database
**Why NOT:** More complex to manage and two customers could accidentally create conflicting cart updates at the same time.

---

## Assumptions

### System Scale
- **Products:** 1,000-5,000 items in database
- **Concurrent Users:** Up to 1,000 simultaneous shoppers
- **Peak Load:** Black Friday-level traffic with auto-scaling

### Usage Patterns
- **Browse/Search:** 70% of traffic (product reads from database)
- **Add to Cart:** 25% of traffic (cart writes + product reads for stock check)
- **Checkout:** 5% of traffic (cart reads/writes + external service calls)
- **Note:** Warehouse service only involved in checkout (ship via RabbitMQ), not during browse or add to cart

### Business Requirements
- **Availability > Consistency:** Website uptime is critical
- **Fast Response:** Customers expect sub-200ms actions
- **Accurate Data:** Must prevent overselling and wrong prices

---

## Future Scaling Plan

### Current Architecture (1,000 products)
-  Single cluster sufficient
-  All data fits in memory
-  5 nodes handle load easily

### Growth Plan (10,000+ products)
**Option 1: Vertical Scaling**
- Increase memory on existing nodes
- Add more CPU resources
- Simple, low-risk approach

**Option 2: Horizontal Partitioning**
- Split into two clusters:
  - **Cluster 1:** Products (W=1, R=5) - Read-optimized
  - **Cluster 2:** Shopping Carts (W=5, R=1) - Write-optimized
- Each cluster optimized for its workload

**Option 3: Geographic Distribution**
- Consider Leaderless for global deployment
- Multi-region replication for low latency worldwide
- Only if expanding internationally

---

## Conclusion

Our W=1, R=5 Leader-Follower architecture provides the optimal balance for an eCommerce system:

 **Fast writes** keep customers happy (50ms add to cart)  
 **Consistent reads** prevent business logic errors  
 **High availability** ensures website stays up  
 **Proven reliability** from Assignment 4 testing  
 **Simple to maintain** and debug

The brief 800ms eventual consistency window is acceptable for our use case and vastly outweighed by the benefits of speed and availability.

