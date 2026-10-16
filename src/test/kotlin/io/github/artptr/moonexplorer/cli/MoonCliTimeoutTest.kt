package io.github.artptr.moonexplorer.cli

import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
            // Do not wait without a limit. If the plugin does not stop the process, the test must fail and not hang.
            stopped.await(TEST_LIMIT_MILLIS, TimeUnit.MILLISECONDS)
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

    private fun runHangingProcess(process: HangingProcess, timeoutMillis: Int): CliResult {
        val handler = CapturingProcessHandler(process, StandardCharsets.UTF_8, "moon query tasks")
        // The fake process has no operating system process. Thus the handler must not stop a process tree.
        handler.setShouldDestroyProcessRecursively(false)
        return runBlocking {
            withTimeout(TEST_LIMIT_MILLIS) { withContext(Dispatchers.IO) { MoonCli.runWithTimeout(handler, timeoutMillis) } }
        }
    }

    fun testProcessThatDoesNotStopGivesTimedOut() {
        val process = HangingProcess()
        assertEquals(CliResult.TimedOut, runHangingProcess(process, 200))
        assertTrue("The plugin must stop the process after the timeout", process.isStopped)
    }

    // Issue #26 sets the time limit to 60 seconds. If you change the limit intentionally, change this test too.
    fun testDefaultTimeoutIsOneMinute() {
        assertEquals(60_000, MoonCli.TIMEOUT_MILLIS)
    }

    private companion object {
        /** The maximum time that a test waits for the fake process. */
        const val TEST_LIMIT_MILLIS = 10_000L
    }
}
