#!/usr/bin/env bash
# azure-deploy.sh — Despliega Jarvis en Azure con Terraform
set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*" >&2; exit 1; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$SCRIPT_DIR/../azure"

# ── Prerequisitos ─────────────────────────────────────────────────────────────
command -v terraform >/dev/null || error "Terraform no instalado. Ver https://developer.hashicorp.com/terraform/install"
command -v az        >/dev/null || error "Azure CLI no instalado. Ver https://learn.microsoft.com/cli/azure/install-azure-cli"

# ── Verificar login Azure ─────────────────────────────────────────────────────
az account show --output none 2>/dev/null \
  || error "No estás logueado en Azure. Ejecuta: az login"

info "Suscripción activa: $(az account show --query name -o tsv)"

cd "$INFRA_DIR"

# ── Verificar terraform.tfvars ────────────────────────────────────────────────
if [[ ! -f terraform.tfvars ]]; then
  warn "No existe terraform.tfvars. Creándolo a partir del ejemplo..."
  cp terraform.tfvars.example terraform.tfvars
  error "Rellena infra/azure/terraform.tfvars con tus valores y vuelve a ejecutar."
fi

info "Inicializando Terraform..."
terraform init -upgrade

info "Validando configuración..."
terraform validate

info "Calculando cambios (plan)..."
terraform plan -out=tfplan

echo ""
warn "¿Continuar con el despliegue? (yes/no)"
read -r CONFIRM
[[ "$CONFIRM" == "yes" ]] || { info "Despliegue cancelado."; exit 0; }

info "Aplicando infraestructura..."
terraform apply tfplan

echo ""
info "✓ Despliegue completado."
echo ""
terraform output
echo ""
info "Nota: El arranque inicial puede tardar 5-10 min (build Docker incluido)."
info "Revisa el log: ssh <ip> 'tail -f /var/log/jarvis-setup.log'"
