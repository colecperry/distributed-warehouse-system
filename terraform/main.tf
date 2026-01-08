# ==========================================
# Terraform Configuration
# ==========================================

terraform {
  required_version = ">= 1.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

# ==========================================
# AWS Provider Configuration
# ==========================================

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = var.common_tags
  }
}

# ==========================================
# ECR Repositories
# ==========================================

resource "aws_ecr_repository" "services" {
  for_each = toset(var.ecr_repositories)

  name                 = each.value
  image_tag_mutability = "MUTABLE"
  force_delete         = true  # Allow deletion even if repository contains images

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }

  tags = {
    Name = each.value
  }
}

# ECR Lifecycle Policy - Keep only last 10 images
resource "aws_ecr_lifecycle_policy" "services" {
  for_each   = aws_ecr_repository.services
  repository = each.value.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep last 10 images"
      selection = {
        tagStatus     = "any"
        countType     = "imageCountMoreThan"
        countNumber   = 10
      }
      action = {
        type = "expire"
      }
    }]
  })
}

# ==========================================
# ECS Cluster
# ==========================================

resource "aws_ecs_cluster" "main" {
  name = "${var.project_name}-cluster"

  setting {
    name  = "containerInsights"
    value = "enabled"
  }

  tags = {
    Name = "${var.project_name}-ecs-cluster"
  }
}

# ==========================================
# CloudWatch Log Groups
# ==========================================

resource "aws_cloudwatch_log_group" "services" {
  for_each = toset(var.ecr_repositories)

  name              = "/ecs/${each.value}"
  retention_in_days = 7

  tags = {
    Name = "/ecs/${each.value}"
  }
}

# ==========================================
# IAM Roles for ECS Tasks
# ==========================================

# ECS Task Execution Role - Allows tasks to pull images, write logs
resource "aws_iam_role" "ecs_execution_role" {
  name = "${var.project_name}-ecs-execution-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "ecs-tasks.amazonaws.com"
      }
    }]
  })

  tags = {
    Name = "${var.project_name}-ecs-execution-role"
  }
}

# Attach AWS managed policy for ECS task execution
resource "aws_iam_role_policy_attachment" "ecs_execution_role_policy" {
  role       = aws_iam_role.ecs_execution_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# ECS Task Role - Allows tasks to access AWS services
resource "aws_iam_role" "ecs_task_role" {
  name = "${var.project_name}-ecs-task-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "ecs-tasks.amazonaws.com"
      }
    }]
  })

  tags = {
    Name = "${var.project_name}-ecs-task-role"
  }
}

# Use created roles for execution and task
locals {
  ecs_execution_role_arn = aws_iam_role.ecs_execution_role.arn
  ecs_task_role_arn      = aws_iam_role.ecs_task_role.arn
}

# ==========================================
# Outputs
# ==========================================

output "ecr_repository_urls" {
  description = "URLs of ECR repositories"
  value = {
    for repo in aws_ecr_repository.services :
    repo.name => repo.repository_url
  }
}

output "ecs_cluster_name" {
  description = "Name of the ECS cluster"
  value       = aws_ecs_cluster.main.name
}

output "ecs_cluster_arn" {
  description = "ARN of the ECS cluster"
  value       = aws_ecs_cluster.main.arn
}

output "ecs_execution_role_name" {
  description = "Name of the ECS execution role"
  value       = aws_iam_role.ecs_execution_role.name
}

output "ecs_task_role_name" {
  description = "Name of the ECS task role"
  value       = aws_iam_role.ecs_task_role.name
}

output "ecs_execution_role_arn" {
  description = "ARN being used for ECS execution"
  value       = local.ecs_execution_role_arn
}

output "ecs_task_role_arn" {
  description = "ARN being used for ECS tasks"
  value       = local.ecs_task_role_arn
}