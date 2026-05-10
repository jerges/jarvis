locals {
  # Deriva la ruta a la clave privada quitando el .pub
  ssh_private_key = replace(var.ssh_public_key_path, ".pub", "")
}

output "instance_id" {
  description = "ID de la instancia EC2"
  value       = aws_instance.jarvis.id
}

output "public_ip" {
  description = "IP pública elástica (fija aunque reinicies la instancia)"
  value       = aws_eip.jarvis.public_ip
}

output "app_url" {
  description = "URL de la aplicación web (UI)"
  value       = "http://${aws_eip.jarvis.public_ip}"
}

output "ssh_command" {
  description = "Comando SSH para conectarte a la instancia"
  value       = "ssh -i ${local.ssh_private_key} ec2-user@${aws_eip.jarvis.public_ip}"
}

output "register_telegram_webhook" {
  description = "Comando para registrar el webhook de Telegram tras el despliegue"
  value       = "curl 'https://api.telegram.org/bot<TOKEN>/setWebhook?url=http://${aws_eip.jarvis.public_ip}/webhook/telegram&secret_token=<SECRET>'"
}
