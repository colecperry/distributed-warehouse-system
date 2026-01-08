# ==========================================
# Database Service Deployment
# ==========================================

# Database Leader Task Definition
resource "aws_ecs_task_definition" "database_leader" {
  family                   = "${var.project_name}-database-leader"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "512"  # 0.5 vCPU for database
  memory                   = "1024" # 1 GB for database
  execution_role_arn       = local.ecs_execution_role_arn
  task_role_arn            = local.ecs_task_role_arn

  container_definitions = jsonencode([
    {
      name      = "database-leader"
      image     = "${aws_ecr_repository.services["database-service"].repository_url}:latest"
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
          "awslogs-group"         = aws_cloudwatch_log_group.services["database-service"].name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "ecs"
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
    Name = "${var.project_name}-database-leader-task"
  }
}

# Database Leader ECS Service
resource "aws_ecs_service" "database_leader" {
  name            = "${var.project_name}-database-leader-service"
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
    container_name   = "database-leader"
    container_port   = 9080
  }

  depends_on = [aws_lb_listener.http]

  tags = {
    Name = "${var.project_name}-database-leader-service"
  }
}

