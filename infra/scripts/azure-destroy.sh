#!/usr/bin/env bash
# azure-destroy.sh — Destruye TODA la infraestructura Azure de Jarvis
set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*" >&2; exit 1; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$SCRIPT_DIR/../azure"

command -v terraform >/dev/null || error "Terraform no instalado."
cd "$INFRA_DIR"

[[ -f terraform.tfvars ]] || error "No existe terraform.tfvars. No hay infraestructura que destruir."

RESOURCE_GROUP=$(terraform output -raw resource_group 2>/dev/null || echo "jarvis-rg")

warn "================================================="
warn "  ATENCIÓN: Se destruirá TODA la infra Azure"
warn "  - Resource Group: $RESOURCE_GROUP"
warn "  - VM + disco + NIC + IP pública"
warn "  - VNet, subnet, NSG"
warn "================================================="
echo ""
warn "Escribe 'destroy' para confirmar:"
read -r CONFIRM
[[ "$CONFIRM" == "destroy" ]] || { info "Operación cancelada."; exit 0; }

info "Destruyendo infraestructura..."
terraform destroy -auto-approve

info "✓ Infraestructura Azure destruida."
