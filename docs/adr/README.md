# Architecture decision records

An architecture decision record (ADR) records one important decision and its reasons.

## Procedure to add an ADR

1. Copy [template.md](template.md) to `NNNN-short-title.md`. Use the next number.
2. Write the ADR in STE.
3. Set the status to `Proposed`.
4. Open a pull request. When the pull request merges, set the status to `Accepted`.

Do not delete an ADR. To change a decision, write a new ADR. Set the status of the old ADR to `Superseded by NNNN`.

## Records

| Number | Title | Status |
|--------|-------|--------|
| [0001](0001-record-architecture-decisions.md) | Record architecture decisions | Accepted |
| [0002](0002-use-ste100-for-technical-docs.md) | Use ASD-STE100 for technical documentation | Accepted |
| [0003](0003-agents-md-as-agent-instructions.md) | Use AGENTS.md as the source of agent instructions | Accepted |
| [0004](0004-yaml-plugin-optional-dependency.md) | Use the YAML plugin as an optional dependency | Proposed |
