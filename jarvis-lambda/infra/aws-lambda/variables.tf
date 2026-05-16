variable "aws_region" {
  description = "AWS region for all resources"
  type        = string
  default     = "eu-west-1"
}

variable "project_name" {
  description = "Project name prefix for all resources"
  type        = string
  default     = "jarvis"
}

variable "environment" {
  description = "Deployment environment (dev, staging, prod)"
  type        = string
  default     = "prod"
}

variable "anthropic_api_key" {
  description = "Anthropic API key"
  type        = string
  sensitive   = true
}

variable "whatsapp_access_token" {
  description = "Meta WhatsApp Cloud API access token"
  type        = string
  sensitive   = true
}

variable "whatsapp_phone_number_id" {
  description = "WhatsApp Phone Number ID from Meta Business"
  type        = string
}

variable "whatsapp_verify_token" {
  description = "Verification token for Meta webhook setup"
  type        = string
  sensitive   = true
}

variable "lambda_memory_mb" {
  description = "Memory allocation for agent Lambdas (MB)"
  type        = number
  default     = 512
}

variable "lambda_timeout_seconds" {
  description = "Timeout for agent Lambdas (seconds)"
  type        = number
  default     = 120
}

variable "orchestrator_timeout_seconds" {
  description = "Timeout for orchestrator Lambda (seconds)"
  type        = number
  default     = 180
}

variable "dynamodb_billing_mode" {
  description = "DynamoDB billing mode (PAY_PER_REQUEST or PROVISIONED)"
  type        = string
  default     = "PAY_PER_REQUEST"
}
