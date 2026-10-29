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
| `gutter-workspace/` | A small workspace for `MoonTaskGutterIntegrationTest`. The test copies it to a temporary directory and gives its own output of `moon query projects`. The script does not record output for this workspace. |

The unit tests read the recorded output. They do not start the moon CLI.

The test workspace contains these items:

- Application projects and library projects. Each of these projects has the `layer` value `application` or `library`, and the `stack` value `frontend` or `backend`.
- The project `scripts` has no `stack` value and no `layer` value. moon gives the value `unknown` for `stack` and for `layer`.
- Tags. Some tags are on more than one project. Some projects have two tags. The projects `icons` and `scripts` have no tags.
- Three files in `.moon/tasks/`. All projects inherit `all.yml`. Frontend projects inherit `frontend.yml`. Backend projects inherit `backend.yml`.
- The project `web` changes the inherited task `lint`.
- The project `scripts` includes only the inherited task `lint`. Thus it does not inherit `format`.
- The project `web` excludes the inherited task `format` and has a local task `format`. It renames the inherited task `typecheck` to `tsc`. moon writes `format` and `typecheck` in `inherited.layers` of `web`, but the plugin must use the `workspace.inheritedTasks` settings of `web` with these entries.
- Tasks with a description and tasks without a description
- The project `ui` has the internal task `generate-icons`. moon does not write internal tasks in the key `tasks` in the output of `moon query projects`.
- The project `config` has no tasks.
- The project `icons` in `packages/ui/icons` is a nested project in the project `ui`. The key `sources` in `.moon/workspace.yml` adds it.
- `build` tasks with the optional output `dist`. The commands do not make output files. Thus `moon run` completes for each task.

**NOTE:** To add a nested project, use the map form of `projects` and put the project ID and the path in `sources`. If you add the nested project to the list form of `projects` or to `projects.globs`, moon also adds the directory `packages` as a project.

**NOTE:** In `moon/query-projects.json`, the project `config` has no key `tasks`. moon does not write this key for a project without tasks.

### 4.1 Record the output again

Record the output again after you change the test workspace.
The file `workspace/package.json` sets the moon version. Node.js and npm are necessary.

1. Run `scripts/record-test-data.sh`.
2. Run `./gradlew test`.
3. Commit the changes in `src/test/testData/`.

The script installs the moon version that `package.json` gives. Then it removes `workspace/.moon/cache/` and writes the files in `moon/`.
A different moon version cannot always read the cache of the previous version.
If a moon command fails, the script stops, and the files in `moon/` do not change.

The output of `moon query projects` contains the absolute path of each project. The script replaces the path of the workspace with `/workspace`.
The script stops with an error if a recorded file contains the path of the repository on your computer.

The test data is an input of the Gradle task `test`. Thus Gradle runs the tests again when the test data changes.
The input is each file in `src/test/testData/` that Git does not ignore. Gradle gets this list from `git ls-files`.
Thus the files that tools make in the test workspace, for example `node_modules/` and `.moon/cache/`, are not inputs.

### 4.2 Test data for a large workspace

Do not commit large test files that a tool makes. Make them during the test.
For tests with a large workspace, use the test function `LargeQueryProjectsJson.generate()`.
The function copies the recorded projects and tasks and gives them new IDs.
The default output has 17 projects with 14 tasks each.

### 4.3 Find changes in the moon output

The tests use the recorded output of one moon version. Thus the tests do not find a change in the output of a newer moon version.
The `Moon output` workflow does this check one time each week. You can also start the workflow manually.

The workflow does these steps:

1. It installs the newest moon 2.x in the test workspace.
2. It runs `scripts/record-test-data.sh`.
3. It runs `./gradlew test`.
4. If the recorded output changes, the record script fails, or the tests fail, it creates an issue.

The workflow finds the issue from a previous run with the label `moon-output`. If this issue is open, the workflow replaces the text of the issue.
If the label does not exist, the workflow creates it before it creates the issue.

The workflow adds a comment only when the moon version changes. Write your notes in comments, not in the issue text.
The issue text shows the two moon versions, the result of each step, and the start of the diff.
If the workflow finds a problem, the artifact `moon-output` of the workflow run contains the recorded output. If the tests ran, the artifact also contains the test reports.

