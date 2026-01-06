


# ==========================================
# ECS Task Definitions (All 4 Services)
# ==========================================

# Credit Card Service Task Definition
resource "aws_ecs_task_definition" "credit_card" {
  family                   = "${var.project_name}-credit-card"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = var.ecs_task_cpu
  memory                   = var.ecs_task_memory
  execution_role_arn       = local.ecs_execution_role_arn
  task_role_arn            = local.ecs_task_role_arn

  container_definitions = jsonencode([
    {
      name      = "credit-card-service"
      image     = "${aws_ecr_repository.services["credit-card-service"].repository_url}:latest"
      essential = true

      portMappings = [
        {
          containerPort = var.service_ports["credit-card-service"]
          protocol      = "tcp"
        }
      ]

      environment = [
        {
          name  = "SPRING_PROFILES_ACTIVE"
          value = "production"
        }
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.services["credit-card-service"].name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "ecs"
        }
      }

      healthCheck = {
        command     = ["CMD-SHELL", "wget --quiet --tries=1 --spider http://localhost:${var.service_ports["credit-card-service"]}/credit-card-authorizer/health || exit 1"]
        interval    = 30
        timeout     = 5
        retries     = 3
        startPeriod = 60
      }
    }
  ])

  tags = {
    Name = "${var.project_name}-credit-card-task"
  }
}

# Warehouse Service Task Definition
resource "aws_ecs_task_definition" "warehouse" {
  family                   = "${var.project_name}-warehouse"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = var.ecs_task_cpu
  memory                   = var.ecs_task_memory
  execution_role_arn       = local.ecs_execution_role_arn
  task_role_arn            = local.ecs_task_role_arn

  container_definitions = jsonencode([
    {
      name      = "warehouse-service"
      image     = "${aws_ecr_repository.services["warehouse-service"].repository_url}:latest"
      essential = true

      portMappings = [
        {
          containerPort = var.service_ports["warehouse-service"]
          protocol      = "tcp"
        }
      ]

      environment = [
        {
          name  = "SPRING_PROFILES_ACTIVE"
          value = "production"
        }
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.services["warehouse-service"].name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "ecs"
        }
      }

      healthCheck = {
        command     = ["CMD-SHELL", "wget --quiet --tries=1 --spider http://localhost:${var.service_ports["warehouse-service"]}/warehouse/health || exit 1"]
        interval    = 30
        timeout     = 5
        retries     = 3
        startPeriod = 60
      }
    }
  ])

  tags = {
    Name = "${var.project_name}-warehouse-task"
  }
}

# Product Service Task Definition
# Product Service Task Definition
resource "aws_ecs_task_definition" "product" {
  family                   = "${var.project_name}-product"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = var.ecs_task_cpu
  memory                   = var.ecs_task_memory
  execution_role_arn       = local.ecs_execution_role_arn
  task_role_arn            = local.ecs_task_role_arn

  container_definitions = jsonencode([
    {
      name      = "product-service"
      image     = "${aws_ecr_repository.services["product-service"].repository_url}:latest"
      essential = true

      portMappings = [
        {
          containerPort = var.service_ports["product-service"]
          protocol      = "tcp"
        }
      ]

      environment = [
        {
          name  = "SPRING_PROFILES_ACTIVE"
          value = "production"
        },
        {
          name  = "DATABASE_URL"
          value = "http://${aws_lb.main.dns_name}"  # Use ALB to reach database leader
        },
        {
          name  = "DATABASE_READ_STRATEGY"
          value = "R1"  # Fast reads for read-heavy Product Service
        }
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.services["product-service"].name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "ecs"
        }
      }

      healthCheck = {
        command     = ["CMD-SHELL", "wget --quiet --tries=1 --spider http://localhost:${var.service_ports["product-service"]}/products/health || exit 1"]
        interval    = 30
        timeout     = 5
        retries     = 3
        startPeriod = 60
      }
    }
  ])

  tags = {
    Name = "${var.project_name}-product-task"
  }
}

# ==========================================
# ECS Services (All 4 Microservices)
# ==========================================

# Credit Card Service
resource "aws_ecs_service" "credit_card" {
  name            = "${var.project_name}-credit-card-service"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.credit_card.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.private[*].id
    security_groups  = [aws_security_group.ecs_tasks.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.credit_card.arn
    container_name   = "credit-card-service"
    container_port   = var.service_ports["credit-card-service"]
  }

  depends_on = [aws_lb_listener.http]

  tags = {
    Name = "${var.project_name}-credit-card-service"
  }
}

# Warehouse Service
resource "aws_ecs_service" "warehouse" {
  name            = "${var.project_name}-warehouse-service"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.warehouse.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.private[*].id
    security_groups  = [aws_security_group.ecs_tasks.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.warehouse.arn
    container_name   = "warehouse-service"
    container_port   = var.service_ports["warehouse-service"]
  }

  depends_on = [aws_lb_listener.http]

  tags = {
    Name = "${var.project_name}-warehouse-service"
  }
}

