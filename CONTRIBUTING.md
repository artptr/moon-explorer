# Contributing to Moon Explorer

Thank you for your help. This document tells how to contribute.

## 1. Before you start

1. Read [AGENTS.md](AGENTS.md). It contains the rules for all contributors, people and AI agents.
2. Find an issue, or open a new issue. Tell the maintainer that you want to do the work.

## 2. Make a change

1. Fork the repository. Maintainers can skip this step.
2. Make a branch from `main`. Use the name `<type>/<issue-number>-<short-description>`.
3. Make the change. Add tests.
4. Run `./gradlew build`.
5. If you changed documentation, run `vale .`.
6. Commit the change. Use [Conventional Commits](https://www.conventionalcommits.org).
7. Open a pull request into `main`.

Refer to [docs/development.md](docs/development.md) for the build procedures.

## 3. Documentation language

Write technical documentation in Simplified Technical English (STE).
Refer to [docs/ste/guide.md](docs/ste/guide.md).

## 4. AI-assisted contributions

You can use AI tools to make contributions.
You are responsible for each line that you submit.
Do a review of AI output before you open a pull request.

## 5. License

You agree that your contribution has the [Apache License 2.0](LICENSE).
