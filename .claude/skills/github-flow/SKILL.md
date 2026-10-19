---
name: github-flow
description: GitHub conventions for Moon Explorer - issues, labels, branches, commits, pull requests, milestones, and the project board. Use when you create or change issues or pull requests, or when you prepare a branch for review.
---

# GitHub workflow

The repository is `artptr/moon-explorer`. Use the GitHub CLI (`gh`) for GitHub operations.

## Issues

1. Use the issue templates in `.github/ISSUE_TEMPLATE/`.
2. Write the issue text in STE. Use the skill `ste-writing`.
3. Add one `type/*` label and one or more `area/*` labels.
4. Add the issue to the project board **Moon Explorer**.

Example:

```bash
gh issue create --title "Show moon projects in the tool window" --label "type/feature,area/ui" --project "Moon Explorer"
```

## Labels

The file `.github/labels.yml` defines all labels. The workflow `Labels` applies the file to GitHub.
To add a label, change `.github/labels.yml`. Do not create labels in the GitHub web interface.

| Prefix | Meaning |
|--------|---------|
| `type/*` | Kind of work: `feature`, `bug`, `docs`, `chore`, `refactor` |
| `area/*` | Part of the product: `ui`, `cli`, `run`, `settings`, `build`, `ai-harness` |
| `status/*` | State outside the board: `blocked`, `needs-info`, `needs-design` |
| `priority/*` | `high`, `medium`, `low` |

## Project board

The board has these columns: **Backlog**, **Ready**, **In progress**, **In review**, **Done**.

1. A new issue goes to **Backlog**.
2. A maintainer moves an issue to **Ready** when the scope is clear.
3. Move the issue to **In progress** when you start the work.
4. GitHub moves the issue to **Done** when the pull request merges.

## Branches and commits

1. Make the branch from `main`: `<type>/<issue-number>-<short-description>`.
2. Use Conventional Commits. Types: `feat`, `fix`, `docs`, `chore`, `refactor`, `test`, `ci`, `build`.
3. Write the commit subject in STE, in the imperative, with a maximum of 72 characters.

## Pull requests

1. Use the pull request template.
2. Put `Closes #<issue-number>` in the text.
3. Make sure that the `Build` and `Docs` workflows pass. If the pull request changes only documentation, the `Build` workflow does not start. Then only `Docs` must pass.
4. Add a `CHANGELOG.md` entry under `Unreleased` for each change that a user can see.

```bash
gh pr create --fill --base main
gh pr checks --watch
```

## Releases

Only a maintainer makes releases.
The `Build` workflow creates a draft release on each push to `main`, except a push that changes only documentation.
When the maintainer publishes the draft, the `Release` workflow publishes the plugin to JetBrains Marketplace.
