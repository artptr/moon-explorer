# Architecture

## 1. Platform

| Item | Value |
|------|-------|
| Plugin ID | `io.github.artptr.moonexplorer` |
| Language | Kotlin |
| Build tool | Gradle, with the IntelliJ Platform Gradle Plugin 2.x |
| Target platform | IntelliJ Platform 2025.3 and later |
| JDK | 21 |
| Platform dependency | `com.intellij.modules.platform` (all IntelliJ-based IDEs) |

## 2. Current structure

The plugin now contains only a placeholder tool window.

| Component | Class | Registration |
|-----------|-------|--------------|
| Tool window **Moon** | `toolWindow.MoonToolWindowFactory` | `plugin.xml` |
| Resource bundle | `MoonExplorerBundle` | `plugin.xml` |

## 3. Planned layers

The plan has three layers. Each layer uses only the layer below it.

1. **UI layer** (`ui`): tool windows, actions, and run configurations.
2. **Model layer** (`model`): data classes for the workspace, projects, and tasks. A project service keeps the current model.
3. **CLI layer** (`cli`): starts the `moon` executable and reads its JSON output.

The model layer does not use Swing. The CLI layer does not use the IntelliJ PSI.

**NOTE:** This structure is a proposal. Record each change to it in an ADR in [adr/](adr/).

## 4. Threading

All calls to the moon CLI run on a background thread in a coroutine.
The UI layer updates Swing components on the EDT only.
Refer to the skill `intellij-plugin-dev` for the full rules.
