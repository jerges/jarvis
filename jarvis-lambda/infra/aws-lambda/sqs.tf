resource "aws_sqs_queue" "orchestrator_dlq" {
  name                      = "${local.prefix}-orchestrator-dlq"
  message_retention_seconds = 86400 * 3  # 3 days
}

resource "aws_sqs_queue" "orchestrator" {
  name                       = "${local.prefix}-orchestrator"
  visibility_timeout_seconds = var.orchestrator_timeout_seconds + 30
  message_retention_seconds  = 3600  # 1 hour

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.orchestrator_dlq.arn
    maxReceiveCount     = 3
  })
}

resource "aws_sqs_queue_policy" "orchestrator" {
  queue_url = aws_sqs_queue.orchestrator.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { AWS = aws_iam_role.webhook_lambda.arn }
      Action    = "sqs:SendMessage"
      Resource  = aws_sqs_queue.orchestrator.arn
    }]
  })
}
