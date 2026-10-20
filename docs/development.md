# Development

## 1. Prerequisites

- Git
- JDK 17 or later to start Gradle. Gradle gets JDK 21 for the build automatically.
- IntelliJ IDEA (recommended for development)
- [Vale](https://vale.sh) for documentation checks
- [GitHub CLI](https://cli.github.com) (`gh`) for issues and pull requests
- [moon](https://moonrepo.dev/docs/install) for manual tests
- [Node.js](https://nodejs.org) and npm to record the test data again. Refer to section 4.1.

## 2. Build and test

1. Clone the repository.
2. Open a terminal in the root directory of the repository.
3. Run `./gradlew build`.

The `build` task compiles the code and runs the tests.

To run only the tests, use `./gradlew test`.

## 3. Run the plugin in a sandbox IDE

The plugin finds `moon` in `node_modules/.bin/` of the workspace, in the proto directories, or in `PATH`. Refer to [architecture.md](architecture.md), section 4.
Before you start the sandbox IDE, make sure that this `moon` is moon 2.2.3 or later.

1. Run `./gradlew runIde`.
2. In the sandbox IDE, open a moon workspace.
3. Open the **Moon** tool window.

To open the test workspace in the sandbox IDE, do these steps:

1. Run `./gradlew runIde`.
2. In the sandbox IDE, open the directory `src/test/testData/workspace/`.
3. Open the **Moon** tool window.

### 3.1 Run the plugin in a local WebStorm

The task `runWebStorm` starts the plugin in a WebStorm that is installed on your computer.
The default path is `~/Applications/WebStorm.app`. To use a different path, set the Gradle property `webStormPath`.

To use the moon version of the test workspace, do these steps:

1. In `src/test/testData/workspace/`, run `npm ci`.
2. In the root directory of the repository, run this command:

   ```bash
   ./gradlew runWebStorm --args="$PWD/src/test/testData/workspace"
   ```

3. Open the **Moon** tool window.

The plugin finds `node_modules/.bin/moon` in the test workspace. Thus it is not necessary to add it to `PATH`.

## 4. Test data

The directory `src/test/testData/` contains the test data.
The test data contains only sample data. Do not copy data from other workspaces.

| Path | Content |
|------|---------|
| `workspace/` | A moon v2 workspace with sample projects and tasks |
| `moon/` | The recorded output of the moon CLI for the test workspace |
| `moon/moon-version.txt` | The version of moon that the script used to record the output |

The unit tests read the recorded output. They do not start the moon CLI.

The test workspace contains these items:

- Application projects and library projects. Each of these projects has the `layer` value `application` or `library`, and the `stack` value `frontend` or `backend`.
- The project `scripts` has no `stack` value and no `layer` value. moon gives the value `unknown` for `stack` and for `layer`.
- Tags. Some tags are on more than one project.
- Three files in `.moon/tasks/`. All projects inherit `all.yml`. Frontend projects inherit `frontend.yml`. Backend projects inherit `backend.yml`.
- The project `web` changes the inherited task `lint`.
- Tasks with a description and tasks without a description
- The project `ui` has the internal task `generate-icons`. moon does not write internal tasks in the key `tasks` in the output of `moon query projects`.
- The project `config` has no tasks.
- The project `icons` in `packages/ui/icons` is a nested project in the project `ui`. The key `sources` in `.moon/workspace.yml` adds it.
- `build` tasks with the optional output `dist`. The commands do not make output files. Thus `moon run` completes for each task.

**NOTE:** To add a nested project, put the project ID and the path in the map `sources`. If `projects` is a list and you add a glob or a path to it, moon also adds the directory `packages` as a project.

**NOTE:** In `moon/query-projects.json`, the project `config` has no key `tasks`. moon does not write this key for a project without tasks.

### 4.1 Record the output again

Record the output again after you change the test workspace.
The file `workspace/package.json` sets the moon version. Node.js and npm are necessary.

1. Run `scripts/record-test-data.sh`.
2. Run `./gradlew test`.
3. Commit the changes in `src/test/testData/`.

The script installs the moon version that `package.json` gives. Then it writes the files in `moon/`.
The output of `moon query projects` contains the absolute path of each project. The script replaces the path of the workspace with `/workspace`.
The script stops with an error if a recorded file contains the path of the repository on your computer.

### 4.2 Test data for a large workspace

Do not commit large test files that a tool makes. Make them during the test.
For tests with a large workspace, use the test function `LargeQueryProjectsJson.generate()`.
The function copies the recorded projects and tasks and gives them new IDs.
The default output has 17 projects with 14 tasks each.

## 5. Do a check of compatibility

Run `./gradlew verifyPlugin`.
The IntelliJ Plugin Verifier does a check of the plugin against the recommended IDE versions.

For a quick check, run `./gradlew verifyPlugin -PquickVerification=true`.
The quick check uses two IDEs only: the IDE of the build (`platformVersion` in `gradle.properties`) and the newest released IDE.
In the quick check, the Plugin Verifier operates in offline mode. It does not download the optional dependencies of the bundled plugins.
The plugin does not use these dependencies.

The `Build` workflow does the quick check for a pull request. A push to `main` does the check against all recommended IDE versions.

The workflow keeps the two IDEs of the quick check in the IDE cache.
The cache key contains a version number of the cache, the versions of the two IDEs, and data about the GitHub runner.
The runner data is the operating system and the CPU architecture.
Only a push to `main` saves the IDE cache. All pull requests can use the IDE cache from `main`.

`platformVersion` must be a fixed version number, for example `2025.3.6.1` or the Early Access Program (EAP) build `261.12345.67`.
Do not use a snapshot version, for example `261-EAP-SNAPSHOT`.
The IDE of a snapshot version can change, but the cache key does not change. Then the IDE cache contains an old IDE.
If `platformVersion` is not a fixed version number, the `Build` workflow stops with an error.

## 6. Do a check of the documentation

Run `vale .`.
Refer to [ste/guide.md](ste/guide.md).

## 7. Continuous integration

GitHub Actions runs these workflows:

| Workflow | Trigger | Jobs |
|----------|---------|------|
| `Build` | Push to `main`, pull request. A change to documentation only does not start the workflow. | Build, test, verification of the plugin, draft release |
| `Docs` | Push to `main`, pull request | STE check with Vale |
| `Labels` | Change to `.github/labels.yml` on `main` | Sync of the GitHub labels |
| `Release` | Publication of a GitHub release | Publication to JetBrains Marketplace |

**NOTE:** The `paths` filter in `build.yml` gives the list of files that do not start the workflow. If `Build` becomes a status check that must pass before a merge, a pull request that changes only documentation cannot merge. GitHub does not start the workflow, thus the status stays pending. In that case, replace the filter with a job that always runs and gives the status.

Obey these rules when you add or change a workflow:

1. Add a `permissions` block at the top of the workflow. Give `contents: read` only.
2. If a job writes data, add a `permissions` block to the job. Give only the permissions that the job must have.
3. In each `uses:` line, use the complete commit SHA of the action. Put the version tag in a comment after the SHA. Example: `actions/checkout@<sha> # v7.0.1`.

Dependabot updates the SHA and the comment together.

## 8. Release

1. Make sure that `CHANGELOG.md` has entries under `Unreleased`.
2. Set the new version in `gradle.properties`.
3. Merge the change into `main`.
4. On GitHub, open the draft release that the `Build` workflow created.
5. Publish the draft release.

The `Release` workflow signs the plugin and publishes it to JetBrains Marketplace.
These repository secrets are necessary: `PUBLISH_TOKEN`, `CERTIFICATE_CHAIN`, `PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD`.
Refer to <https://plugins.jetbrains.com/docs/intellij/plugin-signing.html>.

### 8.1 Version that you uploaded manually

JetBrains Marketplace accepts the first version of a new plugin only as a manual upload.
Marketplace does not accept a version that it has. Thus the `Release` workflow must not publish that version again.
Refer to <https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html>.

1. Set the signing secrets as environment variables in your shell.
2. Run `./gradlew signPlugin verifyPluginSignature`.
3. Upload the file `build/distributions/*-signed.zip` on JetBrains Marketplace.
4. Run `gh variable set SKIP_MARKETPLACE_PUBLISH --body true`.
5. Publish the draft release on GitHub.
6. Make sure that the `Release` workflow is complete.
7. Run `gh variable delete SKIP_MARKETPLACE_PUBLISH`.

When `SKIP_MARKETPLACE_PUBLISH` is `true`, the `Release` workflow signs the plugin, but does not publish it to Marketplace.
The workflow does the other steps as usual.

**CAUTION:** Delete the variable after the release. If you do not delete it, the workflow does not publish the next versions to Marketplace.

## 9. Initial GitHub setup

A maintainer does these steps one time.

1. Create the repository and push the code:

   ```bash
   gh repo create artptr/moon-explorer --public --source . --push
   ```

2. Run the `Labels` workflow once from the **Actions** tab.
3. Create the project board:

   ```bash
   gh project create --owner artptr --title "Moon Explorer"
   ```

4. Link the project board to the repository:

   ```bash
   gh project link <project-number> --owner artptr --repo artptr/moon-explorer
   ```

5. On the project board, set the **Status** field to these values: **Backlog**, **Ready**, **In progress**, **In review**, **Done**.
6. On the project board, turn on the built-in workflows **Item added to project** (status **Backlog**) and **Item closed** (status **Done**).
7. In the repository settings, add a branch rule for `main`. Make the `Docs` check necessary before a merge.
8. To make the `Build` check necessary, first replace the `paths` filter in `build.yml`. Refer to the note in [7. Continuous integration](#7-continuous-integration).
9. In the repository settings, turn on **Discussions**.
