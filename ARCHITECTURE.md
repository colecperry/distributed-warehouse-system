
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
│   ├── warehouse-service/              ← Member A
│   │   ├── src/
│   │   ├── pom.xml
│   │   ├── Dockerfile
│   │   └── README.md
│   │
│   ├── product-service/                ← Member C (reuse from A1/A3)
│   │   ├── src/
│   │   ├── pom.xml
│   │   ├── Dockerfile
│   │   └── README.md
│   │
│   ├── shopping-cart-service/          ← Member C (reuse from A1/A3)
│   │   ├── src/
│   │   ├── pom.xml
│   │   ├── Dockerfile
│   │   └── README.md
│   │
│   └── credit-card-provider/           ← Member D (reuse from A3)
│       ├── src/
│       ├── pom.xml
│       ├── Dockerfile
│       └── README.md
│
├── database/                           ← Member B
│   ├── leader-follower-w1r5/          (from Assignment 4)
│   ├── src/
│   ├── pom.xml
│   ├── Dockerfile
│   ├── data-loader/                   (load 1000 products)
│   └── README.md
│
├── terraform/                          ← Member D
│   ├── main.tf
│   ├── variables.tf
│   ├── ecs.tf                         (autoscaling config)
│   ├── alb.tf
│   ├── networking.tf
│   └── README.md
│
├── locust-tests/                       ← Member D + All
│   ├── locustfile.py
│   ├── add_to_cart_task.py
│   ├── checkout_task.py
│   ├── requirements.txt
│   └── README.md
│
├── documentation/                      ← All members contribute
│   ├── assumptions.md                 (Member C lead)
│   ├── database-choice.md             (Member B lead)
│   ├── architecture-diagram.png       (Member A lead)
│   ├── autoscaling-evidence/          (Member D lead)
│   │   ├── graphs/
│   │   └── metrics/
│   └── final-report.pdf
│
└── videos/                            ← All members
├── code-walkthrough.mp4
└── presentation.mp4
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
- **R=5:** Read from all 5 computers to get the newest data

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

### R=5 (Read from all 5 computers)

**What it means:**
- When customer views their cart, we check all 5 computers
- We show them the newest version

**Why this is good:**
- Customer always sees the correct price
- Won't accidentally show old/wrong cart items
- Prevents showing "sold out" items as "in stock"

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

## Future Scaling Plan

**Right now (1,000 products):**
- One database cluster is sufficient

**Later (if we grow to 10,000+ products):**
- Split into two clusters: one for Products, one for Shopping Carts
- Each optimized for its specific needs