locals {
  ssh_private_key = replace(var.ssh_public_key_path, ".pub", "")
}

output "public_ip" {
  description = "IP pública de la VM"
  value       = azurerm_public_ip.jarvis.ip_address
}

output "app_url" {
  description = "URL de la aplicación web (UI)"
  value       = "http://${azurerm_public_ip.jarvis.ip_address}"
}

output "ssh_command" {
  description = "Comando SSH para conectarte a la VM"
  value       = "ssh -i ${local.ssh_private_key} ${var.admin_username}@${azurerm_public_ip.jarvis.ip_address}"
}

output "resource_group" {
  description = "Resource Group creado"
  value       = azurerm_resource_group.jarvis.name
}

output "register_telegram_webhook" {
  description = "Comando para registrar el webhook de Telegram tras el despliegue"
  value       = "curl 'https://api.telegram.org/bot<TOKEN>/setWebhook?url=http://${azurerm_public_ip.jarvis.ip_address}/webhook/telegram&secret_token=<SECRET>'"
}
