# 0004. Use the YAML plugin as an optional dependency

- Status: Proposed
- Date: 2026-10-23

## Context

The change for issue [#70](https://github.com/artptr/moon-explorer/issues/70) adds run icons in the gutter of moon configuration files.
The IDE shows run icons from a `RunLineMarkerContributor`. A plugin registers each contributor for one language.
The IDE gives each contributor the PSI elements of a file. The IDE attaches each icon to one leaf element, for example a task key.

Thus the plugin must have a PSI tree with one element for each task key.
The YAML plugin (`org.jetbrains.plugins.yaml`, language ID `yaml`) makes this tree for `moon.yml` and for the files in `.moon/tasks/`.
The YAML plugin is not a part of the platform module `com.intellij.modules.platform`.
It is a bundled plugin. IntelliJ IDEA 2025.3 and WebStorm contain it. The user can disable it.

If the YAML plugin is disabled, the IDE shows a `.yml` file as plain text.
The PSI of a plain text file has only one leaf element. This element contains all the text of the file.

Other plugins attach run icons to the configuration files of other tools:

- Nx Console attaches run icons to `project.json` with `<runLineMarkerContributor language="JSON">`. It has a necessary dependency on `com.intellij.modules.json`.
- GitHub Workflow and Maestro Assistant attach run icons to YAML files. They have a necessary dependency on `org.jetbrains.plugins.yaml`.
- AWS Toolkit has an optional dependency on `org.jetbrains.plugins.yaml` with the file `ext-yaml.xml`. This file registers only the YAML functions.

moon can also read configuration files in JSON, JSONC, TOML, HCL, and Pkl. Refer to `MoonWorkspaceLocator.CONFIG_FILE_EXTENSIONS`.
Each format has a different language plugin. HCL and Pkl have plugins from third parties only.

These conditions apply:

1. The main functions of the plugin are the tool window and the run configuration. They do not use YAML (principle 4 in [vision.md](../product/vision.md)).
2. If a user disables the YAML plugin, the tool window and the run configuration must operate.
3. Rule 9 in `AGENTS.md` says that each new dependency must have a record in an issue or an ADR.
4. A later issue can add run icons for a different configuration format.

## Options

### Option A: Necessary dependency on the YAML plugin

Add `<depends>org.jetbrains.plugins.yaml</depends>` to `plugin.xml`.

- Good: This is the simplest configuration. All code can use the YAML PSI.
- Bad: If the user disables the YAML plugin, the IDE also disables Moon Explorer. The user cannot use the tool window and the run configuration.
- Bad: An IDE without the YAML plugin cannot load Moon Explorer.

### Option B: Optional dependency on the YAML plugin

Add `<depends optional="true" config-file="moon-explorer-yaml.xml">org.jetbrains.plugins.yaml</depends>` to `plugin.xml`.
The descriptor file `moon-explorer-yaml.xml` registers the YAML extensions. The IDE loads this file only when the YAML plugin is active.

- Good: The tool window and the run configuration operate without the YAML plugin.
- Good: A later issue can add run icons for JSON or TOML files in the same way. Each format gets one more optional descriptor file.
- Bad: The plugin has two descriptor files.
- Bad: The code outside the package `yaml` must not use YAML classes. If it uses a YAML class, a `NoClassDefFoundError` occurs only when the YAML plugin is disabled.

### Option C: Gutter icons from the editor markup model, without the YAML plugin

Find the task keys with a text scan. Add gutter icons with a `RangeHighlighter` and a `GutterIconRenderer` for each open editor.

- Good: The plugin has no new dependency.
- Bad: The plugin must parse YAML itself, for example anchors, aliases, and flow mappings.
- Bad: The icons are not a part of the IDE run context. The context menu of the editor does not show the **Run** item.
- Bad: The plugin must add and remove the highlighters for each editor and for each change of the document.

## Decision

Use option B. Moon Explorer has an optional dependency on `org.jetbrains.plugins.yaml`.

Obey these rules:

1. Register all extensions that use the YAML PSI in `src/main/resources/META-INF/moon-explorer-yaml.xml`. Do not register them in `plugin.xml`.
2. Put the classes that use YAML classes in the package `io.github.artptr.moonexplorer.yaml`. The other packages must not import `org.jetbrains.yaml`.
3. Put the logic that does not use YAML classes in other packages. Examples: the search for a task in the loaded data, the target IDs (`project:task`), and the run actions.
4. Add the YAML plugin to the build with `bundledPlugin("org.jetbrains.plugins.yaml")` in `build.gradle.kts`.

The package `yaml` contains a `RunLineMarkerContributor` and a `RunConfigurationProducer` for the language `yaml`.
The producer lets the IDE show the **Run** item in the context menu of a task key.

## Consequences

- When the YAML plugin is active, the IDE shows run icons in `moon.yml` and in the files in `.moon/tasks/`.
- When the YAML plugin is disabled, the IDE shows no run icons in these files. All other functions operate.
- The IDE shows no run icons in configuration files in JSON, JSONC, TOML, HCL, or Pkl. A later issue can add an optional descriptor file for each format.
- The tests always load the YAML plugin. Thus no test finds a `NoClassDefFoundError` from a YAML class outside the package `yaml`. A code review must find this error.
- The command `./gradlew verifyPlugin` must not find problems in the optional dependency.
