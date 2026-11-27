# .claude

Project configuration for Claude Code. Everything here is committed except `settings.local.json`.

| Path | Purpose |
|---|---|
| `CLAUDE.md` | Project instructions loaded every session. Rules, not design discussion. |
| `settings.json` | Shared project settings (permissions, hooks, env). Claude Code reads it only from this exact path, so it cannot live in a subfolder. |
| `settings.local.json` | Personal overrides. Not committed. |
| `specs/` | Design documents the code refers to by name: `BACKEND-SPEC.md` (code comments cite its sections) and `KNOWLEDGE-PLATFORM-NORTH-STAR.md`. Versioned feature specs go here too. |
| `skills/<name>/SKILL.md` | Project-specific skills. |

Add `agents/` (subagents) and `commands/` (slash commands) the same way if they are ever needed.
