terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

# ===============================
# KEY PAIR (SSH)
# ===============================
resource "aws_key_pair" "deployer" {
  key_name   = "${var.project_name}-key"
  public_key = file(var.public_key_path)
}

# ===============================
# EC2
# ===============================
resource "aws_instance" "app" {
  # Amazon Linux 2023 - us-east-1 (Free Tier eligible)
  ami                    = "ami-0c02fb55956c7d316"
  instance_type          = "t2.micro"
  key_name               = aws_key_pair.deployer.key_name
  vpc_security_group_ids = [aws_security_group.ec2.id]
  user_data              = file("${path.module}/user_data.sh")

  root_block_device {
    volume_size = 8
    volume_type = "gp2"
  }

  tags = {
    Name    = "${var.project_name}-ec2"
    Project = var.project_name
  }
}

# ===============================
# ELASTIC IP (IP fixo para EC2)
# ===============================
resource "aws_eip" "app" {
  instance = aws_instance.app.id
  domain   = "vpc"

  tags = {
    Name    = "${var.project_name}-eip"
    Project = var.project_name
  }
}

# ===============================
# RDS MYSQL
# ===============================
resource "aws_db_instance" "mysql" {
  identifier        = "${var.project_name}-db"
  engine            = "mysql"
  engine_version    = "8.0"
  instance_class    = "db.t3.micro"
  allocated_storage = 20
  storage_type      = "gp2"

  db_name  = var.db_name
  username = var.db_user
  password = var.db_password

  publicly_accessible    = false
  vpc_security_group_ids = [aws_security_group.rds.id]

  backup_retention_period = 0
  skip_final_snapshot     = true

  tags = {
    Name    = "${var.project_name}-rds"
    Project = var.project_name
  }
}
