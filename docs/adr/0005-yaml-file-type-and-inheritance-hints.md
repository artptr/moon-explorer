# 0005. Apply the YAML dependency to the file type and the inheritance hints

- Status: Proposed
- Date: 2026-10-28

## Context

[ADR 0004](0004-yaml-plugin-optional-dependency.md) makes the YAML plugin an optional dependency for the run icons of issue [#70](https://github.com/artptr/moon-explorer/issues/70).
The YAML PSI is necessary for two more issues:

- Issue [#99](https://github.com/artptr/moon-explorer/issues/99) adds a file type for the moon configuration files. JetBrains Marketplace finds the file name masks of this file type. Then the IDE can suggest the plugin.
- Issue [#100](https://github.com/artptr/moon-explorer/issues/100) adds Code Vision hints and gutter icons for the task inheritance and the task dependencies.

Each issue tells the team to record the YAML dependency in an ADR. Issue #100 asks for one ADR for the two issues.
ADR 0004 does not refer to these issues. Also, it does not answer these questions:

1. Can the plugin register the file type in the optional descriptor `moon-explorer-yaml.xml`? If the IntelliJ Feature Extractor does not read this file, JetBrains Marketplace does not get the masks.
2. Do the JSON schemas from SchemaStore continue to operate for the files of the new type?
3. Can a file type mask contain a path, for example `.moon/tasks/**/*.yml`?
4. Which configuration formats do the hints and the gutter icons of issue #100 support?

The team examined the sources on 2026-10-28. These are the results.

### The Feature Extractor reads the optional descriptors

The library `intellij-plugin-structure` loads a plugin for the Feature Extractor.
For each `<depends optional="true" config-file="...">`, `OptionalDependencyResolver` loads the descriptor file. Then it calls `mergeContent`.
`mergeContent` adds the extensions of the optional descriptor to `IdePlugin.extensions`.

`FileTypeExtractor` reads the `<fileType>` extensions from `IdePlugin.extensions`. It reads the attributes `extensions`, `fileNames`, `fileNamesCaseInsensitive`, and `patterns`.
Thus the Feature Extractor finds a `<fileType>` in `moon-explorer-yaml.xml`.

### The JSON schemas operate only with the YAML language

The YAML plugin uses the class `YamlJsonEnabler` to find the files for JSON schemas.
This class returns `true` if the file type is a `LanguageFileType` and its language is a `YAMLLanguage`.
Thus the schemas continue to operate if the new file type uses `YAMLLanguage.INSTANCE`.

SchemaStore has schemas for moon v2. These are the `fileMatch` values for YAML:

| Schema | `fileMatch` for YAML |
|--------|----------------------|
| `moon.yml` | `moon.yml`, `moon.yaml` |
| moon workspace | `**/.moon/workspace.yml`, `**/.config/moon/workspace.yml`, and the same names with `.yaml` |
| moon toolchains | `**/.moon/toolchains.yml`, `**/.config/moon/toolchains.yml`, and the same names with `.yaml` |
| moon extensions | `**/.moon/extensions.yml`, `**/.config/moon/extensions.yml`, and the same names with `.yaml` |
| moon tasks | `**/.moon/tasks/**/*.yml`, `**/.config/moon/tasks/**/*.yml`, and the same names with `.yaml` |

### A file type mask applies to the file name only

The IDE compares a file type mask with `WildcardFileNameMatcher`. This class examines only the file name. It does not examine the path.
Thus a mask such as `.moon/tasks/**/*.yml` does not agree with a file in the IDE.
The files in `.moon/` have general names, for example `workspace.yml` and `node.yml`.
A mask for only the name, for example `workspace.yml`, also agrees with the files of other tools.

### Other configuration formats

moon also reads configuration files in JSON, JSONC, TOML, HCL, and Pkl. Refer to ADR 0004.
Each format has a different PSI and a different language plugin. JSON and JSONC use the same plugin.
moon writes YAML files when a user creates a workspace. The moon documentation uses YAML in its examples.

Sources (examined on 2026-10-28):

- `intellij-plugin-verifier` at commit `ca7c42d`: [`FileTypeExtractor.kt`](https://github.com/JetBrains/intellij-plugin-verifier/blob/ca7c42d/intellij-feature-extractor/src/main/java/com/jetbrains/intellij/feature/extractor/extractor/FileTypeExtractor.kt), [`OptionalDependencyResolver.kt`](https://github.com/JetBrains/intellij-plugin-verifier/blob/ca7c42d/intellij-plugin-structure/structure-intellij/src/main/java/com/jetbrains/plugin/structure/intellij/plugin/OptionalDependencyResolver.kt), [`PluginCreator.kt`](https://github.com/JetBrains/intellij-plugin-verifier/blob/ca7c42d/intellij-plugin-structure/structure-intellij/src/main/java/com/jetbrains/plugin/structure/intellij/plugin/PluginCreator.kt)
- `intellij-community` at commit `e77f7c2f55`: [`YamlJsonEnabler.java`](https://github.com/JetBrains/intellij-community/blob/e77f7c2f55/plugins/yaml/backend/src/schema/YamlJsonEnabler.java), [`WildcardFileNameMatcher.java`](https://github.com/JetBrains/intellij-community/blob/e77f7c2f55/jps/model-impl/src/com/intellij/openapi/fileTypes/WildcardFileNameMatcher.java)
- The SchemaStore catalog: [`catalog.json`](https://www.schemastore.org/api/json/catalog.json)

## Options

These options are about question 4: the formats for the hints and the gutter icons of issue #100.

### Option A: YAML only

The hints and the gutter icons use the YAML PSI. The extensions are in `moon-explorer-yaml.xml`, as in ADR 0004.

- Good: The run icons, the file type, and the hints use one dependency and one descriptor file.
- Good: Most moon workspaces use YAML.
- Bad: A workspace with configuration files in other formats shows no hints in these files.

### Option B: YAML and JSON

The hints and the gutter icons use the YAML PSI and the JSON PSI. A second optional descriptor registers the JSON extensions.

- Good: Workspaces with `moon.json` or `moon.jsonc` also show the hints.
- Bad: The plugin has two copies of the code that reads the PSI. Each copy must have tests.
- Bad: The run icons do not support JSON. Thus a JSON file shows hints but no run icons.

### Option C: One model for all formats

A layer converts the YAML PSI and the JSON PSI to one model of the tasks. The hints use only this model.

- Good: For a later format, only a new converter is necessary.
- Bad: This is the largest change. No issue asks for more than YAML now.

## Decision

The rules of ADR 0004 apply to issues #99 and #100. Keep the YAML plugin as an optional dependency.

Obey these rules:

1. Register the file type of issue #99 in `moon-explorer-yaml.xml`. Do not register it in `plugin.xml`.
2. Make the file type a `LanguageFileType` with the language `YAMLLanguage.INSTANCE`. Do not make a new language or a YAML dialect.
3. Give the file type the file names `moon.yml` and `moon.yaml` only. Do not add masks for the files in `.moon/` or in `.config/moon/`.
4. Register the Code Vision providers and the line marker providers of issue #100 in `moon-explorer-yaml.xml`. Put their classes in the package `io.github.artptr.moonexplorer.yaml`.
5. Use the path to identify a moon configuration file, as `configFilePath` does for the run icons. Do not use the file type. The file type does not include the files in `.moon/`.
6. Use option A for issue #100. The hints and the gutter icons support YAML files only.

## Consequences

- When the YAML plugin is active, the IDE opens `moon.yml` and `moon.yaml` with the new file type. The JSON schemas from SchemaStore continue to operate.
- When the YAML plugin is not active, the IDE has no moon file type. It shows no hints, no gutter icons, and no run icons. The tool window and the run configuration operate.
- JetBrains Marketplace gets the masks `moon.yml` and `moon.yaml`. It does not get masks for the files in `.moon/`. Thus a file in `.moon/` does not cause a plugin suggestion.
- The files in `.moon/` keep the usual YAML file type. The hints, the gutter icons, and the run icons operate in these files, because the extensions identify the files from the path.
- The IDE shows no hints in configuration files in JSON, JSONC, TOML, HCL, or Pkl. A later ADR can select option B or option C.
- These items of issue #99 stay open. A test in the IDE must answer them:
  - Does the IDE suggest a plugin for a file name mask when the file already has the YAML file type?
  - Does JetBrains Marketplace show `moon.yml` and `moon.yaml` in its index of plugin features after the release?
