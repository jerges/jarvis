# Local Anthropic OAuth helper

Use `run-local-anthropic-oauth.sh` to resolve an Anthropic token in local tests with a token sourced from Claude CLI (or any custom command).

## Token resolution order

1. `ANTHROPIC_API_KEY`
2. `ANTHROPIC_OAUTH_TOKEN`
3. `ANTHROPIC_OAUTH_TOKEN_FILE`
4. `CLAUDE_OAUTH_TOKEN_COMMAND`
5. Auto-detection attempts using `claude` command

## Quick start

```bash
chmod +x ./scripts/run-local-anthropic-oauth.sh
CLAUDE_OAUTH_TOKEN_COMMAND="claude auth print-token" ./scripts/run-local-anthropic-oauth.sh
```

The token is printed to `stdout`, so you can export it directly:

```bash
export ANTHROPIC_API_KEY="$(CLAUDE_OAUTH_TOKEN_COMMAND='claude auth print-token' ./scripts/run-local-anthropic-oauth.sh)"
```

## Start Jarvis manually after resolving the token

```bash
export ANTHROPIC_API_KEY="$(CLAUDE_OAUTH_TOKEN_COMMAND='claude auth print-token' ./scripts/run-local-anthropic-oauth.sh)"
java -jar ./target/jarvis-0.0.1-SNAPSHOT.jar
```

## Run with token from file

```bash
ANTHROPIC_OAUTH_TOKEN_FILE="$HOME/.config/claude/token" \
./scripts/run-local-anthropic-oauth.sh
```

## Notes

- The script prints the resolved token to `stdout`.
- Informational messages are written to `stderr` so command substitution works cleanly.
- If `claude auth status` reports `authMethod: "claude.ai"`, the helper may not be able to extract a reusable API token, because that login is valid for Claude Code but not necessarily exportable as an Anthropic API key for Spring AI.
- This is intended for local development/testing.
- For production, prefer secret management and key rotation (Key Vault, etc.).

