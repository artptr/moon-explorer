# 0004. Use the YAML plugin as an optional dependency

- Status: Accepted
- Date: 2026-10-23

## Context

The change for issue [#70](https://github.com/artptr/moon-explorer/issues/70) adds run icons in the gutter of moon configuration files.
The IDE shows run icons from a `RunLineMarkerContributor`. A plugin registers each contributor for one language.
The IDE gives each contributor the PSI elements of a file. The IDE attaches each icon to one leaf element, for example a task key.

Thus the plugin must have a PSI tree with one element for each task key.
The YAML plugin (`org.jetbrains.plugins.yaml`, language ID `yaml`) makes this tree for YAML files.
moon configuration files in YAML have the extension `.yml` or `.yaml`, for example `moon.yml` and the files in `.moon/tasks/`.

The YAML plugin is not a part of the platform module `com.intellij.modules.platform`.
It is a bundled plugin. IntelliJ IDEA 2025.3 and WebStorm contain it. The user can disable it.

If the YAML plugin is disabled, the IDE shows a YAML file as plain text.
The PSI of a plain text file has only one leaf element. This element contains all the text of the file.

Other plugins attach run icons to the configuration files of other tools:

- Nx Console attaches run icons to `project.json` with `<runLineMarkerContributor language="JSON">`. It has a necessary dependency on `com.intellij.modules.json`.
- GitHub Workflow and Maestro Assistant attach run icons to YAML files. They have a necessary dependency on `org.jetbrains.plugins.yaml`.
- AWS Toolkit attaches run icons to the Lambda functions in YAML templates. Its descriptor `plugin-intellij.xml` has a necessary dependency on `org.jetbrains.plugins.yaml`.

AWS Toolkit also uses the mechanism of option B for a different function.
Its main descriptor has an optional dependency on `org.jetbrains.plugins.yaml` with the file `ext-yaml.xml`.
This file registers only an index of CloudFormation templates. It does not register run icons.

Sources (examined on 2026-10-23):

- Nx Console 1.61.1: [`plugin.xml`](https://github.com/nrwl/nx-console/blob/master/apps/intellij/src/main/resources/META-INF/plugin.xml)
- GitHub Workflow 2026.6.26: [`plugin.xml`](https://github.com/YunaBraska/github-workflow-plugin/blob/main/src/main/resources/META-INF/plugin.xml)
- Maestro Assistant 1.0.1: [`plugin.xml`](https://github.com/Trendyol/maestro-assistant/blob/main/src/main/resources/META-INF/plugin.xml)
- AWS Toolkit: [`plugin.xml`](https://github.com/aws/aws-toolkit-jetbrains/blob/main/plugins/toolkit/jetbrains-core/resources/META-INF/plugin.xml), [`plugin-intellij.xml`](https://github.com/aws/aws-toolkit-jetbrains/blob/main/plugins/toolkit/jetbrains-core/resources/META-INF/plugin-intellij.xml), [`ext-yaml.xml`](https://github.com/aws/aws-toolkit-jetbrains/blob/main/plugins/toolkit/jetbrains-core/resources/META-INF/ext-yaml.xml)
- The YAML plugin in IntelliJ IDEA 2025.3.6.1: the file `plugins/yaml/lib/yaml.jar`

moon can also read configuration files in JSON, JSONC, TOML, HCL, and Pkl. Refer to `MoonWorkspaceLocator.CONFIG_FILE_EXTENSIONS`.
JSON and JSONC use the same plugin. Each other format has a different language plugin. HCL and Pkl have plugins from third parties only.

These conditions apply:

1. Principle 4 in [vision.md](../product/vision.md) says that the plugin uses only the IntelliJ Platform. Thus the YAML plugin must not be a necessary dependency.
2. The tool window and the run configuration do not use YAML. If a user disables the YAML plugin, these functions must operate.
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
5. Add a test that reads the Kotlin source files. The test fails if a file outside the package `yaml` imports `org.jetbrains.yaml`.

The package `yaml` contains a `RunLineMarkerContributor` and a `RunConfigurationProducer` for the language `yaml`.
The producer lets the IDE show the **Run** item in the context menu of a task key.

## Consequences

- When the YAML plugin is active, the IDE shows run icons in the YAML configuration files of moon. These are `moon.yml`, `moon.yaml`, and the YAML files in `.moon/tasks/`.
- When the YAML plugin is disabled, the IDE shows no run icons in these files. All other functions operate.
- The IDE shows no run icons in configuration files in JSON, JSONC, TOML, HCL, or Pkl. A later issue can add an optional descriptor file for each format.
- The tests always load the YAML plugin. Thus no test finds a `NoClassDefFoundError` at runtime. The import test from rule 5 finds the cause of this error before the merge.
- The command `./gradlew verifyPlugin` must not find problems in the optional dependency.
