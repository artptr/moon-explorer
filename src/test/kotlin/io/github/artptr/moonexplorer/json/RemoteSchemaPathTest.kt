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
        // The check below is valid for 2025.3, 2026.1, 2026.2, 263.7031.23, and 263.7307.44.
        // Only the builds with the new procedure have the class JsonSchemaRemoteContentService. The builds with the old procedure do not have it.
        // The test finds the class file and does not load the class. Thus a class that the JVM cannot link does not change the result.
        val classFile = "com/jetbrains/jsonSchema/remote/http/JsonSchemaRemoteContentService.class"
        val newProcedure = JsonFileResolver::class.java.classLoader.getResource(classFile) != null
        val expected = if (newProcedure) RemoteSchemaPath.REMOTE_SOURCE else RemoteSchemaPath.SCHEMA_FILE
        assertEquals(expected, RemoteSchemaPath.current)
    }
}
