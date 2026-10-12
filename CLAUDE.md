@AGENTS.md

## Claude Code

This section gives instructions that apply to Claude Code only.
The instructions in `AGENTS.md` apply first.

### Skills

| Skill | Use it when you |
|-------|-----------------|
| `ste-writing` | Write or change documentation, comments, commit messages, or pull request text |
| `intellij-plugin-dev` | Write or change plugin code, `plugin.xml`, or tests |
| `github-flow` | Create issues, branches, or pull requests, or change labels and the project board |

### Subagents

| Subagent | Use it to |
|----------|-----------|
| `ste-reviewer` | Do a review of changed documentation for STE compliance before you commit |

### Personal settings

Put personal settings in `.claude/settings.local.json` and personal notes in `CLAUDE.local.md`.
Git ignores these files.
