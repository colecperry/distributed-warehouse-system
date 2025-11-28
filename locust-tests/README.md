# Locust Load Testing for Assignment 5

Realistic load testing that simulates customer shopping behavior and drives auto-scaling.

## 📁 File Structure

```
locust-tests/
├── locustfile.py              # Main test configuration
├── requirements.txt           # Python dependencies
├── run_gradual_test.sh        # Gradual ramp-up test
├── run_spike_test.sh          # Sudden spike test
├── run_sustained_test.sh      # Long-duration test
├── reports/                   # Generated test reports
└── README.md                  # This file
```

## 🎯 Test Scenarios

### Use Case 1: Add Item to Cart (70% of actions)
**Realistic shopping behavior:**
1. Browse product (GET /products/{id})
2. Check warehouse inventory (POST /warehouse/reserve)
3. Add to cart (POST /cart or POST /cart/{id}/items)

**Expected Results:**
- 90% success (warehouse has stock)
- 10% out of stock

### Use Case 2: Checkout (30% of actions)
**Complete purchase flow:**
1. Customer adds credit card info
2. Authorize payment (POST /credit-card-authorizer/authorize)
3. Ship items (POST /warehouse/ship)
4. Complete checkout (POST /cart/{id}/checkout)

**Expected Results:**
- 70% of customers with carts attempt checkout
- 30% abandon cart (realistic behavior)
- 90% payment approval rate
- Final success rate: ~63% (90% payment × 70% attempt rate)

## 🚀 Quick Start

### 1. Install Dependencies

```bash
cd locust-tests

# Create virtual environment (recommended)
python3 -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate

# Install requirements
pip install -r requirements.txt
```

### 2. Get Your ALB URL

```bash
cd ../terraform
terraform output alb_dns_name
```

Example: `ecommerce-a5-alb-1234567890.us-east-1.elb.amazonaws.com`

### 3. Set Environment Variable

```bash
export ALB_URL="http://ecommerce-a5-alb-1234567890.us-east-1.elb.amazonaws.com"
```

### 4. Run Tests

```bash
# Make scripts executable
chmod +x *.sh

# Run gradual ramp-up test (recommended first)
./run_gradual_test.sh
```

## 📊 Test Types

### Test 1: Gradual Ramp-Up (Recommended First)

**Purpose:** Find breaking point by gradually increasing load

```bash
./run_gradual_test.sh
```

**Parameters:**
- Users: 150
- Spawn Rate: 5 users/second (gradual)
- Duration: 10 minutes

**What to Watch:**
- At ~70-80 users: First services scale to 2 instances
- At ~120-150 users: All services at 3 instances (max)
- Response times should stay under 2000ms

**CloudWatch Monitoring:**
```bash
# Watch ECS scaling in real-time
watch -n 5 'aws ecs describe-services \
  --cluster ecommerce-a5-cluster \
  --services ecommerce-a5-credit-card-service \
  --query "services[0].[serviceName,runningCount,desiredCount]"'
```

---

### Test 2: Spike Test

**Purpose:** Test auto-scaling response to sudden traffic

```bash
./run_spike_test.sh
```

**Parameters:**
- Users: 200
- Spawn Rate: 50 users/second (FAST!)
- Duration: 5 minutes

**What to Watch:**
- Initial spike: High response times (3000-5000ms)
- After 2-3 minutes: Response times improve as instances scale
- All services should reach 3 instances quickly

---

### Test 3: Sustained Load

**Purpose:** Verify system stability under prolonged load

```bash
./run_sustained_test.sh
```

**Parameters:**
- Users: 180
- Spawn Rate: 10 users/second
- Duration: 15 minutes (LONG)

**What to Watch:**
- Response times should stabilize
- No memory leaks or degradation
- CPU/Memory usage steady at ~80-90%

---

### Test 4: Interactive Web UI

**Purpose:** Real-time monitoring and manual control

```bash
# Start Locust web UI
locust -f locustfile.py --host "$ALB_URL"
```

Then open: http://localhost:8089

**Features:**
- Real-time charts
- Start/stop/adjust users on the fly
- Download reports
- View individual requests

---

## 📈 Expected Auto-Scaling Timeline

