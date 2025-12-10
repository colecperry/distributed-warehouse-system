# Database Architecture Decision

## Executive Summary

**Choice:** Single Leader-Follower Cluster (W=1, R=5, N=5)

We chose a single database cluster with 5 nodes using Leader-Follower architecture where writes go to 1 node (W=1) and reads query all 5 nodes (R=5). This provides fast writes for customer actions while ensuring strong read consistency for accurate product and cart information.

---

## Use Cases Analysis

### Use Case 1: Add Item to Cart
- **Reads:** Product info (price, name, stock) from Product database (frequent)
- **Writes:** Create/update shopping cart (frequent)
- **Note:** Inventory availability checked via Product database stock field

### Use Case 2: Checkout
- **Reads:** Shopping cart contents, product details (frequent)
- **Writes:** Update cart status (frequent)
- **External Services:** Credit card authorization (90% approve, 10% decline)
- **External Services:** Warehouse ship request via RabbitMQ (always succeeds)

### Database Access Patterns

**Products:**
- **Pattern:** Read-heavy (customers browse constantly, admins update rarely)
- **Operations:** Read product details, check stock (frequent), Admin adds products (rare)

**Shopping Carts:**
- **Pattern:** Mixed read/write (view cart, add/remove items, checkout)
- **Operations:** Create cart, update items, read contents (all frequent during sessions)

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
3. Customer sees response (5ms under write-heavy load per Assignment 4 testing)
4. Leader replicates to 4 Followers in background (200ms each)

**Why this is good:**
- Fast customer experience (5ms vs 618ms for W=3, 2484ms for W=5 from Assignment 4)
- No waiting for replication
- Customer actions feel instant
- Critical for conversions

### R=5 (Read from all 5 nodes)

**How it works:**
1. Customer views cart, product, or checks stock availability
2. System queries all 5 nodes in parallel
3. Returns the newest version (highest version number)

**Why this is good:**
- Always shows accurate prices
- Always shows current cart contents
- Always shows correct stock levels (prevents overselling)
- Zero stale reads (proven in Assignment 4 with 10,000 operations)

---

## Why Leader-Follower (Not Leaderless)?

### Advantages of Leader-Follower

**Simpler consistency model:**
- One Leader handles all writes
- Four Followers replicate from Leader
- Clear authority prevents write conflicts

**Prevents race conditions:**
- Two customers cannot modify the same cart simultaneously
- Shopping cart updates are serialized through Leader
- No conflict resolution needed

**Easier to debug:**
- Single source of truth (Leader)
- Clear replication flow
- Proven in Assignment 4 testing

### Why NOT Leaderless?

**From Assignment 4 testing:**
- W=N, R=1 Leaderless showed 1243ms writes at 90% write load (249x slower than W=1, R=5)
- 95% throughput degradation from read-heavy to write-heavy workloads
- Requires complex conflict resolution (vector clocks, CRDTs)
- Unnecessary for our scale (1,000 products)

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
- **Strong Consistency:** Accept brief stale data (up to 800ms)

### The Trade-off Explained

**What we gave up:**
- For about 0.8 seconds, Follower nodes might have slightly old data
- **Example:** Customer adds item to cart at 2:00:00 PM
    - Leader knows immediately (2:00:00 PM)
    - Follower 1 knows at 2:00:00.2 PM (200ms later)
    - Follower 2 knows at 2:00:00.4 PM (400ms later)
    - Follower 3 knows at 2:00:00.6 PM (600ms later)
    - Follower 4 knows at 2:00:00.8 PM (800ms later)
- After 0.8 seconds, ALL nodes have the same data

**Why this is acceptable:**
- 0.8 seconds is too fast for customers to notice
- Much better than website being down
- eCommerce prioritizes availability over brief staleness
- Our R=5 reads always return the newest version anyway (zero stale reads in Assignment 4)

---

## Assignment 4 Learnings

### Performance Testing Results

From our Assignment 4 experiments with W=1, R=5 across 10,000 operations:

