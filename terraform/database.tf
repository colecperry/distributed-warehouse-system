# ==========================================
# Database ECR Repository
# ==========================================

resource "aws_ecr_repository" "database" {
  name                 = "w1r5-database"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }

  tags = {
    Name = "w1r5-database"
  }
}

# ECR Lifecycle Policy
resource "aws_ecr_lifecycle_policy" "database" {
  repository = aws_ecr_repository.database.name

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
# CloudWatch Log Groups for Database
# ==========================================

resource "aws_cloudwatch_log_group" "database_leader" {
  name              = "/ecs/w1r5-leader"
  retention_in_days = 7

  tags = {
    Name = "/ecs/w1r5-leader"
  }
}

resource "aws_cloudwatch_log_group" "database_follower" {
  name              = "/ecs/w1r5-follower"
  retention_in_days = 7

  tags = {
    Name = "/ecs/w1r5-follower"
  }
}

# ==========================================
# Task Definition: Database Leader
# ==========================================

resource "aws_ecs_task_definition" "database_leader" {
  family                   = "${var.project_name}-db-leader"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "512"   # 0.5 vCPU
  memory                   = "1024"  # 1 GB
  execution_role_arn       = local.ecs_execution_role_arn
  task_role_arn            = local.ecs_task_role_arn

  container_definitions = jsonencode([
    {
      name      = "db-leader"
      image     = "${aws_ecr_repository.database.repository_url}:latest"
      essential = true

      portMappings = [
        {
          containerPort = 9080
          protocol      = "tcp"
        }
      ]

      environment = [
        {
          name  = "PORT"
          value = "9080"
        },
        {
          name  = "NODE_ID"
          value = "1"
        },
        {
          name  = "NODE_TYPE"
          value = "leader"
        }
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.database_leader.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "leader"
        }
      }

      healthCheck = {
        command     = ["CMD-SHELL", "wget --quiet --tries=1 --spider http://localhost:9080/api/leader/health || exit 1"]
        interval    = 30
        timeout     = 5
        retries     = 3
        startPeriod = 60
      }
    }
  ])

  tags = {
    Name = "${var.project_name}-db-leader-task"
  }
}

# ==========================================
# Task Definitions: Database Followers (4 separate definitions)
# ==========================================

# Follower 1 (Node ID 2, Port 9081)
resource "aws_ecs_task_definition" "database_follower_1" {
  family                   = "${var.project_name}-db-follower-1"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "512"
  memory                   = "1024"
  execution_role_arn       = local.ecs_execution_role_arn
  task_role_arn            = local.ecs_task_role_arn

  container_definitions = jsonencode([
    {
      name      = "db-follower-1"
      image     = "${aws_ecr_repository.database.repository_url}:latest"
      essential = true

      portMappings = [
        {
          containerPort = 9081
          protocol      = "tcp"
        }
      ]

      environment = [
        {
          name  = "PORT"
          value = "9081"
        },
        {
          name  = "NODE_ID"
          value = "2"
        },
        {
          name  = "NODE_TYPE"
          value = "follower"
        },
        {
          name  = "LEADER_URL"
          value = "http://${aws_lb.main.dns_name}"
        }
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.database_follower.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "follower-1"
        }
      }

      healthCheck = {
        command     = ["CMD-SHELL", "wget --quiet --tries=1 --spider http://localhost:9081/api/follower/health || exit 1"]
        interval    = 30
        timeout     = 5
        retries     = 3
        startPeriod = 60
      }
    }
  ])

  tags = {
    Name = "${var.project_name}-db-follower-1-task"
  }
}

# Follower 2 (Node ID 3, Port 9082)
resource "aws_ecs_task_definition" "database_follower_2" {
  family                   = "${var.project_name}-db-follower-2"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "512"
  memory                   = "1024"
  execution_role_arn       = local.ecs_execution_role_arn
  task_role_arn            = local.ecs_task_role_arn

  container_definitions = jsonencode([
    {
      name      = "db-follower-2"
      image     = "${aws_ecr_repository.database.repository_url}:latest"
      essential = true

      portMappings = [
        {
          containerPort = 9082
          protocol      = "tcp"
        }
      ]

      environment = [
        {
          name  = "PORT"
          value = "9082"
        },
        {
          name  = "NODE_ID"
          value = "3"
        },
        {
          name  = "NODE_TYPE"
          value = "follower"
        },
        {
          name  = "LEADER_URL"
          value = "http://${aws_lb.main.dns_name}"
        }
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.database_follower.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "follower-2"
        }
      }

      healthCheck = {
        command     = ["CMD-SHELL", "wget --quiet --tries=1 --spider http://localhost:9082/api/follower/health || exit 1"]
        interval    = 30
        timeout     = 5
        retries     = 3
        startPeriod = 60
      }
    }
  ])

  tags = {
    Name = "${var.project_name}-db-follower-2-task"
  }
}