| Time | Users | Credit Card | Warehouse | Product | Cart | Notes |
|------|-------|-------------|-----------|---------|------|-------|
| 0:00 | 20 | 1 | 1 | 1 | 1 | Baseline |
| 2:00 | 50 | 1 | 1 | 1 | 1 | Warming up |
| 4:00 | 80 | 2 | 1 | 2 | 1 | CPU services scale first |
| 6:00 | 120 | 2 | 2 | 2 | 2 | All services scaling |
| 8:00 | 150 | 3 | 2 | 3 | 2 | Near max |
| 10:00 | 180 | 3 | 3 | 3 | 3 | **All maxed!** |

**Why different timing?**
- Credit Card & Product: CPU-based (70%) - scale faster
- Warehouse & Cart: Memory-based (75%) - scale slightly slower

---

## 📊 Analyzing Results

### HTML Reports

After each test, check `reports/` directory:

```
reports/
├── gradual_ramp_20241201_143022.html     # Visual charts
├── gradual_ramp_20241201_143022_stats.csv      # Request statistics
├── gradual_ramp_20241201_143022_failures.csv   # Failed requests
└── gradual_ramp_20241201_143022_exceptions.csv # Exceptions
```

### Key Metrics to Capture

**For Assignment Report:**

1. **Response Times:**
    - Average: Should be 500-1500ms
    - Median: Should be 400-1000ms
    - 95th percentile: Should be < 2000ms
    - 99th percentile: Should be < 3000ms

2. **Throughput:**
    - Requests/sec at peak load
    - Total successful requests

3. **Failure Rates:**
    - Expected: ~15% (10% payment decline + 10% out of stock - some overlap)
    - If > 20%: Something is wrong!

4. **Auto-Scaling Evidence:**
    - Screenshots of CloudWatch showing CPU/Memory
    - Screenshots of ECS console showing instance counts
    - Timestamps of scaling events

---

## 🔍 Monitoring During Tests

### CloudWatch Metrics

```bash
# CPU Utilization (Credit Card & Product)
aws cloudwatch get-metric-statistics \
    --namespace AWS/ECS \
    --metric-name CPUUtilization \
    --dimensions Name=ServiceName,Value=ecommerce-a5-credit-card-service \
    --start-time $(date -u -d '10 minutes ago' +%Y-%m-%dT%H:%M:%S) \
    --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
    --period 60 \
    --statistics Average

# Memory Utilization (Warehouse & Cart)
aws cloudwatch get-metric-statistics \
    --namespace AWS/ECS \
    --metric-name MemoryUtilization \
    --dimensions Name=ServiceName,Value=ecommerce-a5-warehouse-service \
    --start-time $(date -u -d '10 minutes ago' +%Y-%m-%dT%H:%M:%S) \
    --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
    --period 60 \
    --statistics Average
```

### ECS Service Status

```bash
# Check all services at once
aws ecs describe-services \
    --cluster ecommerce-a5-cluster \
    --services \
        ecommerce-a5-credit-card-service \
        ecommerce-a5-warehouse-service \
        ecommerce-a5-product-service \
        ecommerce-a5-cart-service \
    --query 'services[*].[serviceName,runningCount,desiredCount]' \
    --output table
```

### Real-Time Logs

```bash
# Credit Card Service logs
aws logs tail /ecs/credit-card-service --follow --since 5m

# All services
aws logs tail /ecs/credit-card-service --follow &
aws logs tail /ecs/warehouse-service --follow &
aws logs tail /ecs/product-service --follow &
aws logs tail /ecs/cart-service --follow &
```

---

## 📸 Screenshots to Capture

For your Assignment 5 report:

1. **Locust Web UI:**
    - Total requests chart
    - Response time chart (median + 95th percentile)
    - Number of users over time

2. **CloudWatch:**
    - CPU utilization for Credit Card & Product services
    - Memory utilization for Warehouse & Cart services
    - Side-by-side comparison of all 4 services at max load

3. **ECS Console:**
    - Service page showing 3 tasks running
    - Task details showing healthy status
    - Auto-scaling activity history

4. **ALB:**
    - Target health showing all targets healthy
    - Request count graph

---

## 🐛 Troubleshooting

### High Failure Rate (>25%)

**Problem:** Too many failed requests

