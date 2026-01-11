# Terraform Infrastructure for Assignment 5

This Terraform configuration deploys the complete eCommerce microservices architecture to AWS ECS with auto-scaling.

## 📁 File Structure

```
terraform/
├── main.tf          # Main configuration, ECR, ECS cluster, IAM roles
├── variables.tf     # All configurable variables
├── networking.tf    # VPC, subnets, security groups, NAT gateway
├── alb.tf          # Application Load Balancer, target groups, routing
├── ecs.tf          # ECS services, task definitions, auto-scaling
├── ecs-services.tf # ECS service definitions
├── database.tf     # Database configuration
├── elasticache.tf  # ElastiCache Redis configuration
├── rabbitmq.tf     # RabbitMQ setup
└── README.md       # This file
```

## 🏗️ Architecture Overview

### Services Deployed:
1. **Credit Card Service** (Port 8082) - Memory auto-scaling @ 40%
2. **Warehouse Service** (Port 8081) - Memory-based auto-scaling @ 40%
3. **Product Service** (Port 8083) - Memory auto-scaling @ 40% (uses R=1 read strategy)
4. **Shopping Cart Service** (Port 8084) - Memory-based auto-scaling @ 40% (uses R=5 read strategy)
5. **Database Service** (Port 9080) - Leader node for distributed database

### Infrastructure Components:
- **VPC** with public/private subnets across 2 AZs (us-west-2)
- **Application Load Balancer** with path-based routing
- **ECS Fargate** cluster with auto-scaling
  - **Resource Allocation**: 1 vCPU / 2 GB per microservice
  - **Database**: 0.5 vCPU / 1 GB
  - **Max Capacity**: 10 instances per service
  - **Min Capacity**: 1 instance per service
- **ElastiCache Redis** for Product Service caching (cache.t3.micro, configurable for HA)
- **ECR** repositories for Docker images (5 services)
- **CloudWatch** for logging and monitoring
- **Security Groups** for network isolation
- **NAT Gateway** for outbound internet access
- **EC2 Instance** for RabbitMQ message broker

## 📋 Prerequisites

### 1. Install Terraform
```bash
# macOS
brew install terraform

# Verify installation
terraform --version
```

### 2. AWS Credentials

Configure AWS credentials for your AWS account:

```bash
# Configure AWS CLI
aws configure

# Or set environment variables
export AWS_ACCESS_KEY_ID="your-access-key"
export AWS_SECRET_ACCESS_KEY="your-secret-key"
export AWS_DEFAULT_REGION="us-west-2"
```

### 3. Build and Push Docker Images to ECR

**IMPORTANT:** Do this AFTER running `terraform apply` (which creates the ECR repositories)

**Option 1: Use automated script (recommended)**
```bash
cd terraform
./push-images.sh
```

This script builds and pushes all 5 services (including database) with correct platform (linux/amd64).

**Option 2: Manual push**
```bash
# 1. Get your AWS account ID
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

# 2. Login to ECR
aws ecr get-login-password --region us-west-2 | \
  docker login --username AWS --password-stdin ${AWS_ACCOUNT_ID}.dkr.ecr.us-west-2.amazonaws.com

# 3. Build and push each service with linux/amd64 platform
cd microservices/credit-card-service
docker build --platform linux/amd64 -t credit-card-service:latest .
docker tag credit-card-service:latest ${AWS_ACCOUNT_ID}.dkr.ecr.us-west-2.amazonaws.com/credit-card-service:latest
docker push ${AWS_ACCOUNT_ID}.dkr.ecr.us-west-2.amazonaws.com/credit-card-service:latest

# Repeat for: warehouse-service, product-service, shopping-cart-service

# Build and push database service
cd ../../database/leader-follower-w1r5
docker build --platform linux/amd64 -t database-service:latest .
docker tag database-service:latest ${AWS_ACCOUNT_ID}.dkr.ecr.us-west-2.amazonaws.com/database-service:latest
docker push ${AWS_ACCOUNT_ID}.dkr.ecr.us-west-2.amazonaws.com/database-service:latest
```

**Important:** All images must be built with `--platform linux/amd64` for ECS Fargate compatibility (especially on Apple Silicon).

## 🚀 Deployment Steps

### Step 1: Initialize Terraform
```bash
cd terraform
terraform init
```

This downloads required providers (AWS) and prepares Terraform.

### Step 2: Review the Plan
```bash
terraform plan
```

This shows what resources will be created (VPC, ECS, ALB, etc.).

### Step 3: Deploy Infrastructure
```bash
terraform apply
```

Type `yes` when prompted. This creates:
- ECR repositories (for Docker images)
- VPC and networking
- ECS cluster
- Application Load Balancer
- ElastiCache Redis (for Product Service caching)
- Auto-scaling configuration

**⏱️ Takes about 5-7 minutes to complete**

### Step 4: Get the ALB URL
```bash
terraform output alb_dns_name
```

Example output: `ecommerce-a5-alb-1234567890.us-west-2.elb.amazonaws.com`

### Step 5: Push Docker Images
Now push your Docker images to ECR (see section above).

### Step 6: Wait for Services to Start
```bash
# Check ECS services status
aws ecs list-services --cluster ecommerce-a5-cluster

# Check task status
aws ecs list-tasks --cluster ecommerce-a5-cluster --service-name ecommerce-a5-credit-card-service
```

Services take 2-3 minutes to start and pass health checks.

