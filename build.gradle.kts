import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

// The quick check uses two IDEs only. It does not download the optional dependencies of the bundled plugins.
// The Build workflow uses it for pull requests. Refer to docs/development.md.
val quickVerification = providers.gradleProperty("quickVerification").map { it.toBoolean() }.getOrElse(false)

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // Do not add bridge methods for the default methods of platform interfaces.
        jvmDefault.set(org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode.NO_COMPATIBILITY)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    // https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea(providers.gradleProperty("platformVersion"))
        // This dependency is optional. Refer to docs/adr/0004-yaml-plugin-optional-dependency.md.
        bundledPlugin("org.jetbrains.plugins.yaml")
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        version = providers.gradleProperty("version")
        // The plugin.xml <change-notes> are generated from CHANGELOG.md.
        // A local variable keeps the lambda free of script references (configuration cache).
        val changelogExtension = project.changelog
        changeNotes = providers.gradleProperty("version").map { pluginVersion ->
            with(changelogExtension) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    org.jetbrains.changelog.Changelog.OutputType.HTML,
                )
            }
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }

    pluginVerification {
        ides {
            if (quickVerification) {
                // The first IDE is the IDE of the build. It is also the minimum IDE version for the plugin.
                current()
                // The second IDE is the newest released IDE. The check does not use EAP builds.
                // JetBrains often releases a new EAP build. If the check uses EAP builds, each new EAP build changes the key of the IDE cache in the 'Build' workflow.
                latest {
                    channels = listOf(ProductRelease.Channel.RELEASE)
                }
            } else {
                recommended()
            }
        }
    }
}

tasks.verifyPlugin {
    // In offline mode, the Plugin Verifier does not download the optional dependencies of the bundled plugins (about 2 GB).
    // The plugin does not use these dependencies. Thus the result of the check is the same.
    if (quickVerification) {
        offline = true
    }
}

// The Build workflow uses the first line of the output (the newest IDE) in the key of the IDE cache.
tasks.printProductsReleases {
    if (quickVerification) {
        channels = listOf(ProductRelease.Channel.RELEASE)
    }
}

// The test data is each file in src/test/testData that Git does not ignore, also new files that Git does not track yet.
// Thus the files that tools make in the test workspace, for example node_modules, are not inputs of the tests.
// If the project is not in a Git repository, the input is the complete directory.
val testDataFiles = providers.exec {
    commandLine("git", "ls-files", "--cached", "--others", "--exclude-standard", "-z", "--", "src/test/testData")
    isIgnoreExitValue = true
}.let { git ->
    git.result.zip(git.standardOutput.asText) { result, output ->
        if (result.exitValue == 0) output.split('\u0000').filter(String::isNotEmpty) else listOf("src/test/testData")
    }
}

tasks.test {
    // The tests read the test data with this path. Refer to TestData.kt.
    systemProperty("moonExplorer.testData", layout.projectDirectory.dir("src/test/testData").asFile.absolutePath)
    // YamlImportBoundaryTest reads the main Kotlin sources. An unused import does not change the classes. Thus the sources are an input.
    val mainSources = layout.projectDirectory.dir("src/main/kotlin")
    systemProperty("moonExplorer.mainSources", mainSources.asFile.absolutePath)
    inputs.dir(mainSources).withPropertyName("moonExplorerMainSources").withPathSensitivity(PathSensitivity.RELATIVE)
    // The test data is an input of the task. Thus Gradle runs the tests again when the test data changes.
    inputs.files(testDataFiles).withPropertyName("moonExplorerTestData").withPathSensitivity(PathSensitivity.RELATIVE)
}

changelog {
    groups.empty()
    repositoryUrl = providers.gradleProperty("pluginRepositoryUrl")
    // The release tags have no prefix, for example '0.2.0'. The default prefix 'v' makes incorrect links in CHANGELOG.md.
    versionPrefix = ""
}

// Runs the plugin in the local WebStorm installation. Set the path with the property `webStormPath`.
intellijPlatformTesting {
    runIde {
        register("runWebStorm") {
            localPath = file(
                providers.gradleProperty("webStormPath")
                    .getOrElse("${System.getProperty("user.home")}/Applications/WebStorm.app"),
            )
        }
    }
}
