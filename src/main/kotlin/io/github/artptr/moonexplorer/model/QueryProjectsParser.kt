package io.github.artptr.moonexplorer.model

/** Reads the JSON output of `moon query projects`. */
object QueryProjectsParser {

    /** moon gives this `stack` value if a project does not set `stack`. */
    private const val UNKNOWN_STACK = "unknown"

    /**
     * Reads [json] and gives the `stack` value of each project. The keys are project IDs.
     * The value is null if the project has no `stack` value.
     * Throws [MoonOutputException] if the JSON is not correct.
     */
    fun parseStacks(json: String): Map<String, String?> {
        val projects = parseMoonJson(json).objectOrNull()?.get("projects")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: throw MoonOutputException("The output does not contain the array \"projects\"")
        return projects.mapNotNull { it.objectOrNull() }
            .mapNotNull { project ->
                val id = project.string("id") ?: return@mapNotNull null
                val stack = project.string("stack") ?: project.get("config")?.objectOrNull()?.string("stack")
                id to stack?.takeUnless { it.isBlank() || it.equals(UNKNOWN_STACK, ignoreCase = true) }
            }
            .toMap()
    }
}
