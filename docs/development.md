# Development

## 1. Prerequisites

- Git
- JDK 17 or later to start Gradle. Gradle gets JDK 21 for the build automatically.
- IntelliJ IDEA (recommended for development)
- [Vale](https://vale.sh) for documentation checks
- [GitHub CLI](https://cli.github.com) (`gh`) for issues and pull requests
- [moon](https://moonrepo.dev/docs/install) for manual tests

## 2. Build and test

1. Clone the repository.
2. Open a terminal in the root directory of the repository.
3. Run `./gradlew build`.

The `build` task compiles the code and runs the tests.

To run only the tests, use `./gradlew test`.

## 3. Run the plugin in a sandbox IDE

1. Run `./gradlew runIde`.
2. In the sandbox IDE, open a moon workspace.
3. Open the **Moon** tool window.

## 4. Do a check of compatibility

Run `./gradlew verifyPlugin`.
The IntelliJ Plugin Verifier does a check of the plugin against the recommended IDE versions.

## 5. Do a check of the documentation

Run `vale .`.
Refer to [ste/guide.md](ste/guide.md).

## 6. Continuous integration

GitHub Actions runs these workflows:

| Workflow | Trigger | Jobs |
|----------|---------|------|
| `Build` | Push to `main`, pull request | Build, test, verification of the plugin, draft release |
| `Docs` | Push to `main`, pull request | STE check with Vale |
| `Labels` | Change to `.github/labels.yml` on `main` | Sync of the GitHub labels |
| `Release` | Publication of a GitHub release | Publication to JetBrains Marketplace |

## 7. Release

1. Make sure that `CHANGELOG.md` has entries under `Unreleased`.
2. Set the new version in `gradle.properties`.
3. Merge the change into `main`.
4. On GitHub, open the draft release that the `Build` workflow created.
5. Publish the draft release.

The `Release` workflow signs the plugin and publishes it to JetBrains Marketplace.
These repository secrets are necessary: `PUBLISH_TOKEN`, `CERTIFICATE_CHAIN`, `PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD`.
Refer to <https://plugins.jetbrains.com/docs/intellij/plugin-signing.html>.

## 8. Initial GitHub setup

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
