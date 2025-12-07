
# Assignment 5

## Team Communication

This is a basic skeleton design - please give ideas and change as needed!
**Remember: Reach out early if you're stuck! Turn off AWS resources daily!**

## Team Responsibilities

### Member A: Warehouse Service + Use Case Orchestration
**Primary Focus**: New microservice and workflow implementation
- Build Warehouse microservice with `reserve()` and `ship()` endpoints
- Implement 90/10 random logic (accept/decline)
- Add simulated delays (100-1000ms or log-normal)
- Implement end-to-end use case workflows:
  - Add item to cart flow
  - Checkout flow with transaction calls
- Add `begin_transaction`, `end_transaction`, `abort_transaction` to workflows
- Create system architecture diagram (all services + message flows)
- **Folders**: `microservices/warehouse-service/`, `documentation/architecture-diagram.png`

### Member B: Database Infrastructure + Integration
**Primary Focus**: Database setup and data management
- Set up distributed database from Assignment 4 (recommend W=1, R=5 Leader-Follower)
- Add 3 transaction endpoints to database (just print messages):
  - `begin_transaction`
  - `end_transaction`  
  - `abort_transaction`
- Build data loader to populate 1,000 products into database
- Decide database allocation strategy (separate clusters vs combined)
- Write database choice justification with CAP theorem analysis
- Record code walk-through video explaining database read/write implementation
- **Folders**: `database/`, `documentation/database-choice.md`, `videos/code-walkthrough.mp4`

### Member C: Product & Shopping Cart Services
**Primary Focus**: Core eCommerce microservices
- Update Product service from A1/A3 with simulated delays
- Update Shopping Cart service from A1/A3 with simulated delays
- Implement Shopping Cart logic:
  - Create cart if none exists
  - Add items to existing cart
- Integrate both services with database (Member B's database)
- Handle all error checking (bounds checking, invalid values, business logic)
- Write assumptions document:
  - Relative frequency of each use case
  - Read-write ratios for each use case
- **Folders**: `microservices/product-service/`, `microservices/shopping-cart-service/`, `documentation/assumptions.md`

### Member D: Credit Card Provider + AWS Deployment
**Primary Focus**: Infrastructure and load testing
- Update Credit Card Provider from A3 with simulated delays
- Verify 90/10 approve/decline logic
- Configure Terraform for AWS deployment:
  - ECS services for all 4 microservices
  - Auto-scaling (max 3 instances per service)
  - Use different metrics (CPU for some, memory for others)
  - ALB configuration
- Write Locust Python scripts:
  - Add to cart task (customer session)
  - Checkout task (complete purchase)
  - Simulate realistic customer behavior
- Run load tests and collect evidence:
  - Drive system to overload
  - Show all services scale equally
  - Document bottlenecks
- Write auto-scaling evidence document
- **Folders**: `microservices/credit-card-provider/`, `terraform/`, `locust-tests/`, `documentation/autoscaling-evidence/`

## Shared Responsibilities (All Members)

### Code Quality (Everyone)
- Review each other's code for quality standards
- Ensure proper error handling throughout
- Write unit tests for your components
- **Folder**: Each service has its own tests

### Integration Testing (Everyone)
- Test complete workflows end-to-end
- Debug integration issues between services
- Verify database connectivity from all services

### Final Deliverables (Everyone)
- Compile final report (PDF)
- Prepare 10-minute presentation
- Everyone appears in presentation video
- Review and proofread all documentation
- **Folders**: `documentation/final-report.pdf`, `videos/presentation.mp4`



## Schedule
### Phase 1: Foundation (Nov 28th Friday)
- **Member B**: Set up database, create data loader
- **Member C**: Update Product/Shopping Cart services
- **Member D**: Update Credit Card Service
- **Member A**: Start Warehouse service

### Phase 2: Integration (Nov 30th Sunday)
- **Member A**: Complete Warehouse, local unit test
- **Member B**: Add transaction endpoints, integrate with services
- **Member C**: Integrate services with database, test workflows
- **Member D**: Configure Terraform, prepare AWS infrastructure

### Phase 3: Deployment & Testing (Dec 5th Friday)
- **Member D**: Deploy all services to AWS
- **Member D + All**: Write Locust scripts together
- **All**: Run load tests, collect metrics
- **Member B**: Record code walk-through video

### Phase 4: Documentation & Presentation (Dec 7th Sunday)
- **Member A**: Finalize architecture diagram
- **Member B**: Finalize database choice document
- **Member C**: Finalize assumptions document
- **Member D**: Finalize auto-scaling evidence
- **All**: Compile final report, prepare presentation

## Dependencies
1. Member B must complete database setup before Members A & C can integrate
2. Member D needs all services containerized before deploying to AWS
3. Member A needs Members C & D's services to test complete use cases
4. Load testing requires all services deployed (Member D + All)


---

# CHANGES to assignment 5:

## CHANGE 1: Warehouse Service - NOT New, REUSE from Assignment 3
### OLD VERSION:
Build NEW Warehouse service
Add reserve() endpoint (90% yes, 10% no)
Add ship() endpoint (always succeeds)

### NEW VERSION:
REUSE Warehouse service from Assignment 3
Warehouse reached via RabbitMQ queue (not REST API)
Warehouse has NO date. ONLY ships (always succeeds)
NO reserve() endpoint!

---

## CHANGE 2: Use Case 1 - Simplified
### OLD VERSION:
Customer → Product → Warehouse.reserve() → Shopping Cart

### NEW VERSION:
Customer → Product → Shopping Cart
(No warehouse check when adding to cart!)
Warehouse only used during checkout (shipping), not when adding to cart


### UPDATED USE CASES
#### Use Case 1: Add Item to Cart (SIMPLIFIED)
Steps:

* Customer selects product
* Customer chooses quantity
* System adds to shopping cart
* NO warehouse check!

#### Errors:

Create cart if doesn't exist
Bounds checking on quantity/product ID


### Use Case 2: Checkout (SAME)
#### Steps:

* Customer adds credit card info
* System begins transaction
* Credit Card authorizes (90%) or declines (10%)
* If approved: Send ship message to Warehouse via RabbitMQ
* System ends transaction
* If declined: System aborts transaction

---

## CHANGE 3: RabbitMQ Integration
### NEW VERSION says:
Warehouse service reached by RabbitMQ queue from Assignment 3
This means message-based communication, not REST calls