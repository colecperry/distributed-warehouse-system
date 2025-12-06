# ==========================================
# RabbitMQ on EC2 (Learner Lab Compatible)
# ==========================================

# Get latest Amazon Linux 2023 AMI
data "aws_ami" "amazon_linux_2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-*-x86_64"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

# EC2 Instance for RabbitMQ
resource "aws_instance" "rabbitmq" {
  ami           = data.aws_ami.amazon_linux_2023.id
  instance_type = var.rabbitmq_instance_type

  # Deploy in PUBLIC subnet so it gets public IP
  subnet_id                   = aws_subnet.public[0].id
  vpc_security_group_ids      = [aws_security_group.rabbitmq.id]
  associate_public_ip_address = true

  # User data script to install RabbitMQ via Docker
  user_data = <<-EOF
              #!/bin/bash
              set -e

              # Update system
              yum update -y

              # Install Docker
              yum install -y docker
              systemctl start docker
              systemctl enable docker

              # Add ec2-user to docker group
              usermod -a -G docker ec2-user

              # Run RabbitMQ container
              docker run -d \
                --name rabbitmq \
                --restart unless-stopped \
                -p 5672:5672 \
                -p 15672:15672 \
                -e RABBITMQ_DEFAULT_USER=${var.rabbitmq_username} \
                -e RABBITMQ_DEFAULT_PASS=${var.rabbitmq_password} \
                rabbitmq:3-management

              echo "RabbitMQ installation complete"
              EOF

  # Root volume
  root_block_device {
    volume_type           = "gp3"
    volume_size           = 30
    delete_on_termination = true
    encrypted             = true
  }

  tags = {
    Name        = "${var.project_name}-rabbitmq"
    Environment = var.environment
    Service     = "rabbitmq"
  }

  user_data_replace_on_change = true
}

# Security Group for RabbitMQ EC2
resource "aws_security_group" "rabbitmq" {
  name        = "${var.project_name}-rabbitmq-sg"
  description = "Security group for RabbitMQ EC2 instance"
  vpc_id      = aws_vpc.main.id

  # AMQP from ECS tasks
  ingress {
    description     = "AMQP from ECS tasks"
    from_port       = 5672
    to_port         = 5672
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_tasks.id]
  }

  # RabbitMQ Management UI
  ingress {
    description = "RabbitMQ Management Console"
    from_port   = 15672
    to_port     = 15672
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]  # You can restrict this to your IP
  }

  # SSH for debugging (optional)
  ingress {
    description = "SSH access"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]  # You can restrict this to your IP
  }

  # Allow all outbound
  egress {
    description = "All outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.project_name}-rabbitmq-sg"
  }
}

# ==========================================
# Outputs
# ==========================================

output "rabbitmq_instance_id" {
  description = "EC2 Instance ID of RabbitMQ server"
  value       = aws_instance.rabbitmq.id
}

output "rabbitmq_private_ip" {
  description = "Private IP of RabbitMQ server"
  value       = aws_instance.rabbitmq.private_ip
}

output "rabbitmq_public_ip" {
  description = "Public IP of RabbitMQ server"
  value       = aws_instance.rabbitmq.public_ip
}

output "rabbitmq_management_url" {
  description = "RabbitMQ Management Console URL"
  value       = "http://${aws_instance.rabbitmq.public_ip}:15672"
}

output "rabbitmq_connection_string" {
  description = "RabbitMQ connection string (private IP for VPC services)"
  value       = "amqp://${var.rabbitmq_username}:${var.rabbitmq_password}@${aws_instance.rabbitmq.private_ip}:5672/"
  sensitive   = true
}