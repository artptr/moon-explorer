package io.github.artptr.moonexplorer.schema

import io.github.artptr.moonexplorer.model.objectOrNull
import io.github.artptr.moonexplorer.model.parseMoonJson
import io.github.artptr.moonexplorer.model.string
import java.nio.file.InvalidPathException
import java.nio.file.Path

/** Reads the output of `moon templates --json`. */
object TemplatesParser {

    /**
     * Gives the root directory of each template in [json]. The output is an object with one entry for each template ID.
     * Each entry has the absolute path of the template in the key `root`.
     * The function ignores an entry without a valid path. Throws [io.github.artptr.moonexplorer.model.MoonOutputException] if [json] is not valid JSON.
     */
    fun parse(json: String): List<Path> {
        val templates = parseMoonJson(json).objectOrNull() ?: return emptyList()
        return templates.entrySet().mapNotNull { (_, template) ->
            template.objectOrNull()?.string("root")?.let(::toPath)
        }
    }

    private fun toPath(root: String): Path? = try {
        Path.of(root).takeIf { it.isAbsolute }
    } catch (_: InvalidPathException) {
        null
    }
}