# Product Service
resource "aws_ecs_service" "product" {
  name            = "${var.project_name}-product-service"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.product.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.private[*].id
    security_groups  = [aws_security_group.ecs_tasks.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.product.arn
    container_name   = "product-service"
    container_port   = var.service_ports["product-service"]
  }

  depends_on = [aws_lb_listener.http]

  tags = {
    Name = "${var.project_name}-product-service"
  }
}

# Shopping Cart Service
resource "aws_ecs_service" "cart" {
  name            = "${var.project_name}-cart-service"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.cart.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.private[*].id
    security_groups  = [aws_security_group.ecs_tasks.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.cart.arn
    container_name   = "cart-service"
    container_port   = var.service_ports["shopping-cart-service"]
  }

  depends_on = [
    aws_lb_listener.http,
    aws_instance.rabbitmq
  ]

  tags = {
    Name = "${var.project_name}-cart-service"
  }
}

# ==========================================
# Auto-Scaling Targets
# ==========================================

# Credit Card - CPU-based scaling
resource "aws_appautoscaling_target" "credit_card" {
  max_capacity       = var.max_capacity
  min_capacity       = var.min_capacity
  resource_id        = "service/${aws_ecs_cluster.main.name}/${aws_ecs_service.credit_card.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

# Warehouse - Memory-based scaling
resource "aws_appautoscaling_target" "warehouse" {
  max_capacity       = var.max_capacity
  min_capacity       = var.min_capacity
  resource_id        = "service/${aws_ecs_cluster.main.name}/${aws_ecs_service.warehouse.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

# Product - CPU-based scaling
resource "aws_appautoscaling_target" "product" {
  max_capacity       = var.max_capacity
  min_capacity       = var.min_capacity
  resource_id        = "service/${aws_ecs_cluster.main.name}/${aws_ecs_service.product.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

# Shopping Cart - Memory-based scaling
resource "aws_appautoscaling_target" "cart" {
  max_capacity       = var.max_capacity
  min_capacity       = var.min_capacity
  resource_id        = "service/${aws_ecs_cluster.main.name}/${aws_ecs_service.cart.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

# ==========================================
# Auto-Scaling Policies - ALL MEMORY BASED @ 35%
# ==========================================

# Credit Card - Scale on MEMORY (changed from CPU)
resource "aws_appautoscaling_policy" "credit_card_memory" {
  name               = "${var.project_name}-credit-card-memory-scaling"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.credit_card.resource_id
  scalable_dimension = aws_appautoscaling_target.credit_card.scalable_dimension
  service_namespace  = aws_appautoscaling_target.credit_card.service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageMemoryUtilization"
    }
    target_value       = var.credit_card_cpu_target  # Reusing variable name
    scale_in_cooldown  = var.scale_in_cooldown
    scale_out_cooldown = var.scale_out_cooldown
  }
}

# Warehouse - Scale on Memory
resource "aws_appautoscaling_policy" "warehouse_memory" {
  name               = "${var.project_name}-warehouse-memory-scaling"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.warehouse.resource_id
  scalable_dimension = aws_appautoscaling_target.warehouse.scalable_dimension
  service_namespace  = aws_appautoscaling_target.warehouse.service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageMemoryUtilization"
    }
    target_value       = var.warehouse_memory_target
    scale_in_cooldown  = var.scale_in_cooldown
    scale_out_cooldown = var.scale_out_cooldown
  }
}

# Product - Scale on MEMORY (changed from CPU)
resource "aws_appautoscaling_policy" "product_memory" {
  name               = "${var.project_name}-product-memory-scaling"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.product.resource_id
  scalable_dimension = aws_appautoscaling_target.product.scalable_dimension
  service_namespace  = aws_appautoscaling_target.product.service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageMemoryUtilization"
    }
    target_value       = var.product_cpu_target  # Reusing variable name
    scale_in_cooldown  = var.scale_in_cooldown
    scale_out_cooldown = var.scale_out_cooldown
  }
}

# Shopping Cart - Scale on Memory
resource "aws_appautoscaling_policy" "cart_memory" {
  name               = "${var.project_name}-cart-memory-scaling"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.cart.resource_id
  scalable_dimension = aws_appautoscaling_target.cart.scalable_dimension
  service_namespace  = aws_appautoscaling_target.cart.service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageMemoryUtilization"
    }
    target_value       = var.cart_memory_target
    scale_in_cooldown  = var.scale_in_cooldown
    scale_out_cooldown = var.scale_out_cooldown
  }
}

# ==========================================
# Outputs
# ==========================================

output "ecs_service_names" {
  description = "Names of all ECS services"
  value = {
    credit_card = aws_ecs_service.credit_card.name
    warehouse   = aws_ecs_service.warehouse.name
    product     = aws_ecs_service.product.name
    cart        = aws_ecs_service.cart.name
  }
}

output "auto_scaling_summary" {
  description = "Auto-scaling configuration summary"
  value = {
    max_instances = var.max_capacity
    min_instances = var.min_capacity
    credit_card   = "Memory @ ${var.credit_card_cpu_target}%"
    warehouse     = "Memory @ ${var.warehouse_memory_target}%"
    product       = "Memory @ ${var.product_cpu_target}%"
    cart          = "Memory @ ${var.cart_memory_target}%"
  }
}
