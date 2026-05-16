terraform {
  required_version = ">= 1.6"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    archive = {
      source  = "hashicorp/archive"
      version = "~> 2.0"
    }
  }

  backend "s3" {
    # Configure via -backend-config or terraform.tfvars
    # bucket = "your-terraform-state-bucket"
    # key    = "jarvis/lambda/terraform.tfstate"
    # region = "eu-west-1"
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = var.project_name
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}

locals {
  prefix     = "${var.project_name}-${var.environment}"
  agent_list = ["secretary", "developer", "devops", "frontend", "security", "social-media"]
}
