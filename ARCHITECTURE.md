
## Project Overview
Building a complete eCommerce system with:
- 4 Microservices (Product, Shopping Cart, Warehouse, Credit Card Provider)
- Distributed Database (Leader-Follower W=1, R=5)
- AWS Deployment (ECS, Auto-scaling, ALB)
- Load Testing (Locust)

## Project Structure

```
assignment5/
│
├── README.md
├── ARCHITECTURE.md
├── PLANNING_README.md
├── .gitignore
│
├── microservices/
│   ├── warehouse-service/              
│   │   ├── src/
│   │   ├── pom.xml
│   │   ├── Dockerfile
│   │   └── README.md
│   │
│   ├── product-service/                
│   │   ├── src/
│   │   ├── pom.xml
│   │   ├── Dockerfile
│   │   └── README.md
│   │
│   ├── shopping-cart-service/          
│   │   ├── src/
│   │   ├── pom.xml
│   │   ├── Dockerfile
│   │   └── README.md
│   │
│   └── credit-card-provider/           
│       ├── src/
│       ├── pom.xml
│       ├── Dockerfile
│       └── README.md
│
├── database/                          
│   ├── leader-follower-w1r5/          
│   ├── src/
│   ├── pom.xml
│   ├── Dockerfile
│   ├── data-loader/                   (load 1000 products)
│   └── README.md
│
├── terraform/                          
│   ├── main.tf
│   ├── variables.tf
│   ├── ecs.tf                         (autoscaling config)
│   ├── alb.tf
│   ├── networking.tf
│   └── README.md
│
├── locust-tests/                      
│   ├── locustfile.py
│   ├── add_to_cart_task.py
│   ├── checkout_task.py
│   ├── requirements.txt
│   └── README.md
│
├── documentation/                      
│   ├── assumptions.md                 
│   ├── database-choice.md             
│   ├── architecture-diagram.png      
│   ├── autoscaling-evidence/          
│   │   ├── graphs/
│   │   └── metrics/
│   └── final-report.pdf
│
```
---

# Database Architecture Decision

## Use Cases

### Use Case 1: Add Item to Cart
- **Reads:** Product info (price, name), Warehouse inventory check (VERY frequent)
- **Writes:** Create/update shopping cart (RARE)

### Use Case 2: Checkout
- **Reads:** Shopping cart contents, product details (FREQUENT)
- **Writes:** Update cart status, possibly inventory (VERY FREQUENT)

---

## Our Decision

**One database with 5 computers (1 Leader + 4 Followers)**

- **W=1:** Write to 1 computer, then respond to customer
- **R=1 or R=5:** Configurable read strategy per microservice
  - **R=1:** Read from single node (fast, eventual consistency) - Used by Product Service
  - **R=5:** Read from all 5 computers to get the newest data (strong consistency) - Used by Shopping Cart Service

---

## Why This Makes Sense for eCommerce

### W=1 (Write to 1 computer first)

**What it means:**
- When customer clicks "Add to Cart", we save it on the Leader computer
- Customer sees "Item Added!" right away (fast!)
- Then we quietly copy to the other 4 computers in the background

**Why this is good:**
- Customers don't wait
- Feels instant (50ms vs 250ms if we waited for all 5)

### R=5 (Read from all 5 computers) - Shopping Cart Service

**What it means:**
- When customer views their cart, we check all 5 computers
- We show them the newest version
- Used by Shopping Cart Service for strong consistency

**Why this is good:**
- Customer always sees the correct price
- Won't accidentally show old/wrong cart items
- Prevents showing "sold out" items as "in stock"
- Critical for checkout operations where accuracy matters more than speed

### R=1 (Read from single node) - Product Service

**What it means:**
- When customer browses products, we read from just one node (usually the Leader)
- Fast response time (~50ms vs ~200-300ms for R=5)
- Used by Product Service for read-heavy workloads

