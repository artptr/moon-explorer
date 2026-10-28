# 0005. Apply the YAML dependency to the file type and the task hints

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
2. Can the new file type use the YAML language? The YAML plugin already has a file type for this language.
3. Do the JSON schemas from SchemaStore continue to operate for the files of the new type?
4. Can a file type mask contain a path, for example `.moon/tasks/**/*.yml`?
5. Which configuration formats do the hints and the gutter icons of issue #100 support?

The team examined the sources on 2026-10-28. For the IntelliJ Platform, the team examined the branch `253`. This branch agrees with IntelliJ IDEA 2025.3, as in ADR 0004.
These are the results.

### The Feature Extractor reads the optional descriptors

The Feature Extractor starts in `CliMain.kt`. It calls `IdePluginManager.createManager().createPlugin()` to load the plugin.
`IdePluginManager` calls `OptionalDependencyResolver.resolveOptionalDependencies()`.
For each `<depends optional="true" config-file="...">`, `OptionalDependencyResolver` loads the descriptor file. Then it calls `mergeContent`.
`mergeContent` adds the extensions of the optional descriptor to `IdePlugin.extensions`.

`FileTypeExtractor` reads the `<fileType>` extensions from `IdePlugin.extensions`. It reads the attributes `extensions`, `fileNames`, `fileNamesCaseInsensitive`, and `patterns`.
Thus the Feature Extractor finds a `<fileType>` in `moon-explorer-yaml.xml`.

### The new file type must be secondary

`YAMLFileType.YML` is the primary file type of `YAMLLanguage`.
A `LanguageFileType` with the constructor parameter `secondary = true` is never the associated file type of its language.
When the IDE loads a `<fileType>`, `FileTypeManagerImpl` compares the attribute `language` with the file type:

- For a primary file type, the attribute must be the language ID, for example `yaml`.
- For a secondary file type, the attribute must not be in the XML.

If the values do not agree, `FileTypeManagerImpl` writes the error "Incorrect language specified in `<fileType>`".
If the new file type is primary, the YAML language has two primary file types. Then the IDE can use the moon file type for all YAML files.

### An exact file name has priority over an extension

`FileTypeAssocTable.findAssociatedFileType()` finds the file type of a file name in this sequence:

1. The exact file names (attribute `fileNames`)
2. The exact file names in uppercase or lowercase letters (attribute `fileNamesCaseInsensitive`)
3. The masks (attribute `patterns`)
4. The extensions (attribute `extensions`)

The YAML plugin registers the extensions `yml` and `yaml`. Thus the file name `moon.yml` in the moon file type has priority over the YAML file type.

### The JSON schemas operate only with the YAML language

The YAML plugin uses the class `YamlJsonEnabler` to find the files for JSON schemas.
This class returns `true` for a `LanguageFileType` with the language `YAMLLanguage`. It also returns `true` for a scratch file with the YAML language.
A secondary file type also has its language. Thus the schemas continue to operate if the new file type uses `YAMLLanguage.INSTANCE`.

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
`FileTypeAssocTable` also gives only the file name to the matchers.
Thus a mask such as `.moon/tasks/**/*.yml` does not agree with a file in the IDE.

The files in `.moon/` have general names, for example `workspace.yml` and `node.yml`.
A mask for only the name, for example `workspace.yml`, also agrees with the files of other tools.

### Other configuration formats

moon also reads configuration files in JSON, JSONC, TOML, HCL, and Pkl. Refer to ADR 0004.
Each format has a different PSI and a different language plugin. JSON and JSONC use the same plugin.
moon writes YAML files when a user creates a workspace. The moon documentation uses YAML in its examples.

Sources (examined on 2026-10-28):

