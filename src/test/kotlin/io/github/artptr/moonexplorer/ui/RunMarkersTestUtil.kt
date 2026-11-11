package io.github.artptr.moonexplorer.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PlatformTestUtil
import io.github.artptr.moonexplorer.model.MoonTasksService
import io.github.artptr.moonexplorer.model.lastLoaded
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Records each file that the refresher analyzes again, and gives the list of the files. If [callReal] is true, the refresher also does the real restart.
 * The light project is the same for all tests. Thus the function puts the real restart back when the IDE disposes [disposable].
 */
fun RunMarkersRefresher.recordRestarts(disposable: Disposable, callReal: Boolean): MutableList<PsiFile> {
    val real = restart
    val files = Collections.synchronizedList(mutableListOf<PsiFile>())
    restart = { file ->
        files += file
        if (callReal) real(file)
    }
    Disposer.register(disposable) { restart = real }
    return files
}

/**
 * Waits until [RunMarkersRefresher] handled the current loaded data of the service. If the IDE did not make the refresher, the function does nothing.
 * If the refresher does not handle the data in 10 seconds, the test fails.
 *
 * The refresher starts the analysis of the open files on the EDT after a load. If the refresher does this while a test highlights a file,
 * the test framework stops the analysis with an AssertionError. Thus the test helpers that change the state call this function.
 */
fun MoonTasksService.awaitRunMarkersRefresher() {
    val refresher = project.serviceIfCreated<RunMarkersRefresher>() ?: return
    fun done() = refresher.handled == state.value.lastLoaded
    val message = "The refresher did not handle the loaded data"
    if (ApplicationManager.getApplication().isDispatchThread) {
        // The refresher uses the EDT. Thus the wait must dispatch the events.
        PlatformTestUtil.waitWithEventsDispatching(message, ::done, 10)
    } else {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!done()) {
            if (System.nanoTime() > deadline) throw AssertionError(message)
            Thread.sleep(10)
        }
    }
}
