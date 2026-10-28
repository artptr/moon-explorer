package io.github.artptr.moonexplorer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Rule 5 of ADR 0004: only the package `yaml` uses the classes of the YAML plugin. The YAML plugin is an optional dependency.
 * If a class in a different package uses a YAML class, a `NoClassDefFoundError` occurs when the user disables the YAML plugin.
 * The tests always load the YAML plugin. Thus this test reads the source files to find the problem.
 */
class YamlImportBoundaryTest {

    /** Gradle sets the property `moonExplorer.mainSources`. Without it, the path is relative to the repository root. */
    private val sources = File(System.getProperty("moonExplorer.mainSources") ?: "src/main/kotlin")

    private val yamlPackage = File(sources, "io/github/artptr/moonexplorer/yaml")

    @Test
    fun onlyTheYamlPackageUsesYamlClasses() {
        val files = sources.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("No Kotlin files in $sources", files.isNotEmpty())
        val violations = files
            .filterNot { it.startsWith(yamlPackage) }
            .filter { file -> file.readLines().any { YAML_REFERENCE.containsMatchIn(it) } }
            .map { it.relativeTo(sources).invariantSeparatorsPath }
        assertEquals("These files use org.jetbrains.yaml or the package yaml outside the package yaml", emptyList<String>(), violations)
    }


    private companion object {
        /**
         * A reference to a YAML class, or to a class of the package `yaml`. A class of the package `yaml` can load YAML classes.
         * Thus a class in a different package must not use it either.
         */
        val YAML_REFERENCE = Regex("""\b(?:org\.jetbrains\.yaml|io\.github\.artptr\.moonexplorer\.yaml)\b""")
    }
}
