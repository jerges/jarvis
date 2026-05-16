output "webhook_url" {
  description = "WhatsApp webhook URL — register this in Meta Business Manager"
  value       = "${aws_apigatewayv2_stage.default.invoke_url}/webhook/whatsapp"
}

output "orchestrator_queue_url" {
  description = "SQS queue URL for the orchestrator"
  value       = aws_sqs_queue.orchestrator.url
}

output "dynamodb_table_name" {
  description = "DynamoDB table for conversation history"
  value       = aws_dynamodb_table.conversations.name
}

output "anthropic_secret_arn" {
  description = "Secrets Manager ARN for Anthropic API key"
  value       = aws_secretsmanager_secret.anthropic.arn
}

output "whatsapp_secret_arn" {
  description = "Secrets Manager ARN for WhatsApp credentials"
  value       = aws_secretsmanager_secret.whatsapp.arn
}

output "agent_lambda_names" {
  description = "Lambda function names for all specialized agents"
  value       = { for k, v in aws_lambda_function.agents : k => v.function_name }
}
