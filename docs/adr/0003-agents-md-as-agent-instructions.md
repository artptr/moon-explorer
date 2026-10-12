# 0003. Use AGENTS.md as the source of agent instructions

- Status: Accepted
- Date: 2026-10-13

## Context

Different AI coding agents read different instruction files.
Claude Code reads `CLAUDE.md`. Many other agents read `AGENTS.md`.
Two copies of the same instructions become different with time.

## Decision

`AGENTS.md` contains all general instructions.
`CLAUDE.md` imports `AGENTS.md` with `@AGENTS.md`. It adds only instructions for Claude Code.
Long procedures go into skills in `.claude/skills/`.

## Consequences

- There is one source of truth for all agents.
- `AGENTS.md` stays short, because skills contain the long procedures.
- Agents other than Claude Code do not load the skills automatically. `AGENTS.md` gives the skill paths to them.
