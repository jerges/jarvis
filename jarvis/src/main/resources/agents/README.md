# Agent Prompt Catalog

This directory stores prompt assets per agent, similar to a folder-based GitHub prompt structure.

## Structure

- `orchestrator/routing.md` -> routing instruction used by `OrchestratorAgent`
- `<agent>/base.md` -> base system prompt
- `<agent>/instructions.md` -> execution instructions
- `<agent>/skills.md` -> domain skills and boundaries
- `<agent>/mcp.md` -> MCP tooling usage notes

Supported agents:

- `orchestrator`
- `secretary`
- `developer`
- `devops`
- `frontend`
- `social-media`
- `security`

## Runtime override

By default, prompts are loaded from classpath (`classpath:agents`).

You can point to an external folder by setting:

`JARVIS_AGENTS_PROMPTS_ROOT=/absolute/path/to/agents`

The same folder layout is expected.

