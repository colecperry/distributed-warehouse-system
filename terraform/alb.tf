# ==========================================
# Application Load Balancer
# ==========================================

resource "aws_lb" "main" {
  name               = "${var.project_name}-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = aws_subnet.public[*].id

  enable_deletion_protection = false
  enable_http2              = true

  tags = {
    Name = "${var.project_name}-alb"
  }
}

# ==========================================
# Target Groups (one for each microservice)
# ==========================================

# Credit Card Service Target Group
resource "aws_lb_target_group" "credit_card" {
  name        = "${var.project_name}-credit-card-tg"
  port        = var.service_ports["credit-card-service"]
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"

  health_check {
    enabled             = true
    healthy_threshold   = 2
    unhealthy_threshold = 3
    timeout             = 5
    interval            = 30
    path                = "/credit-card-authorizer/health"
    protocol            = "HTTP"
    matcher             = "200"
  }

  deregistration_delay = 30

  tags = {
    Name = "${var.project_name}-credit-card-tg"
  }
}

# Warehouse Service Target Group
resource "aws_lb_target_group" "warehouse" {
  name        = "${var.project_name}-warehouse-tg"
  port        = var.service_ports["warehouse-service"]
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"

  health_check {
    enabled             = true
    healthy_threshold   = 2
    unhealthy_threshold = 3
    timeout             = 5
    interval            = 30
    path                = "/warehouse/health"
    protocol            = "HTTP"
    matcher             = "200"
  }

  deregistration_delay = 30

  tags = {
    Name = "${var.project_name}-warehouse-tg"
  }
}

# Product Service Target Group
resource "aws_lb_target_group" "product" {
  name        = "${var.project_name}-product-tg"
  port        = var.service_ports["product-service"]
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"

  health_check {
    enabled             = true
    healthy_threshold   = 2
    unhealthy_threshold = 3
    timeout             = 5
    interval            = 30
    path                = "/products/health"
    protocol            = "HTTP"
    matcher             = "200"
  }

  deregistration_delay = 30

  tags = {
    Name = "${var.project_name}-product-tg"
  }
}

# Shopping Cart Service Target Group
resource "aws_lb_target_group" "cart" {
  name        = "${var.project_name}-cart-tg"
  port        = var.service_ports["shopping-cart-service"]
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"

  health_check {
    enabled             = true
    healthy_threshold   = 2
    unhealthy_threshold = 3
    timeout             = 5
    interval            = 30
    path                = "/cart/health"
    protocol            = "HTTP"
    matcher             = "200"
  }

  deregistration_delay = 30

  tags = {
    Name = "${var.project_name}-cart-tg"
  }
}

# ==========================================
# ALB Listener (Port 80 HTTP)
# ==========================================

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = "80"
  protocol          = "HTTP"

  # Default action - return 404 for unmatched paths
  default_action {
    type = "fixed-response"

    fixed_response {
      content_type = "text/plain"
      message_body = "404: Service not found"
      status_code  = "404"
    }
  }

  tags = {
    Name = "${var.project_name}-http-listener"
  }
}

# ==========================================
# ALB Listener Rules (path-based routing)
# ==========================================

# Route /credit-card/* to Credit Card Service
resource "aws_lb_listener_rule" "credit_card" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 100

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.credit_card.arn
  }

  condition {
    path_pattern {
      values = ["/credit-card-authorizer/*"]
    }
  }

  tags = {
    Name = "${var.project_name}-credit-card-rule"
  }
}

# Route /warehouse/* to Warehouse Service
resource "aws_lb_listener_rule" "warehouse" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 200

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.warehouse.arn
  }

  condition {
    path_pattern {
      values = ["/reserve", "/ship", "/warehouse/health"]
    }
  }

  tags = {
    Name = "${var.project_name}-warehouse-rule"
  }
}

# Route /products/* to Product Service
resource "aws_lb_listener_rule" "product" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 300

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.product.arn
  }

  condition {
    path_pattern {
      values = ["/products/*", "/products"]
    }
  }

  tags = {
    Name = "${var.project_name}-product-rule"
  }
}

# Route /cart/* to Shopping Cart Service
resource "aws_lb_listener_rule" "cart" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 400

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.cart.arn
  }

  condition {
    path_pattern {
      values = ["/shopping-cart", "/shopping-cart/*", "/shopping-carts/*","/cart/health"]
    }
  }

  tags = {
    Name = "${var.project_name}-cart-rule"
  }
}

# ==========================================
# Outputs
# ==========================================

output "alb_dns_name" {
  description = "DNS name of the Application Load Balancer"
  value       = aws_lb.main.dns_name
}

output "alb_arn" {
  description = "ARN of the Application Load Balancer"
  value       = aws_lb.main.arn
}

output "target_group_arns" {
  description = "ARNs of all target groups"
  value = {
    credit_card = aws_lb_target_group.credit_card.arn
    warehouse   = aws_lb_target_group.warehouse.arn
    product     = aws_lb_target_group.product.arn
    cart        = aws_lb_target_group.cart.arn
  }
}

# ==========================================
# Database Leader Target Group
# ==========================================

resource "aws_lb_target_group" "database_leader" {
  name        = "${var.project_name}-db-leader-tg"
  port        = 9080
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"

  health_check {
    enabled             = true
    healthy_threshold   = 2
    unhealthy_threshold = 3
    timeout             = 5
    interval            = 30
    path                = "/api/leader/health"
    protocol            = "HTTP"
    matcher             = "200"
  }

  deregistration_delay = 30

  tags = {
    Name = "${var.project_name}-db-leader-tg"
  }
}

# ==========================================
# ALB Listener Rule for Database Leader
# ==========================================

# Route /api/* to Database Leader (highest priority to catch database calls)
resource "aws_lb_listener_rule" "database_leader" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 50  # Higher priority than other services

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.database_leader.arn
  }

  condition {
    path_pattern {
      values = ["/api/*"]
    }
  }

  tags = {
    Name = "${var.project_name}-db-leader-rule"
  }
}