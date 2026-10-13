package io.github.artptr.moonexplorer.model

/** A task of a moon project. [target] has the format `<project>:<task>`. */
data class MoonTask(val id: String, val target: String, val description: String?)

/** A moon project and its tasks, sorted by ID. */
data class MoonProject(val id: String, val tasks: List<MoonTask>)

/** All projects of a workspace that have tasks, sorted by ID. */
data class WorkspaceTasks(val projects: List<MoonProject>)
