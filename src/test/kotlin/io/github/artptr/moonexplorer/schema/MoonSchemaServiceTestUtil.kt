package io.github.artptr.moonexplorer.schema

import com.google.gson.JsonPrimitive
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.PlatformTestUtil
import io.github.artptr.moonexplorer.cli.CliResult
import io.github.artptr.moonexplorer.testData.TestData
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Path

/**
 * Stops the service and removes its mapping. Then sets fakes that do not start moon. The service does not start.
 * The tests of the schemas call [MoonSchemaService.start]. Call [MoonSchemaService.resetForTests] in tearDown.
 */
fun MoonSchemaService.prepareForTests() {
    runBlocking { resetForTests() }
    templates = { CliResult.Success("{}") }
    syncSchemas = { CliResult.Failed("The tests do not start moon") }
}

/**
 * Waits until the mapping agrees with [predicate], and until the jobs of the service stop. If this does not occur in 10 seconds, the test fails.
 * The function runs the events of the EDT while it waits. The refresh of the VFS after `moon sync` needs the EDT.
 */
fun MoonSchemaService.awaitMapping(predicate: (SchemaMapping?) -> Boolean): SchemaMapping? {
    PlatformTestUtil.waitWithEventsDispatching(
        { "The mapping did not change as expected. The mapping is ${mapping.value}" },
        { predicate(mapping.value) && jobsCompletedForTests() },
        10,
    )
    return mapping.value
}

/** Waits until the jobs of the service stop. If this does not occur in 10 seconds, the test fails. */
fun MoonSchemaService.awaitJobs() {
    awaitMapping { true }
}

/** Gives [text] as a JSON string, with the quotes. For example, a path in the fake output of moon. */
fun jsonString(text: String): String = JsonPrimitive(text).toString()

/** The output of `moon templates --json` for [root]. The recorded file has the template `package` in `/workspace/templates/package`. */
fun recordedTemplates(root: Path): String =
    TestData.recorded("templates.json").replace("\"/workspace", jsonString(root.toString()).dropLast(1))

/**
 * Copies the test workspace to [target], without `node_modules/`, `.moon/cache/`, `.idea/`, and the `package.json` files.
 * The JavaScript plugin keeps pointers to the `package.json` files in the content of the IDE project. The test fails if a pointer stays.
 * If [schemas] is true, the copy gets the recorded schemas in `.moon/cache/schemas/`, as after `moon sync config-schemas`.
 */
fun copyWorkspace(target: Path, schemas: Boolean = true) {
    FileUtil.copyDir(TestData.workspace, target.toFile()) { file ->
        val relative = FileUtil.getRelativePath(TestData.workspace, file)?.replace(File.separatorChar, '/') ?: return@copyDir true
        relative !in EXCLUDED && file.name !in PACKAGE_FILES
    }
    if (schemas) copyRecordedSchemas(target)
}

private val EXCLUDED = setOf("node_modules", ".moon/cache", ".idea")
private val PACKAGE_FILES = setOf("package.json", "package-lock.json")

/** Copies the recorded schemas to `.moon/cache/schemas/` in [root]. */
fun copyRecordedSchemas(root: Path) {
    FileUtil.copyDir(File(TestData.moon, "schemas"), File(SchemaMapping.of(root).schemasDir))
}