The file `moon-version.txt` changes for each new moon version. If only this file changes, the workflow does not create an issue.
The workflow does not commit or push changes.
Only a run on `main` creates or changes an issue. A manual run on a different branch shows the result only in the job summary.
A cancelled run does not create or change an issue.

**NOTE:** The workflow does not add the issue to the project board. The `GITHUB_TOKEN` cannot change a user project. Add the issue to the board manually.

To start the workflow manually, do these steps:

1. On GitHub, open **Actions** > **Moon output**.
2. Click **Run workflow**.
3. In **moon-version**, type the version of `@moonrepo/cli`. The default value `2` installs the newest moon 2.x.
4. Click **Run workflow**.

To update the test data to a new moon version, do these steps:

1. In `src/test/testData/workspace/`, run `npm install --save-exact @moonrepo/cli@<version>`.
2. Run `scripts/record-test-data.sh`.
3. Run `./gradlew test`.
4. If a test fails, correct the plugin or the test.
5. Commit the changes in `src/test/testData/`, which include `workspace/package.json` and `workspace/package-lock.json`.
6. In the pull request text, write `Closes #<issue-number>` for the issue from the workflow.

The test `recordedWithMoonVersionOfWorkspace` compares `moon/moon-version.txt` with the moon version in `workspace/package.json`.
If you change only `package.json`, this test fails.

**NOTE:** You can use the plugin with moon 2.2.3 and later versions. If you record the output with a newer moon version, no test uses the output of moon 2.2.3.

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
Vale examines the Markdown files and the comments in `.github/workflows/*.yml`.
Vale does not examine the files in `.claude/worktrees/`.
Claude Code makes a git worktree in this directory for each separate session.
Refer to [ste/guide.md](ste/guide.md).

The `Docs` workflow uses the Vale version in `.github/workflows/docs.yml`. Use the same Vale version on your computer.

## 7. Continuous integration

GitHub Actions runs these workflows:

| Workflow | Trigger | Jobs |
|----------|---------|------|
| `Build` | Push to `main`, pull request. A change to documentation only does not start the workflow. | Build, test, verification of the plugin, draft release |
| `Docs` | Push to `main`, pull request | STE check with Vale |
| `Labels` | Change to `.github/labels.yml` on `main` | Sync of the GitHub labels |
| `Moon output` | Each Monday, manual start | Record of the output of the newest moon 2.x, tests, issue for a change. Refer to section 4.3. |
| `Version check` | Each Monday, manual start, pull request that changes `version-check.yml` | Check of the IntelliJ IDEA version and the Vale version, issue for a new version. Refer to section 7.1. |
| `Release` | Publication of a GitHub release | Publication to JetBrains Marketplace, pull request for `CHANGELOG.md` |

**NOTE:** The `paths` filter in `build.yml` gives the list of files that do not start the workflow. If `Build` becomes a status check that must pass before a merge, a pull request that changes only documentation cannot merge. GitHub does not start the workflow, thus the status stays pending. In that case, replace the filter with a job that always runs and gives the status.

The `Build` workflow uses `version` in `gradle.properties` for the draft release.
If a published release has this version, the workflow stops with an error before it changes the draft releases.
Then set `version` to the version of the next release. Refer to section 8.

Obey these rules when you add or change a workflow:

1. Add a `permissions` block at the top of the workflow. Give `contents: read` only.
2. If a job writes data, add a `permissions` block to the job. Give only the permissions that the job must have.
3. In each `uses:` line, use the complete commit SHA of the action. Put the version tag in a comment after the SHA. Example: `actions/checkout@<sha> # v7.0.1`.

Dependabot updates the SHA and the comment together.

### 7.1 Versions that Dependabot does not update

Dependabot updates the Gradle dependencies and the actions. It does not update these versions:

| Version | Location | Reason |
|---------|----------|--------|
| IntelliJ IDEA | `platformVersion` in `gradle.properties` | The IntelliJ Platform Gradle Plugin gets the IDE in a different way from a usual Gradle dependency. |
| Vale | `version` of `errata-ai/vale-action` in `.github/workflows/docs.yml` | Dependabot does not read the inputs of an action. |

