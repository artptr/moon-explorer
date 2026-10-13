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

The plugin uses `moon` from `PATH`. Before you start the sandbox IDE, make sure that `moon --version` shows moon 2.2.3 or later.

1. Run `./gradlew runIde`.
2. In the sandbox IDE, open a moon workspace.
3. Open the **Moon** tool window.

To open the test workspace in the sandbox IDE, do these steps:

1. Run `./gradlew runIde`.
2. In the sandbox IDE, open the directory `src/test/testData/workspace/`.
3. Open the **Moon** tool window.

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

- Application projects and library projects. Each project has the `stack` value `frontend` or `backend`.
- Tags. Some tags are on more than one project.
- Three files in `.moon/tasks/`. All projects inherit `all.yml`. Frontend projects inherit `frontend.yml`. Backend projects inherit `backend.yml`.
- The project `web` changes the inherited task `lint`.
- Tasks with a description and tasks without a description
- The project `config` has no tasks.

**NOTE:** The output of `moon query tasks` does not contain projects without tasks. Thus the project `config` is not in `moon/query-tasks.json`.

### 4.1 Record the output again

Record the output again after you change the test workspace.
The file `workspace/package.json` sets the moon version. Node.js and npm are necessary.

1. Run `scripts/record-test-data.sh`.
2. Run `./gradlew test`.
3. Commit the changes in `src/test/testData/`.

The script installs the moon version that `package.json` gives. Then it writes the files in `moon/`.
The script stops with an error if a recorded file contains the path of the repository on your computer.

### 4.2 Test data for a large workspace

Do not commit large test files that a tool makes. Make them during the test.
For tests with a large workspace, use the test function `LargeQueryTasksJson.generate()`.
The function copies the recorded tasks and gives them new IDs.
The default output has 17 projects with 14 tasks each, and about 16,000 lines.

## 5. Do a check of compatibility

Run `./gradlew verifyPlugin`.
The IntelliJ Plugin Verifier does a check of the plugin against the recommended IDE versions.

## 6. Do a check of the documentation

Run `vale .`.
Refer to [ste/guide.md](ste/guide.md).

## 7. Continuous integration

GitHub Actions runs these workflows:

| Workflow | Trigger | Jobs |
|----------|---------|------|
| `Build` | Push to `main`, pull request | Build, test, verification of the plugin, draft release |
| `Docs` | Push to `main`, pull request | STE check with Vale |
| `Labels` | Change to `.github/labels.yml` on `main` | Sync of the GitHub labels |
| `Release` | Publication of a GitHub release | Publication to JetBrains Marketplace |

## 8. Release

1. Make sure that `CHANGELOG.md` has entries under `Unreleased`.
2. Set the new version in `gradle.properties`.
3. Merge the change into `main`.
4. On GitHub, open the draft release that the `Build` workflow created.
5. Publish the draft release.

The `Release` workflow signs the plugin and publishes it to JetBrains Marketplace.
These repository secrets are necessary: `PUBLISH_TOKEN`, `CERTIFICATE_CHAIN`, `PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD`.
Refer to <https://plugins.jetbrains.com/docs/intellij/plugin-signing.html>.

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
7. In the repository settings, add a branch rule for `main`. Make the `Build` and `Docs` checks necessary before a merge.
8. In the repository settings, turn on **Discussions**.