**Solutions:**
```bash
# Check service health
aws ecs describe-services --cluster ecommerce-a5-cluster --services ecommerce-a5-credit-card-service

# Check ALB target health
aws elbv2 describe-target-health --target-group-arn <your-tg-arn>

# Check logs for errors
aws logs tail /ecs/credit-card-service --since 5m
```

### Services Not Scaling

**Problem:** Stuck at 1 instance despite high load

**Solutions:**
```bash
# Check auto-scaling configuration
aws application-autoscaling describe-scalable-targets \
    --service-namespace ecs \
    --resource-ids service/ecommerce-a5-cluster/ecommerce-a5-credit-card-service

# Check if metrics are being collected
aws cloudwatch get-metric-statistics \
    --namespace AWS/ECS \
    --metric-name CPUUtilization \
    --dimensions Name=ServiceName,Value=ecommerce-a5-credit-card-service \
    --start-time $(date -u -d '10 minutes ago' +%Y-%m-%dT%H:%M:%S) \
    --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
    --period 60 \
    --statistics Average
```

### Locust Can't Connect

**Problem:** Connection refused or timeout

**Solutions:**
```bash
# Verify ALB is running
aws elbv2 describe-load-balancers --names ecommerce-a5-alb

# Test ALB directly
curl -v http://your-alb-url.amazonaws.com/credit-card-authorizer/health

# Check security groups
aws ec2 describe-security-groups --group-ids <alb-sg-id>
```

---

## 📝 Test Strategy for Assignment

### Phase 1: Baseline (5 minutes)
```bash
# Start with low load to verify everything works
locust -f locustfile.py --host "$ALB_URL" --users 20 --spawn-rate 5 --run-time 5m --headless
```

**Success Criteria:**
- All services responding
- ~15% failure rate (expected)
- Response times < 1500ms

---

### Phase 2: Gradual Ramp (10 minutes)
```bash
./run_gradual_test.sh
```

**Success Criteria:**
- Services scale from 1→2→3 instances
- Response times stay under 2000ms
- No errors in CloudWatch logs

---

### Phase 3: Spike Test (5 minutes)
```bash
./run_spike_test.sh
```

**Success Criteria:**
- All services reach 3 instances within 2-3 minutes
- Initial spike in response times recovers
- System handles sudden load

---

### Phase 4: Sustained Load (15 minutes)
```bash
./run_sustained_test.sh
```

**Success Criteria:**
- All services stable at 3 instances
- Response times consistent
- No memory leaks or degradation

---

### Phase 5: Cool Down (5 minutes)
```bash
# Drop to low load to verify scale-in
locust -f locustfile.py --host "$ALB_URL" --users 30 --spawn-rate 5 --run-time 5m --headless
```

**Success Criteria:**
- Services scale back down to 1-2 instances
- System remains stable

---

## 💡 Tips for Success

1. **Run tests during AWS Learner Lab hours** - Labs have time limits
2. **Monitor CloudWatch actively** - Take screenshots in real-time
3. **Start with gradual test** - Verify everything works before spike
4. **Document timestamps** - Note when services scale
5. **Keep reports organized** - Name files with date/time
6. **Test locally first** - Use Docker Compose to verify endpoints

---

## 🎓 Assignment 5 Deliverables

From these tests, you'll need:

1. **Auto-scaling Evidence:**
    - CloudWatch metrics showing scaling
    - ECS console showing 3 instances per service
    - Timestamps of scaling events

2. **Performance Metrics:**
    - Response times (avg, median, 95th, 99th percentile)
    - Throughput (requests/second)
    - Failure rates

3. **System Behavior:**
    - How services scaled (which scaled first?)
    - When system hit max capacity
    - Bottleneck analysis

4. **Locust Reports:**
    - HTML reports with charts
    - CSV data for analysis

---

## 🚀 Ready to Test!

**Checklist:**
- [ ] Terraform infrastructure deployed
- [ ] All Docker images pushed to ECR
- [ ] All services healthy in ECS
- [ ] ALB URL obtained
- [ ] Locust installed (`pip install -r requirements.txt`)
- [ ] Environment variable set (`export ALB_URL=...`)
- [ ] CloudWatch dashboard open
- [ ] ECS console open
- [ ] Ready to take screenshots

**Run your first test:**
```bash
./run_gradual_test.sh
```