Each version is in only one location. Do not write these versions in other files.

The `Version check` workflow finds new versions one time each week. You can also start the workflow manually.

The workflow does these steps:

1. It gets the newest released IDE of each IDE line with the Gradle task `printProductsReleases`. A line is the first two numbers of a version, for example 2025.3.
2. It compares `platformVersion` with the newest IDE in the same line.
3. It finds the lines that are newer than the line of `platformVersion`.
4. It compares the Vale version with the newest Vale release on GitHub.
5. If a new version is available, it creates or updates an issue.

A newer IDE in the same line, for example 2025.3.7, is an update. Change `platformVersion` to this version.
A newer line, for example 2026.1, is only information. A change to a newer line also changes the minimum IDE version of the plugin. Thus a maintainer must decide about this change.

The workflow finds the issue from a previous run with the label `version-check`. If this issue is open, the workflow replaces the text of the issue.
If the label does not exist, the workflow creates it before it creates the issue.
The workflow adds a comment or creates an issue only for a version that the previous issue does not contain. Thus each new version causes one notification.

For a newer line, the workflow compares only the line, not the patch version. Thus a new patch version of a newer line does not cause a notification.
If you close the issue, the workflow does not open a new issue for the same versions. Write your notes in comments, not in the issue text.
If the newest versions are all in the repository, the workflow closes the open issue. A newer IDE line keeps the issue open until you close it.

Only a run on `main` creates or changes an issue. A manual run on a different branch and a run for a pull request show the result only in the job summary.
A new version does not cause a failure of the workflow. Only an error causes a failure.

**NOTE:** The workflow can compare only the version of a released IDE, for example `2025.3.6.1`. If `platformVersion` is an EAP build number, for example `261.12345.67`, the workflow stops with an error.

**NOTE:** The workflow does not add the issue to the project board. The `GITHUB_TOKEN` cannot change a user project. Add the issue to the board manually.

To change the IntelliJ IDEA version, do these steps:

1. In `gradle.properties`, set `platformVersion` to the new version.
2. Run `./gradlew build`.
3. Run `./gradlew verifyPlugin -PquickVerification=true`.

To change the Vale version, do these steps:

1. In `.github/workflows/docs.yml`, set `version` of the step `Run Vale` to the new version.
2. Install the same Vale version on your computer.
3. Run `vale .`.

For each change, open a pull request. In the pull request text, write `Closes #<issue-number>` for the issue from the workflow.

## 8. Release

1. Make sure that `CHANGELOG.md` has entries under `Unreleased`.
2. Make sure that `version` in `gradle.properties` is the version of this release.
3. If you changed `CHANGELOG.md` or `gradle.properties`, merge the change into `main`.
4. Make sure that the `Build` workflow on `main` is complete. Then, on GitHub, open the draft release that the workflow created.
5. Publish the draft release. Do not delete the text of the draft release. The `Release` workflow uses this text to move the entries from `Unreleased` to a section for the version.
6. Make sure that the `Release` workflow is complete. The workflow creates the pull request "Changelog update - `<version>`". Check out the branch of this pull request.
7. In `gradle.properties`, set `version` to the version of the next release. If you do not know it, use the next minor version. Example: after 0.2.0, set `version = 0.3.0`. The `Build` workflow uses this version for the next draft release.
8. Push a commit with this change to the branch.
9. Merge the pull request.

**NOTE:** If the text of the release is empty, the `Release` workflow does not create the changelog pull request. Then change steps 6 and 9:

- Step 6: Make a branch from the release tag `<version>`. On this branch, run `./gradlew patchChangelog`. Do not use `main`, because it can have entries that you added after the release.
- Step 9: Open a pull request into `main` for the branch. Then merge it.

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
5. Publish the draft release on GitHub. Do not delete the text of the draft release.
6. Make sure that the `Release` workflow is complete.
7. Run `gh variable delete SKIP_MARKETPLACE_PUBLISH`.
8. Do steps 6-9 of section 8. If the text of the release is empty, also obey the NOTE in section 8.

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
