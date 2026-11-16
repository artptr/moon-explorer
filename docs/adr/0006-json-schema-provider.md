# 0006. Give JSON schemas for the moon configuration files

- Status: Proposed
- Date: 2026-11-14

## Context

Issue [#78](https://github.com/artptr/moon-explorer/issues/78) found that the SchemaStore schemas do not give a good result for moon. Refer to [vision.md](../product/vision.md), section 11.1:

- The user must click **Apply schema** in each file.
- The files directly in `.moon/tasks/` and `template.yml` get no schema.
- Without a network connection, a file gets a schema only if the schema is in the cache of the IDE.

Issue [#186](https://github.com/artptr/moon-explorer/issues/186) adds a JSON schema provider for the moon configuration files.
The provider must use the local schemas in `.moon/cache/schemas/`. moon writes these files when it syncs the workspace.
If the local schemas are not available, the issue tells the plugin to run `moon sync config-schemas --force`, or to use the moon v2 schema URLs.

The IDE gets a schema provider from the extension point `JavaScript.JsonSchema.ProviderFactory`.
The JSON plugin (`com.intellij.modules.json`) defines this extension point and the interfaces `JsonSchemaProviderFactory` and `JsonSchemaFileProvider`.

The JSON plugin is a bundled plugin. The user can disable it. The YAML plugin has a necessary dependency on the JSON plugin.
The YAML plugin uses the JSON schemas for YAML files. Refer to [ADR 0005](0005-yaml-file-type-and-task-hints.md).

These conditions apply:

1. Principle 4 in [vision.md](../product/vision.md) says that the plugin uses only the IntelliJ Platform. Thus the JSON plugin must not be a necessary dependency.
2. Principle 2 says that the plugin operates without a network connection.
3. Principle 1 says that the moon CLI is the only source of data about the workspace.
   moon finds a template in the directories of the setting `generator.templates`. The default directory is `./templates`. Thus the file name `template.yml` alone does not identify a template.
   Other tools also use this name. For example, AWS SAM uses `template.yaml`.
4. Rule 9 in `AGENTS.md` says that each new dependency must have a record in an issue or an ADR.

The team examined IntelliJ IDEA 2025.3.6.2 on 2026-11-14:

- The JSON schema service keeps the schema of each file in a cache. The method `JsonSchemaService.reset()` removes the cache.
- The command `moon templates --json` gives the absolute path of each template in the key `root`.
- When the workspace is not in the content of the IDE project, the daemon does not show the warnings of the schema inspection. The inspection itself finds the problems.

## Options

### Option A: Local schemas, then `moon sync`, then the moon website

Use the files in `.moon/cache/schemas/`. If a file does not exist, run `moon sync config-schemas --force`. If moon fails, use the URLs in `https://moonrepo.dev/schemas/v2/`.

- Good: The schemas agree with the moon version of the workspace.
- Good: The schemas operate without a network connection when moon is available.
- Bad: The plugin starts a moon command that writes files to `.moon/cache/`. moon also writes these files when it runs a task.
- Bad: If moon fails, the IDE downloads the schemas from the network. The IDE does this only if the user lets the IDE download remote schemas.
  The JSON plugin changed the procedure for the remote schemas of a provider in the 2026.3 release. Rule 10 gives the two procedures.

### Option B: Local schemas, then the moon website

- Good: The plugin does not start a moon command for the schemas.
- Bad: A workspace without `.moon/cache/schemas/` gets its schemas only with a network connection.

### Option C: Schemas in the plugin

- Good: The schemas operate without moon and without a network connection.
- Bad: The schemas do not agree with the moon version of the workspace. Each moon release can change them.

## Decision

Use option A. The plugin has an optional dependency on `com.intellij.modules.json`. The plugin finds the templates with `moon templates --json`.

Obey these rules:

1. Register the schema provider in `src/main/resources/META-INF/moon-explorer-json.xml`. Do not register it in `plugin.xml`.
2. Put the classes that use the classes of the JSON plugin in the package `io.github.artptr.moonexplorer.json`. The test `JsonImportBoundaryTest` does a check of this rule.
3. Put the logic that does not use the JSON plugin in the package `io.github.artptr.moonexplorer.schema`. This logic includes the mapping of the files, the moon commands, and the state.
4. Add the JSON plugin to the build with `bundledPlugin("com.intellij.modules.json")` in `build.gradle.kts`.
5. Give the schemas only to the files of the workspace of the last load. Identify a file from its path, as in rule 14 of ADR 0005:
   - `moon.<ext>` in the workspace root, but outside `.moon/`: the project schema `project.json`
   - `.moon/workspace.<ext>`, `.moon/toolchains.<ext>`, and `.moon/extensions.<ext>`: `workspace.json`, `toolchains.json`, and `extensions.json`
   - All files in `.moon/tasks/` and in its subdirectories: `tasks.json`
   - `template.<ext>` in the root directory of a template from `moon templates --json`: `template.json`. This directory can be outside the workspace root.

   `<ext>` is `yml`, `yaml`, `json`, or `jsonc`. The IDE does not examine TOML, HCL, and Pkl with a JSON schema. The files in `.moon/cache/` of the workspace root get no schema.
6. Examine the schema files for a new workspace root and after each new load. Run `moon sync config-schemas --force` only if a schema file does not exist.
   After a fallback to the moon website, use the local files again when they exist.
7. Run `moon templates --json` after each new load. A new load is a `Loaded` state with a new root or new tasks, or a `Loaded` state after a different state.
   Do not run the command again when the plugin adds the task graph to the state.
8. Let only the last job of each type change the mapping. A moon process can complete after the cancellation of its job.
   Cancel the old job with the lock of the service before you start the next job. A job changes the mapping only if the job is active, with the same lock.
9. After each change of the mapping or of the schema files, call `JsonSchemaService.reset()`. This method also restarts the daemon.
10. For a remote schema, give the URL in `getRemoteSource()`. Select the result of `getSchemaFile()` from the procedure of the JSON plugin. The class `RemoteSchemaPath` does this:
    - The old procedure: give the file from `JsonFileResolver.urlToFile`. Before the JSON plugin reads the file, it calls `JsonFileResolver.startFetchingHttpFileIfNeeded`. If the provider gives no file, the file gets no schema.
    - The new procedure: give no file. Then the JSON plugin downloads the URL of `getRemoteSource()` with `JsonSchemaRemoteContentService`. If the provider gives the file of the URL, the JSON plugin does not download it.

    The 2026.2 builds and earlier builds use the old procedure. The 2026.3 EAP build 263.7031.23 also uses the old procedure. The 2026.3 release, build 263.7307.44, uses the new procedure.
    Thus the branch number 263 does not identify the procedure.

    The old procedure needs `JsonFileResolver.startFetchingHttpFileIfNeeded`.
    Use the old procedure only if `JsonFileResolver` has this function. Otherwise use the new procedure.

    In each procedure, the JSON plugin downloads the schema only if the IDE can download remote schemas.

## Consequences

- When the JSON plugin is active, all moon configuration files of the workspace get a schema without a manual step. The YAML files and the JSON files get the same schemas.
- When the JSON plugin is disabled, the IDE shows no schemas for the moon files. The YAML plugin has a necessary dependency on the JSON plugin. Thus the IDE also disables the YAML plugin, and the functions of ADR 0004 and ADR 0005 stop. The tool window, the run configurations, **Search Everywhere**, and **Run Anything** operate.
- The schemas of `.moon/toolchains.yml` and `.moon/extensions.yml` accept all keys as IDs. Thus only a value with an incorrect type gives a warning in these files.
- The team did not examine a file that has a mapping from **Apply schema** of an earlier IDE session. The user can remove such a mapping in **Settings | Languages & Frameworks | Schemas and DTDs | JSON Schema Mappings**.
- `moon templates` can get templates from a Git repository or from npm, if the workspace configuration has such a location. Then moon uses the network. The plugin does not make this decision. The workspace configuration makes it.
- A new template directory gets the schema after the next load that changes the state, or after the IDE project opens again. A change of `generator.templates` alone does not change the output of `moon query projects`.
- If the user deletes `.moon/cache/schemas/`, the plugin runs `moon sync` after the next load.
- If `moon sync` continues to fail, the plugin runs it after each load. A load runs `moon query projects` successfully. Thus moon is available, and a failure of `moon sync` is not usual.
- If `moon sync` completes but does not write all schema files, the plugin also runs it after each load. For example, a different moon version can write different schema files.
- A template directory outside the workspace root can also be outside the content of the IDE project. The IDE can hide the schema warnings in such a file.
- The URLs in `https://moonrepo.dev/schemas/v2/` are for moon v2. If moon changes these URLs, the plugin must change them.
- The tests use build 253. In these tests, the IDE cannot download remote schemas. Thus the tests do a check of the result of `getSchemaFile()` and of the disabled download. They do not do a check of a download.
  The team did a manual test of the download in the new procedure, with build 263.7307.200 and a fake HTTP transport.
- If a later JSON plugin removes `startFetchingHttpFileIfNeeded`, the plugin uses the new procedure. A unit test compares the procedure with a second signal, the class `JsonSchemaRemoteContentService`, for the IDE of the build.
- The tests add the copy of the workspace to the content of the test project. Without this step, the daemon does not show the schema warnings.
