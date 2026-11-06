package io.github.artptr.moonexplorer.model

/**
 * The data from the output of `moon task-graph --json`. This output also contains the internal tasks.
 * [dependents] gives the direct dependents of each target. The dependents are in [ID_ORDER]. A target without dependents is not in the map.
 *
 * [mergeStrategies] gives the merge strategies of each target after the merge. A task with only the value `append` is not in the map.
 */
data class TaskGraph(
    val dependents: Map<String, Set<String>> = emptyMap(),
    val mergeStrategies: Map<String, Map<String, String>> = emptyMap(),
)

/** Reads the JSON output of `moon task-graph --json`. */
object TaskGraphParser {

    /**
     * Reads [json]. Gives null if the output does not contain the object `data`. The function ignores values that it does not know.
     * Each value in `data` is a task with its dependencies. Thus the function does not read the node and edge format of the graph library of moon.
     * Throws [MoonOutputException] if the output is not valid JSON.
     */
    fun parse(json: String): TaskGraph? {
        val tasks = parseMoonJson(json).objectOrNull()?.obj("data") ?: return null
        val dependents = HashMap<String, MutableSet<String>>()
        val strategies = HashMap<String, Map<String, String>>()
        for ((_, value) in tasks.entrySet()) {
            val task = value.objectOrNull() ?: continue
            val target = task.string("target") ?: continue
            task.get("deps")?.arrayOrNull()?.forEach { dep ->
                val dependency = dep.objectOrNull()?.string("target") ?: return@forEach
                dependents.getOrPut(dependency) { sortedSetOf(ID_ORDER) } += target
            }
            mergeStrategies(task).takeIf { it.isNotEmpty() }?.let { strategies[target] = it }
        }
        return TaskGraph(dependents, strategies)
    }
}
