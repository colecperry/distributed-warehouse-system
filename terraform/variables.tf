# ==========================================
# AWS Configuration Variables
# ==========================================

variable "aws_region" {
  description = "AWS region to deploy resources"
  type        = string
  default     = "us-east-1"
}

variable "project_name" {
  description = "Project name used for resource naming"
  type        = string
  default     = "ecommerce-a5"
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
  default     = "dev"
}

# ==========================================
# Networking Variables
# ==========================================

variable "vpc_cidr" {
  description = "CIDR block for VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "availability_zones" {
  description = "Availability zones for subnets"
  type        = list(string)
  default     = ["us-east-1a", "us-east-1b"]
}

variable "public_subnet_cidrs" {
  description = "CIDR blocks for public subnets"
  type        = list(string)
  default     = ["10.0.1.0/24", "10.0.2.0/24"]
}

variable "private_subnet_cidrs" {
  description = "CIDR blocks for private subnets"
  type        = list(string)
  default     = ["10.0.11.0/24", "10.0.12.0/24"]
}

# ==========================================
# ECR Variables
# ==========================================

variable "ecr_repositories" {
  description = "List of ECR repository names for microservices"
  type        = list(string)
  default     = [
    "credit-card-service",
    "warehouse-service",
    "product-service",
    "shopping-cart-service"
  ]
}

# ==========================================
# ECS Variables
# ==========================================

variable "ecs_task_cpu" {
  description = "CPU units for ECS tasks (256 = 0.25 vCPU)"
  type        = string
  default     = "256"
}

variable "ecs_task_memory" {
  description = "Memory for ECS tasks in MB"
  type        = string
  default     = "512"
}

variable "desired_count" {
  description = "Initial desired count of ECS tasks"
  type        = number
  default     = 1
}

# ==========================================
# Auto-scaling Variables (Assignment 5 requirement: max 3 instances)
# ==========================================

variable "min_capacity" {
  description = "Minimum number of tasks"
  type        = number
  default     = 1
}

variable "max_capacity" {
  description = "Maximum number of tasks (Assignment 5 requirement)"
  type        = number
  default     = 3
}

# Credit Card Service - CPU based scaling
variable "credit_card_cpu_target" {
  description = "Target CPU utilization for credit card service"
  type        = number
  default     = 70
}

# Warehouse Service - Memory based scaling
variable "warehouse_memory_target" {
  description = "Target memory utilization for warehouse service"
  type        = number
  default     = 75
}

# Product Service - CPU based scaling
variable "product_cpu_target" {
  description = "Target CPU utilization for product service"
  type        = number
  default     = 70
}

# Shopping Cart Service - Memory based scaling
variable "cart_memory_target" {
  description = "Target memory utilization for shopping cart service"
  type        = number
  default     = 75
}

variable "scale_in_cooldown" {
  description = "Cooldown period (seconds) before allowing scale in"
  type        = number
  default     = 60
}

variable "scale_out_cooldown" {
  description = "Cooldown period (seconds) before allowing scale out"
  type        = number
  default     = 60
}

# ==========================================
# Service Port Mapping
# ==========================================

variable "service_ports" {
  description = "Port mapping for each microservice"
  type        = map(number)
  default = {
    credit-card-service    = 8082
    warehouse-service      = 8081
    product-service        = 8083
    shopping-cart-service  = 8084
  }
}

# ==========================================
# Database Variables (from Assignment 4)
# ==========================================

variable "database_url" {
  description = "Database connection URL for services that need it"
  type        = string
  default     = "placeholder-will-be-set-from-db-module"
}

# ==========================================
# Tags
# ==========================================

variable "common_tags" {
  description = "Common tags to apply to all resources"
  type        = map(string)
  default = {
    Project     = "CS6650-Assignment5"
    ManagedBy   = "Terraform"
  }
}