#!/usr/bin/env bash
# Deploy Jarvis Lambda multi-agent system to AWS
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
INFRA_DIR="$ROOT_DIR/infra/aws-lambda"
DIST_DIR="$ROOT_DIR/dist"

print_step() { echo -e "\n\033[1;34m==> $1\033[0m"; }
print_ok()   { echo -e "\033[1;32m✓ $1\033[0m"; }
print_err()  { echo -e "\033[1;31m✗ $1\033[0m"; exit 1; }

# ── Prerequisites ──────────────────────────────────────────────────
print_step "Checking prerequisites"
command -v python3 &>/dev/null || print_err "python3 is required"
command -v pip    &>/dev/null || print_err "pip is required"
command -v terraform &>/dev/null || print_err "terraform is required"
command -v aws   &>/dev/null || print_err "aws CLI is required"
print_ok "Prerequisites OK"

# ── Build shared layer ─────────────────────────────────────────────
print_step "Installing Python dependencies"
mkdir -p "$DIST_DIR"
LAYER_DIR="$DIST_DIR/layer/python"
rm -rf "$LAYER_DIR"
mkdir -p "$LAYER_DIR"

pip install anthropic boto3 --target "$LAYER_DIR" -q
cp -r "$ROOT_DIR/shared" "$LAYER_DIR/"
cp -r "$ROOT_DIR/lambdas" "$LAYER_DIR/"
print_ok "Dependencies installed"

# ── Package layer ──────────────────────────────────────────────────
print_step "Packaging shared layer"
(cd "$DIST_DIR/layer" && zip -r "$DIST_DIR/shared-layer.zip" python -q)
print_ok "Layer packaged: $DIST_DIR/shared-layer.zip"

# ── Terraform init & apply ─────────────────────────────────────────
print_step "Initializing Terraform"
cd "$INFRA_DIR"
terraform init -upgrade

print_step "Planning Terraform changes"
terraform plan \
  -var="anthropic_api_key=${ANTHROPIC_API_KEY:?Set ANTHROPIC_API_KEY}" \
  -var="whatsapp_access_token=${WHATSAPP_ACCESS_TOKEN:?Set WHATSAPP_ACCESS_TOKEN}" \
  -var="whatsapp_phone_number_id=${WHATSAPP_PHONE_NUMBER_ID:?Set WHATSAPP_PHONE_NUMBER_ID}" \
  -var="whatsapp_verify_token=${WHATSAPP_VERIFY_TOKEN:?Set WHATSAPP_VERIFY_TOKEN}" \
  -out=tfplan

print_step "Applying Terraform changes"
terraform apply tfplan

WEBHOOK_URL=$(terraform output -raw webhook_url)
print_ok "Deployment complete!"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  WhatsApp Webhook URL:"
echo "  $WEBHOOK_URL"
echo ""
echo "  Register this URL in Meta Business Manager:"
echo "  Developers > Your App > WhatsApp > Configuration > Webhook"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
