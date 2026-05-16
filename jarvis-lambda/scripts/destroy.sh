#!/usr/bin/env bash
# Destroy all Jarvis Lambda infrastructure from AWS
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$(cd "$SCRIPT_DIR/../infra/aws-lambda" && pwd)"

echo -e "\033[1;31mWARNING: This will destroy ALL Jarvis Lambda infrastructure!\033[0m"
read -rp "Type 'yes' to confirm: " confirm
[[ "$confirm" == "yes" ]] || { echo "Aborted."; exit 0; }

cd "$INFRA_DIR"
terraform destroy \
  -var="anthropic_api_key=${ANTHROPIC_API_KEY:-placeholder}" \
  -var="whatsapp_access_token=${WHATSAPP_ACCESS_TOKEN:-placeholder}" \
  -var="whatsapp_phone_number_id=${WHATSAPP_PHONE_NUMBER_ID:-placeholder}" \
  -var="whatsapp_verify_token=${WHATSAPP_VERIFY_TOKEN:-placeholder}"
