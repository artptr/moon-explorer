package io.github.artptr.moonexplorer.cli

import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MoonCliTimeoutTest : BasePlatformTestCase() {

    /** A process that does not stop until the plugin stops it. The test does not start moon. */
    private class HangingProcess : Process() {
        private val stopped = CountDownLatch(1)

        val isStopped: Boolean get() = stopped.count == 0L

        override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()
        override fun getInputStream(): InputStream = InputStream.nullInputStream()
        override fun getErrorStream(): InputStream = InputStream.nullInputStream()

        override fun waitFor(): Int {
            stopped.await()
            return exitValue()
        }

        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = stopped.await(timeout, unit)

        override fun exitValue(): Int {
            if (!isStopped) throw IllegalThreadStateException("The process runs")
            return 143
        }

        override fun isAlive(): Boolean = !isStopped

        override fun destroy() = stopped.countDown()
    }

    private fun runWithTimeout(process: HangingProcess, timeoutMillis: Int): CliResult {
        val handler = CapturingProcessHandler(process, StandardCharsets.UTF_8, "moon query tasks")
        // The fake process has no operating system process. Thus the handler must not stop a process tree.
        handler.setShouldDestroyProcessRecursively(false)
        return runBlocking { withContext(Dispatchers.IO) { MoonCli.run(handler, timeoutMillis) } }
    }

    fun testProcessThatDoesNotStopGivesTimedOut() {
        val process = HangingProcess()
        assertEquals(CliResult.TimedOut, runWithTimeout(process, 200))
        assertTrue("The plugin must stop the process after the timeout", process.isStopped)
    }

    fun testDefaultTimeoutIsOneMinute() {
        assertEquals(60_000, MoonCli.TIMEOUT_MILLIS)
    }
}
