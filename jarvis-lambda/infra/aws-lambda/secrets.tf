resource "aws_secretsmanager_secret" "anthropic" {
  name                    = "${local.prefix}/anthropic"
  description             = "Anthropic API credentials for Jarvis agents"
  recovery_window_in_days = 7
}

resource "aws_secretsmanager_secret_version" "anthropic" {
  secret_id = aws_secretsmanager_secret.anthropic.id
  secret_string = jsonencode({
    api_key = var.anthropic_api_key
  })
}

resource "aws_secretsmanager_secret" "whatsapp" {
  name                    = "${local.prefix}/whatsapp"
  description             = "Meta WhatsApp Cloud API credentials"
  recovery_window_in_days = 7
}

resource "aws_secretsmanager_secret_version" "whatsapp" {
  secret_id = aws_secretsmanager_secret.whatsapp.id
  secret_string = jsonencode({
    access_token    = var.whatsapp_access_token
    phone_number_id = var.whatsapp_phone_number_id
    verify_token    = var.whatsapp_verify_token
  })
}
