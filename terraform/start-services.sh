#!/bin/bash
# Script to create all AWS infrastructure and start services

set -e

echo "🚀 Creating AWS infrastructure and starting services..."
echo ""

# Get script directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

# Step 1: Initialize Terraform (if needed)
if [ ! -d ".terraform" ]; then
  echo "📦 Initializing Terraform..."
  terraform init
  echo ""
fi

# Step 2: Apply Terraform (creates all infrastructure)
echo "🏗️  Creating infrastructure (this takes 5-7 minutes)..."
terraform apply -auto-approve

echo ""
echo "✅ Infrastructure created!"
echo ""

# Step 3: Build and push Docker images
echo "📦 Building and pushing Docker images..."
echo "   (This may take 5-10 minutes depending on your connection)"
echo ""

# Check if images need to be pushed
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
AWS_REGION="us-west-2"
ECR_BASE="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

# Login to ECR
echo "🔐 Logging in to ECR..."
aws ecr get-login-password --region ${AWS_REGION} | \
  docker login --username AWS --password-stdin ${ECR_BASE} > /dev/null 2>&1

# Run push script
./push-images.sh

echo ""
echo "✅ All images pushed!"
echo ""

# Step 4: Wait for services to start
echo "⏱️  Waiting for services to start (2-3 minutes)..."
echo "   Services are starting automatically..."
echo ""

CLUSTER=$(terraform output -raw ecs_cluster_name)
REGION="us-west-2"

# Wait and check service status
echo "Checking service status..."
for i in {1..30}; do
  sleep 10
  RUNNING=$(aws ecs list-tasks --cluster $CLUSTER --region $REGION --query 'length(taskArns)' --output text 2>/dev/null || echo "0")
  if [ "$RUNNING" -gt 0 ]; then
    echo "  ✅ $RUNNING tasks running..."
    break
  fi
  echo "  ⏳ Waiting... ($i/30)"
done

echo ""
echo "✅ Infrastructure and services are starting!"
echo ""
echo "📊 Get ALB URL:"
echo "   terraform output alb_dns_name"
echo ""
echo "📊 Check service status:"
echo "   CLUSTER=\$(terraform output -raw ecs_cluster_name)"
echo "   aws ecs describe-services --cluster \$CLUSTER --services ecommerce-a5-product-service --region us-west-2 --query 'services[0].[runningCount,desiredCount]' --output text"
echo ""
echo "⏱️  Services take 2-3 minutes to fully start and pass health checks."