- `intellij-plugin-verifier` at commit `ca7c42d`: [`CliMain.kt`](https://github.com/JetBrains/intellij-plugin-verifier/blob/ca7c42d/intellij-feature-extractor/src/main/java/com/jetbrains/intellij/feature/extractor/CliMain.kt), [`FileTypeExtractor.kt`](https://github.com/JetBrains/intellij-plugin-verifier/blob/ca7c42d/intellij-feature-extractor/src/main/java/com/jetbrains/intellij/feature/extractor/extractor/FileTypeExtractor.kt), [`IdePluginManager.kt`](https://github.com/JetBrains/intellij-plugin-verifier/blob/ca7c42d/intellij-plugin-structure/structure-intellij/src/main/java/com/jetbrains/plugin/structure/intellij/plugin/IdePluginManager.kt), [`OptionalDependencyResolver.kt`](https://github.com/JetBrains/intellij-plugin-verifier/blob/ca7c42d/intellij-plugin-structure/structure-intellij/src/main/java/com/jetbrains/plugin/structure/intellij/plugin/OptionalDependencyResolver.kt), [`PluginCreator.kt`](https://github.com/JetBrains/intellij-plugin-verifier/blob/ca7c42d/intellij-plugin-structure/structure-intellij/src/main/java/com/jetbrains/plugin/structure/intellij/plugin/PluginCreator.kt)
- `intellij-community`, branch `253` at commit `70534e5076`: [`LanguageFileType.java`](https://github.com/JetBrains/intellij-community/blob/70534e5076/platform/core-api/src/com/intellij/openapi/fileTypes/LanguageFileType.java), [`FileTypeManagerImpl.java`](https://github.com/JetBrains/intellij-community/blob/70534e5076/platform/platform-impl/src/com/intellij/openapi/fileTypes/impl/FileTypeManagerImpl.java), [`FileTypeAssocTable.java`](https://github.com/JetBrains/intellij-community/blob/70534e5076/jps/model-impl/src/com/intellij/openapi/fileTypes/impl/FileTypeAssocTable.java), [`WildcardFileNameMatcher.java`](https://github.com/JetBrains/intellij-community/blob/70534e5076/jps/model-impl/src/com/intellij/openapi/fileTypes/WildcardFileNameMatcher.java), [`YamlJsonEnabler.java`](https://github.com/JetBrains/intellij-community/blob/70534e5076/plugins/yaml/backend/src/schema/YamlJsonEnabler.java)
- The SchemaStore catalog: [`catalog.json`](https://www.schemastore.org/api/json/catalog.json)

## Options

These options are about question 5: the formats for the hints and the gutter icons of issue #100.

### Option A: YAML only

The hints and the gutter icons use the YAML PSI. The extensions are in `moon-explorer-yaml.xml`, as in ADR 0004.

- Good: The run icons, the file type, and the hints use one dependency and one descriptor file.
- Good: moon makes YAML files for a new workspace, and its documentation uses YAML. Thus the team thinks that most workspaces use YAML. The team has no data about this.
- Bad: A workspace with configuration files in other formats shows no hints in these files.

### Option B: YAML and JSON

The hints and the gutter icons use the YAML PSI and the JSON PSI. A second optional descriptor registers the JSON extensions.

- Good: Workspaces with `moon.json` or `moon.jsonc` also show the hints.
- Bad: The plugin has two copies of the code that reads the PSI. Each copy must have tests.
- Bad: The run icons do not support JSON. Thus a JSON file shows hints but no run icons.

### Option C: One model for all formats

A converter for each format reads the PSI of a configuration file. It gives one model of the file that does not depend on the format.
The hints and the gutter icons use only this model. Issue #100 adds the converter for YAML only.

- Good: To support one more format later, only a new converter and a small descriptor file are necessary.
- Good: The logic of the hints does not use YAML classes. The YAML PSI is not necessary for its tests.
- Bad: This is the largest change. No issue asks for more than YAML now.
- Bad: The model must contain all the data that the hints use. A change of the hints can also change the model and each converter.

## Decision

The rules of ADR 0004 apply to issues #99 and #100. Keep the YAML plugin as an optional dependency.
For question 5, use option C. With this option, the plugin can support one more format with only a new converter. The logic of the hints does not change.

Obey these rules:

1. Register the file type of issue #99 in `moon-explorer-yaml.xml`. Do not register it in `plugin.xml`.
2. Make the file type a secondary `LanguageFileType` with the language `YAMLLanguage.INSTANCE`. Use the constructor parameter `secondary = true`.
3. Do not put the attribute `language` in the `<fileType>` element. `YAMLFileType.YML` remains the primary file type of YAML.
4. Do not make a new language or a YAML dialect.
5. Put the file type class in the package `io.github.artptr.moonexplorer.yaml`. The class uses `org.jetbrains.yaml`. `YamlImportBoundaryTest` does a check of this rule.
6. Use the name `Moon` for the file type. Do not change this name later, because the IDE keeps it in the settings.
7. Put the description of the file type in `MoonExplorerBundle.properties`.
8. Give the file type the file names `moon.yml` and `moon.yaml` only. Do not add masks for the files in `.moon/` or in `.config/moon/`.
9. Add a test for the file type. The test makes sure that `YAMLLanguage.INSTANCE.associatedFileType` is `YAMLFileType.YML`. It also makes sure that the file type of `moon.yml` is the moon file type.
10. Use option C for issue #100. Add the converter for YAML only.
11. Put the model of a configuration file and the logic of the hints in the package `io.github.artptr.moonexplorer.config`. This package must not import the classes of a language plugin.
12. Define an extension point for the converters in `plugin.xml`. Register the YAML converter in `moon-explorer-yaml.xml`. Put its class in the package `io.github.artptr.moonexplorer.yaml`.
13. Register the Code Vision providers in `plugin.xml`. They get the PSI elements for the hints from the model. Register each line marker provider in the descriptor file of its format. The IDE uses a line marker provider for one language only.
14. Use the absolute path to identify a moon configuration file. The run icons use the same method with `configFilePath` and the loaded data. Do not use the file type to identify a moon configuration file. The file type does not contain the files in `.moon/`.

This is an example of the registration:

```xml
<fileType name="Moon"
          implementationClass="io.github.artptr.moonexplorer.yaml.MoonFileType"
          fileNames="moon.yml;moon.yaml"/>
```

## Consequences

- When the YAML plugin is active, the IDE opens `moon.yml` and `moon.yaml` with the new file type. The JSON schemas from SchemaStore continue to operate.
- Other YAML files keep the YAML file type, because the YAML language keeps its primary file type.
- When the YAML plugin is not active, the IDE has no moon file type. It shows no hints, no gutter icons, and no run icons. The tool window and the run configuration operate.
- JetBrains Marketplace gets the masks `moon.yml` and `moon.yaml`. It does not get masks for the files in `.moon/`. Thus a file in `.moon/` does not cause a plugin suggestion.
- The files in `.moon/` keep the usual YAML file type. The hints, the gutter icons, and the run icons operate in these files, because the plugin identifies these files from the path.
- This decision changes acceptance criterion 2 of issue #99. Only `moon.yml` and `moon.yaml` get the new file type. The files in `.moon/` keep the YAML file type.
- The IDE shows no hints in configuration files in JSON, JSONC, TOML, HCL, or Pkl. A later issue can add a converter for each format. Each format with a converter gets an optional descriptor file, as in ADR 0004.
- Option C makes more work for issue #100 than option A, because issue #100 adds the model and the extension point.
- The run icons of ADR 0004 continue to read the YAML PSI. A later issue can change them to use the model.
- These items of issue #99 remain open. A test in the IDE or a check after the release must answer them:
  - Does the IDE suggest a plugin for a file name mask when the file already has the YAML file type?
  - Does JetBrains Marketplace show `moon.yml` and `moon.yaml` in its index of plugin features after the release?
