package io.github.artptr.moonexplorer.json

import com.jetbrains.jsonSchema.remote.JsonFileResolver
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests the choice of the procedure for the remote schemas. */
class RemoteSchemaPathTest {

    /** A resolver of the old procedure. */
    @Suppress("unused")
    private object OldResolver {
        @JvmStatic
        fun startFetchingHttpFileIfNeeded() = Unit
    }

    @Test
    fun procedureFromTheFunctionsOfTheResolver() {
        assertEquals(RemoteSchemaPath.SCHEMA_FILE, RemoteSchemaPath.detect(OldResolver::class.java))
        assertEquals(RemoteSchemaPath.REMOTE_SOURCE, RemoteSchemaPath.detect(Any::class.java))
    }

    @Test
    fun procedureAgreesWithTheJsonPluginOfTheTestIde() {
        // Only the new procedure has JsonSchemaRemoteContentService. This is true for 2025.3, 2026.1, 2026.2, 263.7031.23, and 263.7307.44.
        val newProcedure = try {
            Class.forName("com.jetbrains.jsonSchema.remote.http.JsonSchemaRemoteContentService", false, JsonFileResolver::class.java.classLoader)
            true
        } catch (_: ClassNotFoundException) {
            false
        }
        val expected = if (newProcedure) RemoteSchemaPath.REMOTE_SOURCE else RemoteSchemaPath.SCHEMA_FILE
        assertEquals(expected, RemoteSchemaPath.current)
    }
}
