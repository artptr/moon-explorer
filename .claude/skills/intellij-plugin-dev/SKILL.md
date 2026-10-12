---
name: intellij-plugin-dev
description: Conventions for IntelliJ Platform plugin development in Moon Explorer. Use when you write or change Kotlin plugin code, plugin.xml, resource bundles, or tests, or when you run the plugin in a sandbox IDE.
---

# IntelliJ Platform plugin development

## Reference documentation

- IntelliJ Platform SDK: https://plugins.jetbrains.com/docs/intellij/welcome.html
- IntelliJ Platform Gradle Plugin 2.x: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
- Threading model: https://plugins.jetbrains.com/docs/intellij/threading-model.html
- moon CLI: https://moonrepo.dev/docs/commands
- Nx Console (reference product): https://github.com/nrwl/nx-console

Read the applicable page before you use an API that you do not know.

## Build configuration

- `settings.gradle.kts` contains the plugin versions for Gradle.
- `build.gradle.kts` contains the target IDE (`intellijIdea(...)`) and the test framework.
- `gradle.properties` contains the plugin version and the group.
- The `since-build` value comes from the target IDE. Do not set it manually without an ADR.

## Extension types

| Need | Extension point or API | Registration |
|------|------------------------|--------------|
| A panel in the IDE | `com.intellij.toolWindow` (`ToolWindowFactory`) | `plugin.xml` |
| A menu or toolbar command | `AnAction` | `<actions>` in `plugin.xml` |
| State for each project | Light service (`@Service(Service.Level.PROJECT)`) | Annotation only |
| Work at project open | `com.intellij.postStartupActivity` (`ProjectActivity`) | `plugin.xml` |
| Settings page | `com.intellij.projectConfigurable` | `plugin.xml` |
| Run configuration for a moon task | `com.intellij.configurationType` | `plugin.xml` |
| Notifications | `com.intellij.notificationGroup` | `plugin.xml` |

## Threading rules

1. Do not call the moon CLI on the EDT. Use a coroutine on `Dispatchers.IO` in a service scope.
2. Get the service scope through constructor injection: `class MoonService(private val project: Project, private val cs: CoroutineScope)`.
3. Read the PSI or the virtual file system only in a read action (`readAction { }`).
4. Change the PSI or the virtual file system only in a write action on the EDT.
5. Update Swing components only on the EDT (`withContext(Dispatchers.EDT) { }`).
6. Implement `DumbAware` if the code does not use indexes.

## moon CLI integration

- Get project and task data from the moon CLI JSON output. Example: `moon query projects --json`.
- Use `GeneralCommandLine` and `CapturingProcessHandler` to start processes.
- Set the working directory to the moon workspace root (the directory that contains `.moon/`).
- If the `moon` executable is not found, show a notification. Do not show an exception.

## Tests

1. Put tests in `src/test/kotlin/io/github/artptr/moonexplorer/`.
2. Extend `BasePlatformTestCase` for tests that need the IDE.
3. Put test data in `src/test/testData/`.
4. Do not start the real moon CLI in unit tests. Use recorded JSON output in `src/test/testData/`.
5. Run `./gradlew test`.

## Manual test

1. Run `./gradlew runIde`.
2. In the sandbox IDE, open a moon workspace.
3. Open the **Moon** tool window.

## Before you commit

1. Run `./gradlew build`.
2. If you changed `plugin.xml` or the target IDE, run `./gradlew verifyPlugin`.