**Why this is good:**
- Much faster for product browsing (customers browse frequently)
- Products don't change often, so slight staleness is acceptable
- Better user experience with faster page loads
- Optimized for high-frequency read operations

---

## The Trade-off (CAP Theorem)

### What we gave up:
- For about 1 second, the 4 Follower computers might have slightly old data
- **Example:** Customer adds item to cart at 2:00:00 PM
    - Leader computer knows immediately (2:00:00 PM)
    - Follower 1 knows at 2:00:00.2 PM (0.2 seconds later)
    - Follower 2 knows at 2:00:00.4 PM (0.4 seconds later)
    - Follower 3 knows at 2:00:00.6 PM (0.6 seconds later)
    - Follower 4 knows at 2:00:00.8 PM (0.8 seconds later)
- After 0.8 seconds, ALL computers have the same data
- This brief delay is called "eventual consistency" - everyone catches up eventually

### What we kept:
- **Availability:** Website stays up even if 1-2 computers crash
- **Speed:** Customers get fast responses

### Why this is okay:
- 0.8 seconds is so fast, customers won't notice
- Much better than website being down

---

## Why Leader-Follower (Not Leaderless)?

### Simpler to understand:
- One boss (Leader) handles all writes
- Four workers (Followers) copy from the boss
- No confusion about which computer has the "real" data

### Prevents problems:
- Two customers can't accidentally modify the same cart at the same time
- We tested this in Assignment 4 and it worked perfectly

---

## Why NOT Other Database Options?

### W=5, R=1 (Write to all 5, Read from 1)
- **Why NOT:** Customers wait 1+ second every time they add to cart (too slow, frustrating experience).

### W=3, R=3 (Write to 3, Read from 3)
- **Why NOT:** Middle ground that's slower than W=1 for writes but less consistent than R=5 for reads (no clear advantage).

### Leaderless Database
- **Why NOT:** More complex to manage and two customers could accidentally create conflicting cart updates at the same time.

---

## What We Learned from Assignment 4

- W=1, R=5 was the best balance of speed and accuracy
- 800ms replication time is totally fine for our use case
- This setup handled 10,000 operations without breaking

---

## Configurable Read Strategies Per Microservice

### Implementation Overview

Each microservice can now configure its own read strategy based on its workload:

- **Product Service**: Uses **R=1** (fast, single node read)
  - Optimized for read-heavy product browsing
  - Median latency: ~550ms (includes service delay)
  - Database read: ~5-50ms
  
- **Shopping Cart Service**: Uses **R=5** (strong consistency, all nodes)
  - Optimized for write-heavy cart operations
  - Median latency: ~670ms (includes service delay)
  - Database read: ~200-300ms

### Database Layer Changes

1. **ReadCoordinator Service**: 
   - Added `readFromSingleNode(String key)` for R=1 strategy
   - Kept existing `readFromAllNodes(String key)` for R=5 strategy
   - Added `ReadStrategy` enum (R1, R5)

2. **LeaderController**:
   - Modified `/api/get` endpoint to accept `?readStrategy=R1` or `?readStrategy=R5`
   - Defaults to R=5 for backward compatibility

### Microservice Configuration

Each service configures its read strategy via `application.properties`:

**Product Service** (`microservices/product-service/src/main/resources/application.properties`):
```properties
database.readStrategy=R1
```

**Shopping Cart Service** (`microservices/shopping-cart-service/src/main/resources/application.properties`):
```properties
database.readStrategy=R5
```

### Performance Results

From load testing (Locust, 10 users, 60 seconds):

| Service | Strategy | Median Latency | Use Case |
|---------|----------|----------------|----------|
| Product Service | R=1 | 550ms | Product browsing (read-heavy) |
| Shopping Cart Service | R=5 | 670ms | Cart operations (consistency-critical) |

**Key Insight**: R=5 is ~22% slower than R=1, but provides strong consistency needed for cart operations. The trade-off is appropriate for each service's workload.

---