#!/bin/bash
# user-data.sh — Bootstrap de la instancia EC2 (Amazon Linux 2023)
# Variables inyectadas por Terraform templatefile: ${repo_url}, ${anthropic_api_key}, etc.
set -euo pipefail
exec > >(tee /var/log/jarvis-setup.log) 2>&1

echo "=== [1/5] Actualizando sistema ==="
yum update -y

echo "=== [2/5] Instalando Docker y Git ==="
yum install -y docker git
systemctl enable --now docker
usermod -aG docker ec2-user

echo "=== [3/5] Instalando Docker Compose v2 ==="
mkdir -p /usr/local/lib/docker/cli-plugins
curl -fsSL "https://github.com/docker/compose/releases/download/v2.27.0/docker-compose-linux-x86_64" \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
ln -sf /usr/local/lib/docker/cli-plugins/docker-compose /usr/local/bin/docker-compose

echo "=== [4/5] Clonando repositorio ==="
git clone "${repo_url}" /opt/jarvis
cd /opt/jarvis

echo "=== [4b/5] Creando fichero .env ==="
cat > /opt/jarvis/.env << 'ENVEOF'
ANTHROPIC_API_KEY=${anthropic_api_key}
JARVIS_DEFAULT_PROVIDER=${jarvis_default_provider}
AZURE_OPENAI_API_KEY=${azure_openai_api_key}
AZURE_OPENAI_ENDPOINT=${azure_openai_endpoint}
AZURE_ORCHESTRATOR_DEPLOYMENT=${azure_orchestrator_deployment}
AZURE_AGENT_DEPLOYMENT=${azure_agent_deployment}
TELEGRAM_BOT_TOKEN=${telegram_bot_token}
TELEGRAM_WEBHOOK_SECRET=${telegram_webhook_secret}
WHATSAPP_ACCESS_TOKEN=${whatsapp_access_token}
WHATSAPP_PHONE_NUMBER_ID=${whatsapp_phone_number_id}
WHATSAPP_VERIFY_TOKEN=${whatsapp_verify_token}
ENVEOF
chmod 600 /opt/jarvis/.env

echo "=== [5/5] Levantando servicios con Docker Compose ==="
cd /opt/jarvis
docker compose up -d --build

echo "=== Setup completado ==="
PUBLIC_IP=$(curl -s http://169.254.169.254/latest/meta-data/public-ipv4 || echo "desconocida")
echo "App disponible en: http://$PUBLIC_IP"