# Follower 3 (Node ID 4, Port 9083)
resource "aws_ecs_task_definition" "database_follower_3" {
  family                   = "${var.project_name}-db-follower-3"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "512"
  memory                   = "1024"
  execution_role_arn       = local.ecs_execution_role_arn
  task_role_arn            = local.ecs_task_role_arn

  container_definitions = jsonencode([
    {
      name      = "db-follower-3"
      image     = "${aws_ecr_repository.database.repository_url}:latest"
      essential = true

      portMappings = [
        {
          containerPort = 9083
          protocol      = "tcp"
        }
      ]

      environment = [
        {
          name  = "PORT"
          value = "9083"
        },
        {
          name  = "NODE_ID"
          value = "4"
        },
        {
          name  = "NODE_TYPE"
          value = "follower"
        },
        {
          name  = "LEADER_URL"
          value = "http://${aws_lb.main.dns_name}"
        }
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.database_follower.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "follower-3"
        }
      }

      healthCheck = {
        command     = ["CMD-SHELL", "wget --quiet --tries=1 --spider http://localhost:9083/api/follower/health || exit 1"]
        interval    = 30
        timeout     = 5
        retries     = 3
        startPeriod = 60
      }
    }
  ])

  tags = {
    Name = "${var.project_name}-db-follower-3-task"
  }
}

# Follower 4 (Node ID 5, Port 9084)
resource "aws_ecs_task_definition" "database_follower_4" {
  family                   = "${var.project_name}-db-follower-4"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "512"
  memory                   = "1024"
  execution_role_arn       = local.ecs_execution_role_arn
  task_role_arn            = local.ecs_task_role_arn

  container_definitions = jsonencode([
    {
      name      = "db-follower-4"
      image     = "${aws_ecr_repository.database.repository_url}:latest"
      essential = true

      portMappings = [
        {
          containerPort = 9084
          protocol      = "tcp"
        }
      ]

      environment = [
        {
          name  = "PORT"
          value = "9084"
        },
        {
          name  = "NODE_ID"
          value = "5"
        },
        {
          name  = "NODE_TYPE"
          value = "follower"
        },
        {
          name  = "LEADER_URL"
          value = "http://${aws_lb.main.dns_name}"
        }
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.database_follower.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "follower-4"
        }
      }

      healthCheck = {
        command     = ["CMD-SHELL", "wget --quiet --tries=1 --spider http://localhost:9084/api/follower/health || exit 1"]
        interval    = 30
        timeout     = 5
        retries     = 3
        startPeriod = 60
      }
    }
  ])

  tags = {
    Name = "${var.project_name}-db-follower-4-task"
  }
}

# ==========================================
# ECS Service: Database Leader (1 instance)
# ==========================================

resource "aws_ecs_service" "database_leader" {
  name            = "${var.project_name}-db-leader-service"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.database_leader.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.private[*].id
    security_groups  = [aws_security_group.database.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.database_leader.arn
    container_name   = "db-leader"
    container_port   = 9080
  }

  depends_on = [aws_lb_listener.http]

  tags = {
    Name = "${var.project_name}-db-leader-service"
  }
}

# ==========================================
# ECS Services: Database Followers (4 separate services)
# ==========================================

resource "aws_ecs_service" "database_follower_1" {
  name            = "${var.project_name}-db-follower-1-service"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.database_follower_1.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.private[*].id
    security_groups  = [aws_security_group.database.id]
    assign_public_ip = false
  }

  depends_on = [aws_ecs_service.database_leader]

  tags = {
    Name = "${var.project_name}-db-follower-1-service"
  }
}

resource "aws_ecs_service" "database_follower_2" {
  name            = "${var.project_name}-db-follower-2-service"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.database_follower_2.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.private[*].id
    security_groups  = [aws_security_group.database.id]
    assign_public_ip = false
  }

  depends_on = [aws_ecs_service.database_leader]

  tags = {
    Name = "${var.project_name}-db-follower-2-service"
  }
}

resource "aws_ecs_service" "database_follower_3" {
  name            = "${var.project_name}-db-follower-3-service"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.database_follower_3.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.private[*].id
    security_groups  = [aws_security_group.database.id]
    assign_public_ip = false
  }

  depends_on = [aws_ecs_service.database_leader]

  tags = {
    Name = "${var.project_name}-db-follower-3-service"
  }
}

resource "aws_ecs_service" "database_follower_4" {
  name            = "${var.project_name}-db-follower-4-service"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.database_follower_4.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.private[*].id
    security_groups  = [aws_security_group.database.id]
    assign_public_ip = false
  }

  depends_on = [aws_ecs_service.database_leader]

  tags = {
    Name = "${var.project_name}-db-follower-4-service"
  }
}

# ==========================================
# Outputs
# ==========================================

output "database_ecr_url" {
  description = "ECR repository URL for database image"
  value       = aws_ecr_repository.database.repository_url
}

output "database_leader_service_name" {
  description = "Name of database leader ECS service"
  value       = aws_ecs_service.database_leader.name
}

output "database_follower_service_names" {
  description = "Names of database follower ECS services"
  value = {
    follower_1 = aws_ecs_service.database_follower_1.name
    follower_2 = aws_ecs_service.database_follower_2.name
    follower_3 = aws_ecs_service.database_follower_3.name
    follower_4 = aws_ecs_service.database_follower_4.name
  }
}

output "database_access_url" {
  description = "URL to access database through ALB"
  value       = "http://${aws_lb.main.dns_name}/api"
}