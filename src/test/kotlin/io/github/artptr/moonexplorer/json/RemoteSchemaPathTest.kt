package io.github.artptr.moonexplorer.json

import com.jetbrains.jsonSchema.remote.JsonFileResolver
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests the choice of the procedure for the remote schemas. */
class RemoteSchemaPathTest {

    @Test
    fun procedureFromTheClassesOfTheJsonPlugin() {
        assertEquals(RemoteSchemaPath.SCHEMA_FILE, RemoteSchemaPath.detect { false })
        assertEquals(RemoteSchemaPath.SCHEMA_FILE, RemoteSchemaPath.detect { it != RemoteSchemaPath.REMOTE_CONTENT_SERVICE })
        assertEquals(RemoteSchemaPath.REMOTE_SOURCE, RemoteSchemaPath.detect { it == RemoteSchemaPath.REMOTE_CONTENT_SERVICE })
    }

    @Test
    fun procedureAgreesWithTheJsonPluginOfTheTestIde() {
        // Only the old procedure has startFetchingHttpFileIfNeeded. This is true for 2025.3, 2026.1, 2026.2, 263.7031.23, and 263.7307.44.
        val oldProcedure = JsonFileResolver::class.java.methods.any { it.name == "startFetchingHttpFileIfNeeded" }
        val expected = if (oldProcedure) RemoteSchemaPath.SCHEMA_FILE else RemoteSchemaPath.REMOTE_SOURCE
        assertEquals(expected, RemoteSchemaPath.current)
    }
}
