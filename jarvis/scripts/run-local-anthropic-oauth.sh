#!/usr/bin/env bash
set -euo pipefail

# Resolves an Anthropic token sourced from local OAuth/CLI and prints it to stdout.
# Precedence:
#   1) ANTHROPIC_API_KEY env var
#   2) ANTHROPIC_OAUTH_TOKEN env var
#   3) ANTHROPIC_OAUTH_TOKEN_FILE (path to a file containing the token)
#   4) CLAUDE_OAUTH_TOKEN_COMMAND (command that prints the token to stdout)
#   5) Best-effort auto-detection from local claude CLI commands

is_valid_token() {
  local candidate="${1:-}"
  [[ -n "$candidate" && ! "$candidate" =~ [[:space:]] ]]
}

get_claude_auth_status() {
  if command -v claude >/dev/null 2>&1; then
    claude auth status 2>/dev/null || true
  fi
}

if [[ -n "${ANTHROPIC_API_KEY:-}" ]]; then
  TOKEN="${ANTHROPIC_API_KEY}"
elif [[ -n "${ANTHROPIC_OAUTH_TOKEN:-}" ]]; then
  TOKEN="${ANTHROPIC_OAUTH_TOKEN}"
else
  TOKEN=""
fi

if [[ -z "$TOKEN" && -n "${ANTHROPIC_OAUTH_TOKEN_FILE:-}" && -f "${ANTHROPIC_OAUTH_TOKEN_FILE}" ]]; then
  TOKEN="$(tr -d '\r' < "${ANTHROPIC_OAUTH_TOKEN_FILE}" | head -n 1 | sed 's/[[:space:]]*$//')"
fi

if [[ -z "$TOKEN" && -n "${CLAUDE_OAUTH_TOKEN_COMMAND:-}" ]]; then
  candidate="$(eval "${CLAUDE_OAUTH_TOKEN_COMMAND}" 2>/dev/null | head -n 1 | sed 's/[[:space:]]*$//')"
  if is_valid_token "$candidate"; then
    TOKEN="$candidate"
  fi
fi

if [[ -z "$TOKEN" ]] && command -v claude >/dev/null 2>&1; then
  CANDIDATE_COMMANDS=(
    "claude auth print-token"
    "claude auth token"
    "claude token"
  )

  for cmd in "${CANDIDATE_COMMANDS[@]}"; do
    set +e
    candidate="$(eval "$cmd" 2>/dev/null | head -n 1 | sed 's/[[:space:]]*$//')"
    rc=$?
    set -e
    if [[ $rc -eq 0 ]] && is_valid_token "$candidate"; then
      TOKEN="$candidate"
      break
    fi
  done
fi

if ! is_valid_token "$TOKEN"; then
  CLAUDE_STATUS="$(get_claude_auth_status)"
  if [[ -n "$CLAUDE_STATUS" ]] && echo "$CLAUDE_STATUS" | grep -q '"loggedIn": true'; then
    if echo "$CLAUDE_STATUS" | grep -q '"authMethod": "claude.ai"'; then
      echo "[ERROR] Claude Code is logged in with claude.ai OAuth, but that session does not expose a reusable Anthropic API token." >&2
      echo "Your current Claude auth status is valid for Claude Code itself, but Spring AI expects an API key/token value." >&2
      echo "Options:" >&2
      echo "  1) Set ANTHROPIC_API_KEY manually" >&2
      echo "  2) Set ANTHROPIC_OAUTH_TOKEN manually if you already have one" >&2
      echo "  3) Use a custom CLAUDE_OAUTH_TOKEN_COMMAND that prints a real token" >&2
      echo "  4) If you want Anthropic Console billing auth in Claude Code, re-login with: claude auth login --console" >&2
      exit 1
    fi
  fi

  echo "[ERROR] Could not resolve Anthropic token." >&2
  echo "Set one of these inputs:" >&2
  echo "  - ANTHROPIC_API_KEY" >&2
  echo "  - ANTHROPIC_OAUTH_TOKEN" >&2
  echo "  - ANTHROPIC_OAUTH_TOKEN_FILE" >&2
  echo "  - CLAUDE_OAUTH_TOKEN_COMMAND" >&2
  exit 1
fi

masked_prefix="${TOKEN:0:8}"

echo "[INFO] Anthropic token resolved (prefix: ${masked_prefix}...)" >&2
echo "$TOKEN"

