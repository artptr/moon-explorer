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

// Workaround for issue #40. With the configuration cache, the task key of compileTestKotlin does not contain the main classes.
// Thus Gradle can use old test classes after a change to the main classes. This input adds the main classes to the task key.
// Use ClasspathNormalizer, not CompileClasspathNormalizer: the tests also contain the bodies of inline functions.
// Gradle corrects the problem in version 9.9.0: https://github.com/gradle/gradle/issues/39132
// Remove this input after the update to Gradle 9.9.0. Then do the steps in issue #40 again.
// Also refer to https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1880.
tasks.named("compileTestKotlin") {
    inputs.files(sourceSets.main.map { it.output.classesDirs })
        .withPropertyName("mainClassesForIssue40")
        .withNormalizer(ClasspathNormalizer::class)
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

tasks.test {
    // The tests read the test data with this path. Refer to TestData.kt.
    systemProperty("moonExplorer.testData", layout.projectDirectory.dir("src/test/testData").asFile.absolutePath)
}

changelog {
    groups.empty()
    repositoryUrl = providers.gradleProperty("pluginRepositoryUrl")
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
