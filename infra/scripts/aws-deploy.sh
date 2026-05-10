#!/usr/bin/env bash
# aws-deploy.sh — Despliega Jarvis en AWS con Terraform
set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*" >&2; exit 1; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$SCRIPT_DIR/../aws"

# ── Prerequisitos ─────────────────────────────────────────────────────────────
command -v terraform >/dev/null || error "Terraform no está instalado. Instálalo desde https://developer.hashicorp.com/terraform/install"
command -v aws       >/dev/null || error "AWS CLI no está instalado. Ejecuta: pip install awscli"

# ── Verificar credenciales AWS ────────────────────────────────────────────────
aws sts get-caller-identity --output text >/dev/null 2>&1 \
  || error "Credenciales AWS no configuradas. Ejecuta: aws configure"

cd "$INFRA_DIR"

# ── Verificar terraform.tfvars ────────────────────────────────────────────────
if [[ ! -f terraform.tfvars ]]; then
  warn "No existe terraform.tfvars. Creándolo a partir del ejemplo..."
  cp terraform.tfvars.example terraform.tfvars
  error "Rellena infra/aws/terraform.tfvars con tus valores y vuelve a ejecutar este script."
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
info "Revisa el log en la instancia: ssh <ip> 'tail -f /var/log/jarvis-setup.log'"
