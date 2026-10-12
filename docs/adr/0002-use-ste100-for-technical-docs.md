# 0002. Use ASD-STE100 for technical documentation

- Status: Accepted
- Date: 2026-10-13

## Context

The project is open source. Many contributors do not have English as their first language.
AI agents read and write most of the documentation.
Text with a controlled vocabulary and short sentences is clear for people and for agents.

## Decision

We write technical documentation in ASD-STE100 Simplified Technical English (STE).
`README.md` and third-party texts are exempt. The list of exempt files is in `docs/ste/allowlist.md`.
Vale does an automatic check of some STE rules in CI.

## Consequences

- The documentation is clear and consistent.
- Contributors must learn the STE rules. The skill `ste-writing` and the Vale rules help them.
- Vale cannot find all STE problems. A review is still necessary.
