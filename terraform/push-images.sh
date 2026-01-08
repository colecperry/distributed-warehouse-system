#!/bin/bash
# Script to push Docker images to ECR for all services

set -e  # Exit on error

echo "🚀 Starting Docker image push to ECR..."

# Get AWS account ID
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
AWS_REGION="us-west-2"
ECR_BASE="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

echo "📦 AWS Account ID: ${AWS_ACCOUNT_ID}"
echo "🌍 Region: ${AWS_REGION}"

# Login to ECR
echo ""
echo "🔐 Logging in to ECR..."
aws ecr get-login-password --region ${AWS_REGION} | \
  docker login --username AWS --password-stdin ${ECR_BASE}

echo "✅ Logged in successfully!"
echo ""

# Function to build, tag, and push a service
push_service() {
  local SERVICE_NAME=$1
  local SERVICE_DIR="../microservices/${SERVICE_NAME}"
  
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "📦 Processing: ${SERVICE_NAME}"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  
  # Check if service directory exists
  if [ ! -d "${SERVICE_DIR}" ]; then
    echo "❌ Error: Directory ${SERVICE_DIR} not found!"
    return 1
  fi
  
  cd "${SERVICE_DIR}"
  
  echo "🔨 Building Docker image for linux/amd64..."
  docker build --platform linux/amd64 -t ${SERVICE_NAME}:latest .
  
  echo "🏷️  Tagging image..."
  docker tag ${SERVICE_NAME}:latest ${ECR_BASE}/${SERVICE_NAME}:latest
  
  echo "⬆️  Pushing to ECR..."
  docker push ${ECR_BASE}/${SERVICE_NAME}:latest
  
  echo "✅ ${SERVICE_NAME} pushed successfully!"
  echo ""
  
  cd - > /dev/null
}

# Push each service
push_service "credit-card-service"
push_service "product-service"
push_service "shopping-cart-service"
push_service "warehouse-service"

# Push database service (special path)
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📦 Processing: database-service"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

DATABASE_DIR="../database/leader-follower-w1r5"

if [ ! -d "${DATABASE_DIR}" ]; then
  echo "❌ Error: Directory ${DATABASE_DIR} not found!"
  exit 1
fi

cd "${DATABASE_DIR}"

echo "🔨 Building Docker image for linux/amd64..."
docker build --platform linux/amd64 -t database-service:latest .

echo "🏷️  Tagging image..."
docker tag database-service:latest ${ECR_BASE}/database-service:latest

echo "⬆️  Pushing to ECR..."
docker push ${ECR_BASE}/database-service:latest

echo "✅ database-service pushed successfully!"
echo ""

cd - > /dev/null

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🎉 All images pushed successfully!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "Next steps:"
echo "1. Verify images in ECR Console"
echo "2. Check ECS services are using the new images"
echo "3. Run load tests"

