# ==========================================
# ElastiCache Subnet Group
# ==========================================

resource "aws_elasticache_subnet_group" "redis" {
  name       = "${var.project_name}-redis-subnet-group"
  subnet_ids = aws_subnet.private[*].id

  tags = {
    Name = "${var.project_name}-redis-subnet-group"
  }
}

# ==========================================
# ElastiCache Security Group
# ==========================================

resource "aws_security_group" "elasticache" {
  name        = "${var.project_name}-elasticache-sg"
  description = "Security group for ElastiCache Redis"
  vpc_id      = aws_vpc.main.id

  # Allow inbound Redis port (6379) from ECS tasks
  ingress {
    description     = "Redis from ECS tasks"
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_tasks.id]
  }

  # Allow all outbound traffic
  egress {
    description = "All outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.project_name}-elasticache-sg"
  }
}

# ==========================================
# ElastiCache Redis Replication Group
# ==========================================

resource "aws_elasticache_replication_group" "redis" {
  replication_group_id       = "${var.project_name}-redis"
  description                = "Redis cache for Product Service"
  
  # Redis engine configuration
  engine                     = "redis"
  engine_version             = "7.1"
  node_type                  = var.redis_node_type
  port                       = 6379
  parameter_group_name       = "default.redis7"
  
  # High availability configuration
  num_cache_clusters         = var.redis_num_cache_nodes
  automatic_failover_enabled = var.redis_num_cache_nodes > 1 ? true : false
  multi_az_enabled           = var.redis_num_cache_nodes > 1 ? true : false
  
  # Network configuration
  subnet_group_name          = aws_elasticache_subnet_group.redis.name
  security_group_ids         = [aws_security_group.elasticache.id]
  
  # Snapshot and backup configuration
  snapshot_retention_limit   = 5
  snapshot_window            = "03:00-05:00"
  
  # Apply at rest encryption (optional but recommended for production)
  at_rest_encryption_enabled = false  # Set to true for production
  transit_encryption_enabled = false  # Set to true for production (requires auth token)
  
  # Maintenance window
  maintenance_window         = "mon:05:00-mon:07:00"
  
  # Log delivery configuration (optional)
  log_delivery_configuration {
    destination      = aws_cloudwatch_log_group.redis.name
    destination_type = "cloudwatch-logs"
    log_format       = "text"
    log_type         = "slow-log"
  }

  tags = {
    Name = "${var.project_name}-redis"
  }
}

# ==========================================
# CloudWatch Log Group for Redis Slow Logs
# ==========================================

resource "aws_cloudwatch_log_group" "redis" {
  name              = "/aws/elasticache/redis/${var.project_name}-redis"
  retention_in_days = 7

  tags = {
    Name = "${var.project_name}-redis-logs"
  }
}

# ==========================================
# Outputs
# ==========================================

output "redis_primary_endpoint" {
  description = "Redis primary endpoint (configuration endpoint)"
  value       = aws_elasticache_replication_group.redis.configuration_endpoint_address != null ? aws_elasticache_replication_group.redis.configuration_endpoint_address : aws_elasticache_replication_group.redis.primary_endpoint_address
}

output "redis_port" {
  description = "Redis port"
  value       = aws_elasticache_replication_group.redis.port
}

output "redis_endpoint_address" {
  description = "Redis endpoint address (for single-node or primary)"
  value       = aws_elasticache_replication_group.redis.primary_endpoint_address
}

output "redis_replication_group_id" {
  description = "Redis replication group ID"
  value       = aws_elasticache_replication_group.redis.replication_group_id
}
