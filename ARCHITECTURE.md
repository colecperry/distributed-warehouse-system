
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