**W=1 Write Performance by Workload:**

| Scenario | Write P50 | Write Avg | Throughput |
|----------|-----------|-----------|------------|
| 90% Write, 10% Read | **5ms** | 26ms | 106 req/s |
| 50% Write, 50% Read | 147ms | 135ms | 148 req/s |
| 10% Write, 90% Read | 268ms | 245ms | 326 req/s |
| 1% Write, 99% Read | 275ms | 273ms | 480 req/s |

**R=5 Read Performance by Workload:**

| Scenario | Read P50 | Read Avg |
|----------|----------|----------|
| 90% Write, 10% Read | **255ms** | 274ms |
| 50% Write, 50% Read | 412ms | 398ms |
| 10% Write, 90% Read | 532ms | 512ms |
| 1% Write, 99% Read | 542ms | 540ms |

**Critical Finding:**
- **Zero stale reads** across all 10,000 operations and all test scenarios
- R=5 always returns newest version by querying all nodes and comparing version numbers
- Even during 800ms replication window, R=5 finds latest data on Leader

### Comparison with Other Strategies (From Assignment 4)

**Write Performance at 90% Write Load:**

| Strategy | Write P50 | Performance vs W=1, R=5 |
|----------|-----------|-------------------------|
| **W=1, R=5** | **5ms** | Baseline (fastest) |
| W=3, R=3 | 618ms | 123x slower |
| W=5, R=1 | 2484ms | 497x slower |
| W=N, R=1 | 1243ms | 249x slower |

**Why This Matters:**
- eCommerce requires fast writes for cart operations
- W=1, R=5 delivered best write performance under write-heavy load
- All configurations achieved zero stale reads, so performance was the deciding factor

### Key Insights

1. **W=1 provides best write performance** - 5ms at 90% write load vs 618ms+ for alternatives
2. **R=5 eliminates stale reads** - Zero stale reads despite async replication
3. **Leader-Follower handled 10,000 operations** - No failures or data loss
4. **Replication delays are predictable** - 200ms per node, total 800ms
5. **Consistent performance** - W=1, R=5 maintained stable throughput across workloads

---

## Why NOT Other Database Options?

### W=5, R=1 (Write to all 5, Read from 1)
**Why NOT:** 2484ms writes at 90% write load (497x slower than W=1, R=5). Unacceptable for customer cart operations.

### W=3, R=3 (Write to 3, Read from 3)
**Why NOT:** 618ms writes vs 5ms (W=1, R=5) at high write loads. No advantage for our workload. Same throughput but much slower writes.

### W=N, R=1 Leaderless
**Why NOT:**
- 1243ms writes (249x slower than W=1, R=5)
- 95% throughput collapse from read-heavy to write-heavy workloads
- Excessive complexity (conflict resolution) unnecessary for our scale

---

## Assumptions

### System Scale
- **Products:** 1,000-5,000 items in database
- **Concurrent Users:** Up to 1,000 simultaneous shoppers
- **Peak Load:** Auto-scaling enabled for traffic spikes

### Usage Patterns
- **Browse/Search:** High frequency (product reads from database)
- **Add to Cart:** High frequency (cart writes + product reads)
- **Checkout:** Lower frequency (cart reads/writes + external service calls)
- **Note:** Warehouse service only involved in checkout (ship via RabbitMQ)

### Business Requirements
- **Availability > Consistency:** Website uptime is critical
- **Fast Response:** Customers expect instant actions
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

 **Fast writes** (5ms at 90% write load from Assignment 4)  
 **Zero stale reads** across 10,000 operations  
 **High availability** ensures website stays up  
 **Proven reliability** from Assignment 4 testing  
 **Simple to maintain** and debug

The brief 800ms eventual consistency window is acceptable for our use case and vastly outweighed by the benefits of speed and availability.

---

**Document prepared by:** Member B (Database Team)  
**Date:** November 27, 2025  
**Assignment:** CS6650 Assignment 5 - Everything, All At Once