package io.github.artptr.moonexplorer.json

import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests the choice of the procedure for the remote schemas for each IDE build. */
class RemoteSchemaPathTest {

    @Test
    fun procedureForEachBuild() {
        // 2025.3, 2026.1, and 2026.2 use the file of the URL.
        for (build in listOf(253, 261, 262)) assertEquals("$build", RemoteSchemaPath.SCHEMA_FILE, RemoteSchemaPath.forBuild(build))
        // 2026.3 and later use the remote source.
        for (build in listOf(263, 271)) assertEquals("$build", RemoteSchemaPath.REMOTE_SOURCE, RemoteSchemaPath.forBuild(build))
    }
}
