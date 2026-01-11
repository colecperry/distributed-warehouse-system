#!/bin/bash
# Script to destroy all AWS infrastructure to stop all charges

set -e

echo "🛑 Destroying all AWS infrastructure..."
echo ""

# Get script directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

echo "This will destroy:"
echo "  - All ECS services and tasks"
echo "  - Application Load Balancer"
echo "  - NAT Gateway (biggest cost)"
echo "  - EC2 instance (RabbitMQ)"
echo "  - VPC and networking"
echo "  - ECR repositories and images"
echo "  - CloudWatch logs"
echo ""
echo "⚠️  This action cannot be undone!"
echo ""
read -p "Are you sure you want to destroy everything? (type 'yes' to confirm): " confirm

if [ "$confirm" != "yes" ]; then
  echo "❌ Cancelled. Infrastructure not destroyed."
  exit 1
fi

echo ""
echo "🗑️  Running terraform destroy..."
terraform destroy -auto-approve

echo ""
echo "✅ All infrastructure destroyed!"
echo "💰 No charges will be incurred."
echo ""
echo "To recreate infrastructure, run: ./start-services.sh"

