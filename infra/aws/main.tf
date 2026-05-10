terraform {
  required_version = ">= 1.6"
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

# ── Red ───────────────────────────────────────────────────────────────────────

resource "aws_vpc" "jarvis" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_hostnames = true
  enable_dns_support   = true
  tags = { Name = "jarvis-vpc", Project = "jarvis" }
}

resource "aws_internet_gateway" "jarvis" {
  vpc_id = aws_vpc.jarvis.id
  tags   = { Name = "jarvis-igw", Project = "jarvis" }
}

resource "aws_subnet" "jarvis" {
  vpc_id                  = aws_vpc.jarvis.id
  cidr_block              = "10.0.1.0/24"
  availability_zone       = "${var.aws_region}a"
  map_public_ip_on_launch = true
  tags                    = { Name = "jarvis-subnet", Project = "jarvis" }
}

resource "aws_route_table" "jarvis" {
  vpc_id = aws_vpc.jarvis.id
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.jarvis.id
  }
  tags = { Name = "jarvis-rt", Project = "jarvis" }
}

resource "aws_route_table_association" "jarvis" {
  subnet_id      = aws_subnet.jarvis.id
  route_table_id = aws_route_table.jarvis.id
}

# ── Security Group ────────────────────────────────────────────────────────────

resource "aws_security_group" "jarvis" {
  name        = "jarvis-sg"
  description = "Jarvis: SSH, HTTP, HTTPS y API directa"
  vpc_id      = aws_vpc.jarvis.id

  ingress {
    description = "SSH"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.ssh_allowed_cidr]
  }

  ingress {
    description = "HTTP (nginx)"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTPS"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "jarvis-sg", Project = "jarvis" }
}

# ── Key Pair ──────────────────────────────────────────────────────────────────

resource "aws_key_pair" "jarvis" {
  key_name   = "jarvis-key-${var.aws_region}"
  public_key = file(var.ssh_public_key_path)
}

# ── AMI: Amazon Linux 2023 (x86_64) ──────────────────────────────────────────

data "aws_ami" "al2023" {
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

# ── EC2 Instance ──────────────────────────────────────────────────────────────
# t3.micro → 2 vCPU / 1 GB RAM — free tier eligible (750 h/mes primer año)
# Para más comodidad en producción: t3.small (2 GB) o t3.medium (4 GB)

resource "aws_instance" "jarvis" {
  ami                    = data.aws_ami.al2023.id
  instance_type          = var.instance_type
  subnet_id              = aws_subnet.jarvis.id
  vpc_security_group_ids = [aws_security_group.jarvis.id]
  key_name               = aws_key_pair.jarvis.key_name

  root_block_device {
    volume_type           = "gp3"
    volume_size           = 20
    delete_on_termination = true
  }

  user_data = templatefile("${path.module}/user-data.sh", {
    repo_url                      = var.repo_url
    anthropic_api_key             = var.anthropic_api_key
    jarvis_default_provider       = var.jarvis_default_provider
    azure_openai_api_key          = var.azure_openai_api_key
    azure_openai_endpoint         = var.azure_openai_endpoint
    azure_orchestrator_deployment = var.azure_orchestrator_deployment
    azure_agent_deployment        = var.azure_agent_deployment
    telegram_bot_token            = var.telegram_bot_token
    telegram_webhook_secret       = var.telegram_webhook_secret
    whatsapp_access_token         = var.whatsapp_access_token
    whatsapp_phone_number_id      = var.whatsapp_phone_number_id
    whatsapp_verify_token         = var.whatsapp_verify_token
  })

  tags = { Name = "jarvis-instance", Project = "jarvis" }
}

# ── Elastic IP ────────────────────────────────────────────────────────────────

resource "aws_eip" "jarvis" {
  instance = aws_instance.jarvis.id
  domain   = "vpc"
  tags     = { Name = "jarvis-eip", Project = "jarvis" }

  depends_on = [aws_internet_gateway.jarvis]
}
