package io.github.artptr.moonexplorer.ui

import com.intellij.openapi.util.text.StringUtil
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.model.MoonFailure
import io.github.artptr.moonexplorer.model.OutputProblem
import io.github.artptr.moonexplorer.model.TasksState

// The tool window and the notification show the same text for a failure.

/** Gives the text that tells the user about [failure]. The text is plain text, not HTML. */
internal fun failureText(failure: TasksState.Failure): String = when (failure) {
    is TasksState.MoonFailed -> moonFailureText(failure.cause)
    is TasksState.InvalidOutput -> invalidOutputText(failure.problem)
    TasksState.UnexpectedError -> MoonExplorerBundle.message("tasks.message.unexpectedError")
}

private fun moonFailureText(cause: MoonFailure): String = when (cause) {
    is MoonFailure.NotFound -> {
        val locations = cause.examined.joinToString("\n") { "  $it" }
        MoonExplorerBundle.message("tasks.message.moonNotFound", locations)
    }
    is MoonFailure.Error -> MoonExplorerBundle.message("tasks.message.moonError", cause.text)
    is MoonFailure.ExitCode -> MoonExplorerBundle.message("tasks.message.moonExitCode", cause.code.toString())
    MoonFailure.Cancelled -> MoonExplorerBundle.message("tasks.message.moonCancelled")
    is MoonFailure.StartFailed -> detailText(cause.detail, "tasks.message.moonStartFailed", "tasks.message.moonStartFailedNoDetail")
    MoonFailure.TimedOut -> MoonExplorerBundle.message("tasks.message.moonTimeout")
}

private fun invalidOutputText(problem: OutputProblem): String = when (problem) {
    is OutputProblem.NotJson -> detailText(problem.detail, "tasks.message.invalidOutput.notJson", "tasks.message.invalidOutput.notJsonNoDetail")
    OutputProblem.NoProjects -> MoonExplorerBundle.message("tasks.message.invalidOutput.noProjects")
}

/** Gives the message [key] with [detail]. If [detail] is null or blank, gives the message [noDetailKey]. */
private fun detailText(detail: String?, key: String, noDetailKey: String): String =
    StringUtil.nullize(detail, true)?.let { MoonExplorerBundle.message(key, it) } ?: MoonExplorerBundle.message(noDetailKey)

/** Gives true if the user can correct [failure] in the settings. Then the plugin shows a link to the settings. */
internal fun hasSettingsLink(failure: TasksState.Failure): Boolean =
    failure is TasksState.MoonFailed && when (failure.cause) {
        is MoonFailure.NotFound, is MoonFailure.StartFailed, MoonFailure.TimedOut -> true
        is MoonFailure.Error, is MoonFailure.ExitCode, MoonFailure.Cancelled -> false
    }

/**
 * Gives the HTML text of a balloon or a notification for the plain text [text]. Balloons and notifications read their text as HTML.
 * Thus a file name with `<`, `>`, or `&` must change into HTML entities. A line break changes into `<br>`.
 */
internal fun plainTextHtml(text: String): String = StringUtil.escapeXmlEntities(text).replace("\n", "<br>")
