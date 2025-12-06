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
└── README.md       # This file
```

## 🏗️ Architecture Overview

### Services Deployed:
1. **Credit Card Service** (Port 8082) - CPU-based auto-scaling @ 70%
2. **Warehouse Service** (Port 8081) - Memory-based auto-scaling @ 75%
3. **Product Service** (Port 8080) - CPU-based auto-scaling @ 70%
4. **Shopping Cart Service** (Port 8083) - Memory-based auto-scaling @ 75%

### Infrastructure Components:
- **VPC** with public/private subnets across 2 AZs
- **Application Load Balancer** with path-based routing
- **ECS Fargate** cluster with auto-scaling (max 3 instances per service)
- **ECR** repositories for Docker images
- **CloudWatch** for logging and monitoring
- **Security Groups** for network isolation

## 📋 Prerequisites

### 1. Install Terraform
```bash
# macOS
brew install terraform

# Verify installation
terraform --version
```

### 2. AWS Credentials (AWS Learner Lab)
When you start the AWS Learner Lab, you'll get temporary credentials:

```bash
# Copy credentials from AWS Learner Lab
export AWS_ACCESS_KEY_ID="your-access-key"
export AWS_SECRET_ACCESS_KEY="your-secret-key"
export AWS_SESSION_TOKEN="your-session-token"
export AWS_DEFAULT_REGION="us-east-1"
```

Or create `~/.aws/credentials`:
```ini
[default]
aws_access_key_id = your-access-key
aws_secret_access_key = your-secret-key
aws_session_token = your-session-token
```

### 3. Build and Push Docker Images to ECR

**IMPORTANT:** Do this AFTER running `terraform apply` (which creates the ECR repositories)

```bash
# 1. Get your AWS account ID
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

# 2. Login to ECR
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin ${AWS_ACCOUNT_ID}.dkr.ecr.us-east-1.amazonaws.com

# 3. Tag and push Credit Card Service
cd ../microservices/credit-card-service
docker build -t credit-card-service:latest .
docker tag credit-card-service:latest \
  ${AWS_ACCOUNT_ID}.dkr.ecr.us-east-1.amazonaws.com/credit-card-service:latest
docker push ${AWS_ACCOUNT_ID}.dkr.ecr.us-east-1.amazonaws.com/credit-card-service:latest

# 4. Repeat for other services (warehouse, product, cart)
# ... (your teammates will do this for their services)
```

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
- Auto-scaling configuration

**⏱️ Takes about 5-7 minutes to complete**

### Step 4: Get the ALB URL
```bash
terraform output alb_dns_name
```

Example output: `ecommerce-a5-alb-1234567890.us-east-1.elb.amazonaws.com`

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
# Maximum 3 instances per service (Assignment 5 requirement)
max_capacity = 3
min_capacity = 1

# CPU targets
credit_card_cpu_target = 70  # Scale at 70% CPU
product_cpu_target     = 70

# Memory targets
warehouse_memory_target = 75  # Scale at 75% memory
cart_memory_target      = 75

# Cooldown periods
scale_in_cooldown  = 60  # Wait 60s before scaling in
scale_out_cooldown = 60  # Wait 60s before scaling out
```

### Service Ports (in variables.tf)

```hcl
service_ports = {
  credit-card-service    = 8082
  warehouse-service      = 8081
  product-service        = 8080
  shopping-cart-service  = 8083
}
```

### Database URL (for Product and Cart services)

Update after Member B deploys the database:

```bash
terraform apply -var="database_url=http://db-leader.internal:5000"
```

## 📊 Monitoring

### CloudWatch Logs
```bash
# View Credit Card Service logs
aws logs tail /ecs/credit-card-service --follow

# View all service logs
aws logs tail /ecs/warehouse-service --follow
aws logs tail /ecs/product-service --follow
aws logs tail /ecs/cart-service --follow
```

### ECS Service Status
```bash
# Check running tasks
aws ecs describe-services \
  --cluster ecommerce-a5-cluster \
  --services ecommerce-a5-credit-card-service \
  --query 'services[0].[serviceName,runningCount,desiredCount]'
```

### Auto-Scaling Events
```bash
# Check scaling activities
aws application-autoscaling describe-scaling-activities \
  --service-namespace ecs \
  --resource-id service/ecommerce-a5-cluster/ecommerce-a5-credit-card-service
```

## 🧹 Cleanup (IMPORTANT!)

**Always destroy resources after testing to avoid charges!**

```bash
# Destroy all resources
terraform destroy

# Type 'yes' when prompted
```

This removes:
- ECS services and tasks
- Load balancer
- VPC and networking
- ECR repositories (and images)
- CloudWatch logs

## 🐛 Troubleshooting

### Service won't start
```bash
# Check task logs
aws ecs describe-tasks \
  --cluster ecommerce-a5-cluster \
  --tasks <task-id> \
  --query 'tasks[0].containers[0].reason'

# Check CloudWatch logs
aws logs tail /ecs/credit-card-service --since 10m
```

### Health checks failing
- Verify health endpoint path in `alb.tf` matches your service
- Check Docker container exposes correct port
- Ensure health endpoint returns HTTP 200

### Can't pull Docker image from ECR
```bash
# Re-login to ECR
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin <account-id>.dkr.ecr.us-east-1.amazonaws.com
```

### Port conflicts
- Each service must use different port
- Update `service_ports` in `variables.tf` if needed

## 📝 Notes for Team

### For Member A (Warehouse Service):
- Your service should listen on port **8081**
- Health endpoint: `/warehouse/health`
- Build and push to ECR after `terraform apply`

### For Member B (Database):
- Provide database URL after deployment
- Update with: `terraform apply -var="database_url=<your-db-url>"`

### For Member C (Product & Cart Services):
- Product service port: **8080**, health: `/products/health`
- Cart service port: **8083**, health: `/cart/health`
- Both services need `DATABASE_URL` environment variable

### For Member D (You):
- Credit Card service port: **8082**, health: `/credit-card-authorizer/health`
- You're responsible for Locust load testing (next step!)

## 🎯 Next Steps

1. ✅ Terraform files are ready
2. ⏳ Wait for teammates to finish their services
3. ⏳ Start AWS Learner Lab
4. ⏳ Run `terraform apply`
5. ⏳ Push Docker images to ECR
6. 🔄 **Create Locust load tests** (your next task!)

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