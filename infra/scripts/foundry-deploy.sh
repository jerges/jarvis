#!/usr/bin/env bash
# foundry-deploy.sh — Despliega la infraestructura Azure AI Foundry de Jarvis
#   Crea: Resource Group, Azure OpenAI + modelos, Storage, Key Vault, AI Hub, AI Project
#   Estado independiente del terraform de la VM (infra/azure/).
set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
info()    { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*" >&2; exit 1; }
section() { echo -e "\n${CYAN}══ $* ══${NC}"; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$SCRIPT_DIR/../foundry"

# ── Prerequisitos ─────────────────────────────────────────────────────────────
command -v terraform >/dev/null || error "Terraform no instalado. Ver https://developer.hashicorp.com/terraform/install"
command -v az        >/dev/null || error "Azure CLI no instalado. Ver https://learn.microsoft.com/cli/azure/install-azure-cli"

# ── Verificar login Azure ─────────────────────────────────────────────────────
az account show --output none 2>/dev/null \
  || error "No estás logueado en Azure. Ejecuta: az login"

info "Suscripción activa: $(az account show --query name -o tsv)"

cd "$INFRA_DIR"

# ── Preparar terraform.tfvars ─────────────────────────────────────────────────
if [[ ! -f terraform.tfvars ]]; then
  warn "No existe terraform.tfvars. Copiando el ejemplo..."
  cp terraform.tfvars.example terraform.tfvars
  warn "Revisa infra/foundry/terraform.tfvars y vuelve a ejecutar si necesitas cambiar algo."
fi

section "Inicializando Terraform (descarga provider azurerm >= 4.3)"
terraform init -upgrade

section "Validando configuración"
terraform validate

section "Calculando cambios"
terraform plan -out=tfplan

echo ""
warn "¿Continuar con el despliegue de Foundry? (yes/no)"
read -r CONFIRM
[[ "$CONFIRM" == "yes" ]] || { info "Despliegue cancelado."; exit 0; }

section "Aplicando infraestructura"
terraform apply tfplan

echo ""
section "Outputs"
terraform output

# ── Mostrar snippet .env ──────────────────────────────────────────────────────
echo ""
info "Snippet listo para copiar en tu .env de Jarvis:"
echo "────────────────────────────────────────────────────"
terraform output -raw env_snippet 2>/dev/null || true
echo ""
echo "────────────────────────────────────────────────────"
echo ""
info "✓ Azure AI Foundry desplegado correctamente."
info "  Portal: https://ai.azure.com"
info "  Conecta el Azure OpenAI al Hub desde:"
info "  ai.azure.com → jarvis-foundry-hub → Settings → Connections → + New"