### Step 7: Test Services
```bash
# Get ALB URL
ALB_URL=$(terraform output -raw alb_dns_name)

# Test Credit Card Service
curl http://${ALB_URL}/credit-card-authorizer/health

# Test other services
curl http://${ALB_URL}/warehouse/health
curl http://${ALB_URL}/products/health
curl http://${ALB_URL}/cart/health
```

## 🔧 Configuration

### Auto-Scaling Settings (in variables.tf)

```hcl
# Resource allocation per service
ecs_task_cpu    = "1024"   # 1 vCPU (4x increase from 256/0.25 vCPU)
ecs_task_memory = "2048"   # 2 GB (4x increase from 512 MB)

# Maximum 10 instances per service (3.3x increase from 3)
max_capacity = 10
min_capacity = 1

# Memory targets (all services use memory-based scaling)
warehouse_memory_target = 40
cart_memory_target     = 40
credit_card_cpu_target = 40  # Variable name, but actually memory-based
product_cpu_target     = 40  # Variable name, but actually memory-based

# Cooldown periods
scale_in_cooldown  = 60  # Wait 60s before scaling in
scale_out_cooldown = 60  # Wait 60s before scaling out
```

**Scaling Strategy:**
- All services use **memory-based scaling** (40% threshold)
- Services scale from 1-10 instances based on memory utilization
- Uniform threshold ensures coordinated scaling across services

### Service Ports (in variables.tf)

```hcl
service_ports = {
  credit-card-service    = 8082
  warehouse-service      = 8081
  product-service        = 8083  # Uses R=1 read strategy
  shopping-cart-service  = 8084   # Uses R=5 read strategy
}

## 📊 Monitoring

### CloudWatch Logs
```bash
# View service logs (replace service-name)
aws logs tail /ecs/credit-card-service --follow --region us-west-2
aws logs tail /ecs/warehouse-service --follow --region us-west-2
aws logs tail /ecs/product-service --follow --region us-west-2
aws logs tail /ecs/shopping-cart-service --follow --region us-west-2
aws logs tail /ecs/database-service --follow --region us-west-2
```

### ECS Service Status
```bash
# Get cluster name
CLUSTER=$(terraform output -raw ecs_cluster_name)

# Check running tasks for a service
aws ecs describe-services \
  --cluster $CLUSTER \
  --services ecommerce-a5-credit-card-service \
  --region us-west-2 \
  --query 'services[0].[serviceName,runningCount,desiredCount]'

# Check all services
for service in credit-card product cart warehouse database-leader; do
  echo "${service}-service:"
  aws ecs describe-services \
    --cluster $CLUSTER \
    --services ecommerce-a5-${service}-service \
    --region us-west-2 \
    --query 'services[0].[runningCount,desiredCount]' \
    --output text | awk '{print "  Running: " $1 ", Desired: " $2}'
done
```

### Auto-Scaling Events
```bash
# Get cluster name
CLUSTER=$(terraform output -raw ecs_cluster_name)

# Check scaling activities for a service
aws application-autoscaling describe-scaling-activities \
  --service-namespace ecs \
  --resource-id service/${CLUSTER}/ecommerce-a5-credit-card-service \
  --region us-west-2 \
  --max-results 10
```

## 🧹 Cleanup (IMPORTANT!)

**Always stop or destroy resources after testing to avoid charges!**

### Option 1: Stop Services (Minimize Charges)

Scale all services to 0 to stop compute charges while keeping infrastructure:

```bash
cd terraform
./stop-services.sh
```

This stops all ECS tasks (no compute charges) but keeps:
- ALB (~$16/month)
- NAT Gateway (~$32/month) - biggest cost
- EC2 (RabbitMQ) (~$15/month)
- ECR storage (~$0.10/month)

**To restart services:**
```bash
cd terraform
./start-services.sh
```

### Option 2: Destroy All Resources (No Charges)

```bash
# Destroy all resources
cd terraform
terraform destroy

# Type 'yes' when prompted
```

This removes:
- ECS services and tasks
- Load balancer
- VPC and networking (including NAT Gateway)
- ECR repositories (and images)
- CloudWatch logs
- EC2 instance (RabbitMQ)

**Note:** You'll need to redeploy everything later if you destroy.

## 🔧 Service Management

### Start/Stop Services

**Stop all services (minimize charges):**
```bash
cd terraform
./stop-services.sh
```

**Start all services:**
```bash
cd terraform
./start-services.sh
```

### Force New Deployment

To force ECS to pull new images after updating Docker images:

```bash
CLUSTER=$(terraform output -raw ecs_cluster_name)

for service in credit-card product cart warehouse database-leader; do
  aws ecs update-service \
    --cluster $CLUSTER \
    --service ecommerce-a5-${service}-service \
    --force-new-deployment \
    --region us-west-2
done
```

## 📚 Useful Commands

```bash
# Show all outputs
terraform output

# Show specific output
terraform output alb_dns_name

# Refresh state
terraform refresh

# Show current resources
terraform state list

# Validate configuration
terraform validate

# Format code
terraform fmt
```

## 📊 Load Testing on AWS

After deploying to AWS, you can run load tests against the ALB:

```bash
# Get ALB URL
cd terraform
ALB_URL=$(terraform output -raw alb_dns_name)

# Preload products
cd ../locust-tests
export ALB_URL="http://${ALB_URL}"
python3 load_products.py

# Run load test
locust -f locustfile.py \
  --host=http://${ALB_URL} \
  --headless \
  --users=500 \
  --spawn-rate=20 \
  --run-time=10m \
  --html=load_test_report.html
```

See `SCALING_AND_PERFORMANCE.md` for performance analysis and scaling behavior.