#!/usr/bin/env bash
# azure-update.sh — Actualiza Jarvis en Azure (git pull + docker compose up --build)
set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*" >&2; exit 1; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$SCRIPT_DIR/../azure"

command -v terraform >/dev/null || error "Terraform no instalado."
command -v ssh       >/dev/null || error "ssh no disponible."
cd "$INFRA_DIR"

[[ -f terraform.tfvars ]] || error "No existe terraform.tfvars. Despliega primero con azure-deploy.sh."

info "Obteniendo IP pública desde estado de Terraform..."
PUBLIC_IP=$(terraform output -raw public_ip 2>/dev/null) \
  || error "No se pudo leer la IP. ¿Está desplegada la infraestructura?"

ADMIN_USER=$(grep -E '^admin_username' terraform.tfvars | awk -F'"' '{print $2}' || echo "azureuser")

if [[ -n "${SSH_KEY_PATH:-}" ]]; then
  KEY="$SSH_KEY_PATH"
else
  KEY=$(terraform output -raw ssh_command 2>/dev/null | grep -oP '(?<=-i )\S+' || echo "$HOME/.ssh/id_rsa")
fi

info "Actualizando Jarvis en $PUBLIC_IP (usuario: $ADMIN_USER)..."
ssh -i "$KEY" \
    -o StrictHostKeyChecking=no \
    -o ConnectTimeout=15 \
    "$ADMIN_USER@$PUBLIC_IP" \
    'set -e
     cd /opt/jarvis
     echo "[1/3] Pulling latest code..."
     git pull
     echo "[2/3] Rebuilding and restarting containers..."
     docker compose up -d --build --remove-orphans
     echo "[3/3] Container status:"
     docker compose ps'

echo ""
info "✓ Actualización completada."
info "App: http://$PUBLIC_IP"
