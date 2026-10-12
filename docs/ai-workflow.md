# AI workflow

This project uses AI coding agents to help with development.
This document tells how people and agents work together.

## 1. Harness structure

| File or directory | Function |
|-------------------|----------|
| `AGENTS.md` | Main instructions for all agents and contributors. This is the source of truth. |
| `CLAUDE.md` | Imports `AGENTS.md`. Adds instructions for Claude Code only. |
| `.claude/settings.json` | Shared permissions for Claude Code |
| `.claude/skills/` | Procedures that an agent loads when it needs them |
| `.claude/agents/` | Subagents with a special function |
| `docs/` | Technical documentation. Agents read it for context. |
| `docs/adr/` | Decisions and their reasons |

Other agents (for example Codex or Cursor) read `AGENTS.md` directly.

## 2. Skills and subagents

| Name | Kind | Function |
|------|------|----------|
| `ste-writing` | Skill | Rules and procedure to write in STE |
| `intellij-plugin-dev` | Skill | IntelliJ Platform rules for this project |
| `github-flow` | Skill | Issue, branch, and pull request rules |
| `ste-reviewer` | Subagent | Review of documentation for STE compliance |

## 3. Work procedure

1. A person writes or approves an issue. The issue gives the scope and the acceptance criteria.
2. The agent reads the issue and the applicable documents.
3. The agent makes a plan. For large changes, a person approves the plan.
4. The agent makes the change on a branch. It adds tests and documentation.
5. The agent runs `./gradlew build` and `vale .`.
6. The agent opens a pull request.
7. A person does a review of the pull request and merges it.

## 4. Rules

1. A person is responsible for each merged change.
2. Agents do not publish releases.
3. Agents do not change the release workflow or secrets.
4. When an agent finds a rule that is missing or wrong, it proposes a change to `AGENTS.md` in a pull request.

## 5. Keep the harness up to date

1. Keep `AGENTS.md` short. Put long procedures in skills.
2. When a decision changes the architecture, add an ADR.
3. When a skill has wrong information, correct it in the same pull request as the code change.

## 6. Bootstrap exception

The first commit (`chore: initialize Moon Explorer project`) went to `main` with no issue and no pull request.
This is the only exception to the GitHub workflow in `AGENTS.md`. Do not use it as precedent.
