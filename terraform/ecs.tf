# ==========================================
# Shopping Cart Service Task Definition
# (Updated for LabRole and Amazon MQ RabbitMQ)
# ==========================================

resource "aws_ecs_task_definition" "cart" {
  family                   = "${var.project_name}-cart"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = var.ecs_task_cpu
  memory                   = var.ecs_task_memory
  execution_role_arn       = local.ecs_execution_role_arn
  task_role_arn            = local.ecs_task_role_arn

  container_definitions = jsonencode([
    {
      name      = "cart-service"
      image     = "${aws_ecr_repository.services["shopping-cart-service"].repository_url}:latest"
      essential = true

      portMappings = [
        {
          containerPort = var.service_ports["shopping-cart-service"]
          protocol      = "tcp"
        }
      ]

      # Environment variables - Database on laptop, RabbitMQ on AWS
      environment = [
        {
          name  = "DATABASE_URL"
          value = "http://${aws_lb.main.dns_name}"
        },
        {
          name  = "CREDIT_CARD_AUTHORIZER_URL"
          value = "http://${aws_lb.main.dns_name}/credit-card-authorizer/authorize"
        },
        {
          name  = "PRODUCT_SERVICE_URL"
          value = "http://${aws_lb.main.dns_name}"
        },
        {
          name  = "WAREHOUSE_SERVICE_URL"
          value = "http://${aws_lb.main.dns_name}"
        },
        {
          name  = "RABBITMQ_HOST"
          value = aws_instance.rabbitmq.private_ip
        },
        {
          name  = "RABBITMQ_PORT"
          value = "5672"
        },
        {
          name  = "RABBITMQ_USERNAME"
          value = var.rabbitmq_username
        },
        {
          name  = "RABBITMQ_PASSWORD"
          value = var.rabbitmq_password
        }
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.services["shopping-cart-service"].name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "ecs"
        }
      }

      healthCheck = {
        command     = ["CMD-SHELL", "wget --quiet --tries=1 --spider http://localhost:${var.service_ports["shopping-cart-service"]}/cart/health || exit 1"]
        interval    = 30
        timeout     = 5
        retries     = 3
        startPeriod = 60
      }
    }
  ])

  # Wait for RabbitMQ to be ready before creating tasks
  depends_on = [aws_instance.rabbitmq]

  tags = {
    Name = "${var.project_name}-cart-task"
  }
}