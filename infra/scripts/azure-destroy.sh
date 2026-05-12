#!/usr/bin/env bash
# azure-destroy.sh — Destruye TODA la infraestructura Azure de Jarvis
#   Incluye purga del Key Vault (soft-delete) para evitar conflictos en redespliegues.
set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*" >&2; exit 1; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$SCRIPT_DIR/../azure"

command -v terraform >/dev/null || error "Terraform no instalado."
command -v az        >/dev/null || error "Azure CLI (az) no instalado — necesario para purgar el Key Vault."

cd "$INFRA_DIR"

[[ -f terraform.tfvars ]] || error "No existe terraform.tfvars. No hay infraestructura que destruir."

# ── Leer valores del estado Terraform (o caer al valor por defecto en variables.tf) ──
RESOURCE_GROUP=$(terraform output -raw resource_group  2>/dev/null || echo "jarvis-rg")
KEY_VAULT_NAME=$(terraform output -raw key_vault_name  2>/dev/null || echo "jarvis-kv-jb")
LOCATION=$(terraform -chdir="$INFRA_DIR" output -raw public_ip 2>/dev/null \
  && terraform -chdir="$INFRA_DIR" show -json 2>/dev/null \
     | python3 -c "import sys,json; cfg=json.load(sys.stdin); \
         vals=[r['values']['location'] for r in cfg.get('values',{}).get('root_module',{}).get('resources',[]) \
               if r.get('type')=='azurerm_resource_group']; print(vals[0] if vals else 'westeurope')" 2>/dev/null \
  || echo "westeurope")

warn "======================================================"
warn "  ⚠️  ATENCIÓN: Se destruirá TODA la infra Azure"
warn "  - Resource Group : $RESOURCE_GROUP"
warn "  - VM + disco OS + NIC + IP pública"
warn "  - VNet, subnet, NSG"
warn "  - Key Vault      : $KEY_VAULT_NAME  (+ purga soft-delete)"
warn "  - Secretos del Key Vault"
warn "======================================================"
echo ""
warn "Escribe 'destroy' para confirmar:"
read -r CONFIRM
[[ "$CONFIRM" == "destroy" ]] || { info "Operación cancelada."; exit 0; }

# ── 1. Destruir con Terraform ─────────────────────────────────────────────────
info "Destruyendo infraestructura con Terraform..."
terraform destroy -auto-approve

# ── 2. Purgar Key Vault (queda en soft-delete 7 días si no se purga) ──────────
info "Purgando Key Vault '$KEY_VAULT_NAME' (soft-delete) en '$LOCATION'..."
if az keyvault show --name "$KEY_VAULT_NAME" --resource-group "$RESOURCE_GROUP" &>/dev/null; then
  # Todavía existe (raro tras destroy, pero por si acaso)
  warn "El Key Vault aún existe activo; eliminando antes de purgar..."
  az keyvault delete --name "$KEY_VAULT_NAME" --resource-group "$RESOURCE_GROUP"
fi

# Intentar purgar el vault soft-deleted
if az keyvault purge --name "$KEY_VAULT_NAME" --location "$LOCATION" 2>/dev/null; then
  info "✓ Key Vault purgado correctamente."
else
  warn "No se encontró el Key Vault en soft-delete o ya fue purgado anteriormente."
fi

# ── 3. Limpiar archivos de estado locales (opcional) ─────────────────────────
if [[ -f "$INFRA_DIR/terraform.tfstate" ]]; then
  info "Eliminando terraform.tfstate local..."
  rm -f "$INFRA_DIR/terraform.tfstate" "$INFRA_DIR/terraform.tfstate.backup"
fi

info "✓ Infraestructura Azure destruida y limpiada completamente."
