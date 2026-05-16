# ── Lambda layer with shared Python utilities ──────────────────────
data "archive_file" "shared_layer" {
  type        = "zip"
  source_dir  = "${path.root}/../../"
  output_path = "${path.root}/../../dist/shared-layer.zip"
  excludes    = ["dist", "infra", ".git", "__pycache__", "*.pyc"]
}

resource "aws_lambda_layer_version" "shared" {
  layer_name          = "${local.prefix}-shared"
  filename            = data.archive_file.shared_layer.output_path
  source_code_hash    = data.archive_file.shared_layer.output_base64sha256
  compatible_runtimes = ["python3.12"]
  description         = "Jarvis shared utilities (anthropic, boto3 wrappers, models)"
}

# ── Webhook Lambda ─────────────────────────────────────────────────
data "archive_file" "webhook" {
  type        = "zip"
  source_dir  = "${path.root}/../../lambdas/webhook"
  output_path = "${path.root}/../../dist/webhook.zip"
}

resource "aws_lambda_function" "webhook" {
  function_name    = "${local.prefix}-webhook"
  description      = "WhatsApp webhook receiver"
  role             = aws_iam_role.webhook_lambda.arn
  runtime          = "python3.12"
  handler          = "handler.handler"
  filename         = data.archive_file.webhook.output_path
  source_code_hash = data.archive_file.webhook.output_base64sha256
  timeout          = 10
  memory_size      = 256
  layers           = [aws_lambda_layer_version.shared.arn]

  environment {
    variables = {
      AWS_REGION            = var.aws_region
      ORCHESTRATOR_QUEUE_URL = aws_sqs_queue.orchestrator.url
      WHATSAPP_SECRET_ARN   = aws_secretsmanager_secret.whatsapp.arn
    }
  }
}

# ── Orchestrator Lambda ────────────────────────────────────────────
data "archive_file" "orchestrator" {
  type        = "zip"
  source_dir  = "${path.root}/../../lambdas/orchestrator"
  output_path = "${path.root}/../../dist/orchestrator.zip"
}

resource "aws_lambda_function" "orchestrator" {
  function_name    = "${local.prefix}-orchestrator"
  description      = "Multi-agent orchestrator (routing + direct response)"
  role             = aws_iam_role.orchestrator_lambda.arn
  runtime          = "python3.12"
  handler          = "handler.handler"
  filename         = data.archive_file.orchestrator.output_path
  source_code_hash = data.archive_file.orchestrator.output_base64sha256
  timeout          = var.orchestrator_timeout_seconds
  memory_size      = var.lambda_memory_mb
  layers           = [aws_lambda_layer_version.shared.arn]

  environment {
    variables = {
      AWS_REGION           = var.aws_region
      ANTHROPIC_SECRET_ARN = aws_secretsmanager_secret.anthropic.arn
      WHATSAPP_SECRET_ARN  = aws_secretsmanager_secret.whatsapp.arn
      DYNAMODB_TABLE       = aws_dynamodb_table.conversations.name
      LAMBDA_SECRETARY     = "${local.prefix}-agent-secretary"
      LAMBDA_DEVELOPER     = "${local.prefix}-agent-developer"
      LAMBDA_DEVOPS        = "${local.prefix}-agent-devops"
      LAMBDA_FRONTEND      = "${local.prefix}-agent-frontend"
      LAMBDA_SECURITY      = "${local.prefix}-agent-security"
      LAMBDA_SOCIAL_MEDIA  = "${local.prefix}-agent-social-media"
    }
  }
}

resource "aws_lambda_event_source_mapping" "orchestrator_sqs" {
  event_source_arn = aws_sqs_queue.orchestrator.arn
  function_name    = aws_lambda_function.orchestrator.arn
  batch_size       = 1
  enabled          = true
}

# ── Specialized Agent Lambdas ──────────────────────────────────────
locals {
  agents = {
    secretary    = { handler = "handler.handler", dir = "secretary" }
    developer    = { handler = "handler.handler", dir = "developer" }
    devops       = { handler = "handler.handler", dir = "devops" }
    frontend     = { handler = "handler.handler", dir = "frontend" }
    security     = { handler = "handler.handler", dir = "security" }
    social-media = { handler = "handler.handler", dir = "social_media" }
  }
}

data "archive_file" "agents" {
  for_each    = local.agents
  type        = "zip"
  source_dir  = "${path.root}/../../lambdas/agents/${each.value.dir}"
  output_path = "${path.root}/../../dist/agent-${each.key}.zip"
}

resource "aws_lambda_function" "agents" {
  for_each = local.agents

  function_name    = "${local.prefix}-agent-${each.key}"
  description      = "Jarvis specialized agent: ${each.key}"
  role             = aws_iam_role.agent_lambda.arn
  runtime          = "python3.12"
  handler          = each.value.handler
  filename         = data.archive_file.agents[each.key].output_path
  source_code_hash = data.archive_file.agents[each.key].output_base64sha256
  timeout          = var.lambda_timeout_seconds
  memory_size      = var.lambda_memory_mb
  layers           = [aws_lambda_layer_version.shared.arn]

  environment {
    variables = {
      AWS_REGION           = var.aws_region
      ANTHROPIC_SECRET_ARN = aws_secretsmanager_secret.anthropic.arn
    }
  }
}

# ── CloudWatch Log Groups ──────────────────────────────────────────
resource "aws_cloudwatch_log_group" "webhook" {
  name              = "/aws/lambda/${aws_lambda_function.webhook.function_name}"
  retention_in_days = 14
}

resource "aws_cloudwatch_log_group" "orchestrator" {
  name              = "/aws/lambda/${aws_lambda_function.orchestrator.function_name}"
  retention_in_days = 30
}

resource "aws_cloudwatch_log_group" "agents" {
  for_each          = local.agents
  name              = "/aws/lambda/${local.prefix}-agent-${each.key}"
  retention_in_days = 14
}
