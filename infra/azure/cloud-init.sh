#!/bin/bash
# cloud-init.sh — Bootstrap de la VM Azure (Ubuntu 24.04 LTS)
# Variables sustituidas por Terraform templatefile antes de ejecutarse
set -euo pipefail
exec > >(tee /var/log/jarvis-setup.log) 2>&1

if [[ "$(uname -s)" != "Linux" ]]; then
  echo "Este script está pensado para ejecutarse dentro de la VM Linux (cloud-init), no en local." >&2
  exit 1
fi

install_prerequisites() {
  if command -v apt-get >/dev/null 2>&1; then
    echo "=== [1/5] Actualizando sistema (apt) ==="
    apt-get update -y
    apt-get install -y ca-certificates curl gnupg git
    return
  fi

  if command -v dnf >/dev/null 2>&1; then
    echo "=== [1/5] Actualizando sistema (dnf) ==="
    dnf -y update
    dnf -y install ca-certificates curl git docker
    return
  fi

  if command -v yum >/dev/null 2>&1; then
    echo "=== [1/5] Actualizando sistema (yum) ==="
    yum -y update
    yum -y install ca-certificates curl git docker
    return
  fi

  echo "No se encontró un gestor de paquetes soportado (apt-get/dnf/yum)." >&2
  exit 1
}

install_docker() {
  if command -v apt-get >/dev/null 2>&1; then
    echo "=== [2/5] Instalando Docker CE desde repositorio oficial ==="
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
    chmod a+r /etc/apt/keyrings/docker.asc

    ARCH=$(dpkg --print-architecture)
    source /etc/os-release
    echo "deb [arch=$${ARCH} signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $${VERSION_CODENAME} stable" \
      > /etc/apt/sources.list.d/docker.list

    apt-get update -y
    apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
  elif command -v dnf >/dev/null 2>&1 || command -v yum >/dev/null 2>&1; then
    echo "=== [2/5] Docker instalado desde repositorio del sistema ==="
  fi

  systemctl enable --now docker
  usermod -aG docker ${admin_username}
}

install_prerequisites
install_docker

echo "=== [3/5] Verificando instalación ==="
docker --version
docker compose version

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
OLLAMA_ORCHESTRATOR_MODEL=${ollama_orchestrator_model}
OLLAMA_AGENT_MODEL=${ollama_agent_model}
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
echo "App disponible en: http://$(curl -s ifconfig.me || echo 'IP desconocida')"
