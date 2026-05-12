#!/usr/bin/env bash
# foundry-destroy.sh — Destruye TODA la infraestructura Azure AI Foundry de Jarvis
#   Incluye: OpenAI, modelos, Storage, Key Vault (+ purga soft-delete), Hub, Project.
#   NO afecta al terraform de la VM (infra/azure/).
set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*" >&2; exit 1; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$SCRIPT_DIR/../foundry"

command -v terraform >/dev/null || error "Terraform no instalado."
command -v az        >/dev/null || error "Azure CLI (az) no instalado."

cd "$INFRA_DIR"

[[ -f terraform.tfvars ]] || error "No existe terraform.tfvars en infra/foundry/. No hay infraestructura que destruir."

# ── Leer valores del estado Terraform ─────────────────────────────────────────
RESOURCE_GROUP=$(terraform output -raw resource_group     2>/dev/null || echo "jarvis-foundry-rg")
KV_NAME=$(terraform output -raw key_vault_name            2>/dev/null || echo "")
OAI_NAME=$(terraform output -raw openai_account_name      2>/dev/null || echo "")

# Obtener la location desde el estado de Terraform
LOCATION=$(terraform show -json 2>/dev/null \
  | python3 -c "
import sys, json
cfg = json.load(sys.stdin)
resources = cfg.get('values', {}).get('root_module', {}).get('resources', [])
locs = [r['values']['location'] for r in resources if r.get('type') == 'azurerm_resource_group']
print(locs[0] if locs else 'westeurope')
" 2>/dev/null || echo "westeurope")

warn "══════════════════════════════════════════════════════"
warn "  ⚠️  ATENCIÓN: Se destruirá TODA la infra de Foundry"
warn "  - Resource Group : $RESOURCE_GROUP"
warn "  - Azure OpenAI   : ${OAI_NAME:-<ver estado>}  (+ todos los deployments)"
warn "  - AI Foundry Hub + Project"
warn "  - Storage Account"
warn "  - Key Vault      : ${KV_NAME:-<ver estado>}  (+ purga soft-delete)"
warn "  Esta operación NO afecta a la VM de Jarvis (infra/azure/)."
warn "══════════════════════════════════════════════════════"
echo ""
warn "Escribe 'destroy' para confirmar:"
read -r CONFIRM
[[ "$CONFIRM" == "destroy" ]] || { info "Operación cancelada."; exit 0; }

# ── 1. Destruir con Terraform ─────────────────────────────────────────────────
info "Destruyendo infraestructura con Terraform..."
terraform destroy -auto-approve

# ── 2. Purgar Key Vault (soft-delete 7 días si no se purga explícitamente) ────
# El provider tiene purge_soft_delete_on_destroy=true, pero por seguridad
# lo forzamos vía CLI también para que el nombre quede libre inmediatamente.
if [[ -n "$KV_NAME" ]]; then
  info "Purgando Key Vault '$KV_NAME' en '$LOCATION'..."
  if az keyvault show --name "$KV_NAME" --resource-group "$RESOURCE_GROUP" &>/dev/null; then
    warn "El KV aún existe activo; eliminando antes de purgar..."
    az keyvault delete --name "$KV_NAME" --resource-group "$RESOURCE_GROUP"
  fi
  if az keyvault purge --name "$KV_NAME" --location "$LOCATION" 2>/dev/null; then
    info "✓ Key Vault purgado."
  else
    warn "El Key Vault ya fue purgado por Terraform o no estaba en soft-delete."
  fi
else
  warn "No se pudo determinar el nombre del Key Vault desde el estado; omitiendo purga manual."
fi

# ── 3. Purgar Azure OpenAI (también tiene soft-delete) ───────────────────────
if [[ -n "$OAI_NAME" ]]; then
  info "Verificando purga de Azure OpenAI '$OAI_NAME'..."
  # El provider con purge_soft_delete_on_destroy=true debería haberlo purgado ya.
  # Si por algún motivo quedó en soft-delete, lo purgamos manualmente.
  if az cognitiveservices account show --name "$OAI_NAME" --resource-group "$RESOURCE_GROUP" &>/dev/null; then
    warn "El OpenAI aún existe; eliminando..."
    az cognitiveservices account delete --name "$OAI_NAME" --resource-group "$RESOURCE_GROUP" -y 2>/dev/null || true
  fi
  if az cognitiveservices account purge --name "$OAI_NAME" --resource-group "$RESOURCE_GROUP" --location "$(az cognitiveservices account list-deleted --query "[?name=='$OAI_NAME'].location | [0]" -o tsv 2>/dev/null || echo '')" 2>/dev/null; then
    info "✓ Azure OpenAI purgado."
  else
    info "Azure OpenAI ya purgado o no estaba en soft-delete."
  fi
fi

# ── 4. Limpiar archivos de estado locales ────────────────────────────────────
if [[ -f "$INFRA_DIR/terraform.tfstate" ]]; then
  info "Eliminando terraform.tfstate local..."
  rm -f "$INFRA_DIR/terraform.tfstate" "$INFRA_DIR/terraform.tfstate.backup"
fi

if [[ -f "$INFRA_DIR/tfplan" ]]; then
  rm -f "$INFRA_DIR/tfplan"
fi

info "✓ Infraestructura Azure AI Foundry destruida y limpiada completamente."

