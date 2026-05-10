#!/usr/bin/env bash
# aws-destroy.sh — Destruye TODA la infraestructura AWS de Jarvis
set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*" >&2; exit 1; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$SCRIPT_DIR/../aws"

command -v terraform >/dev/null || error "Terraform no instalado."
cd "$INFRA_DIR"

[[ -f terraform.tfvars ]] || error "No existe terraform.tfvars. No hay infraestructura que destruir."

warn "=========================================="
warn "  ATENCIÓN: Se destruirá TODA la infra AWS"
warn "  - Instancia EC2"
warn "  - Elastic IP (se perderá la IP)"
warn "  - VPC, subnets, security groups"
warn "=========================================="
echo ""
warn "Escribe 'destroy' para confirmar:"
read -r CONFIRM
[[ "$CONFIRM" == "destroy" ]] || { info "Operación cancelada."; exit 0; }

info "Destruyendo infraestructura..."
terraform destroy -auto-approve

info "✓ Infraestructura AWS destruida."
