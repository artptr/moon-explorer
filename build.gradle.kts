import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

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
        intellijIdea("2025.3.6.1")
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
            recommended()
        }
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
