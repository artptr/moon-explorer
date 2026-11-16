package io.github.artptr.moonexplorer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Rule 2 of ADR 0006: only the package `json` uses the classes of the JSON plugin. The JSON plugin is an optional dependency.
 * If a class in a different package uses a JSON class, a `NoClassDefFoundError` occurs when the user disables the JSON plugin.
 * The tests always load the JSON plugin. Thus this test reads the source files to find the problem.
 */
class JsonImportBoundaryTest {

    /** Gradle sets the property `moonExplorer.mainSources`. Without it, the path is relative to the repository root. */
    private val sources = File(System.getProperty("moonExplorer.mainSources") ?: "src/main/kotlin")

    private val jsonPackage = File(sources, "io/github/artptr/moonexplorer/json")

    @Test
    fun onlyTheJsonPackageUsesJsonClasses() {
        val files = sources.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("No Kotlin files in $sources", files.isNotEmpty())
        val violations = files
            .filterNot { it.startsWith(jsonPackage) }
            .filter { file -> file.readLines().any { JSON_REFERENCE.containsMatchIn(it) } }
            .map { it.relativeTo(sources).invariantSeparatorsPath }
        assertEquals("These files use the JSON plugin or the package json outside the package json", emptyList<String>(), violations)
    }

    private companion object {
        /**
         * A reference to a class of the JSON plugin, or to a class of the package `json`. A class of the package `json` can load JSON classes.
         * Thus a class in a different package must not use it either. The platform also has classes in `com.intellij.json`. The plugin does not use them.
         */
        val JSON_REFERENCE = Regex("""\b(?:com\.jetbrains\.jsonSchema|com\.intellij\.json|io\.github\.artptr\.moonexplorer\.json)\b""")
    }
}
