# Product vision

## 1. Purpose

Moon Explorer is a plugin for IntelliJ-based IDEs.
It gives a graphical interface for the [moon](https://moonrepo.dev) build system.

Nx Console gives a graphical interface for Nx in VS Code and in IntelliJ IDEs.
Moon Explorer has the same function for moon.

## 2. Users

- Developers who use moon in a workspace and use an IntelliJ-based IDE.
- Developers who are new to a moon workspace and must learn its structure.

## 3. Problems to solve

1. The user must know moon CLI commands to see the projects and tasks in a workspace.
2. The user must leave the IDE to run a moon task.
3. The user cannot see the relations between projects in the IDE.

## 4. Scope

The team will decide the features in a brainstorm session.
Each approved feature will have a GitHub issue with the label `type/feature`.

Possible areas for features:

- A tree of moon projects and tasks in a tool window
- Run configurations for moon tasks
- A view of the project graph
- Support for moon configuration files (`moon.yml`, `.moon/workspace.yml`)
- Settings for the path to the `moon` executable

**NOTE:** This list is not approved. It is a start point for the brainstorm.

## 5. Out of scope

- Changes to moon itself
- Support for build systems other than moon
