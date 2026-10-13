package io.github.artptr.moonexplorer.cli

import io.github.artptr.moonexplorer.testData.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MoonWorkspaceLocatorTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val workspace = TestData.workspace.toPath().toAbsolutePath().normalize()

    @Test
    fun findsTheWorkspaceFromItsRoot() {
        assertEquals(workspace, MoonWorkspaceLocator.findWorkspaceRoot(workspace))
    }

    @Test
    fun findsTheWorkspaceFromASubdirectory() {
        assertEquals(workspace, MoonWorkspaceLocator.findWorkspaceRoot(workspace.resolve("apps/web")))
    }

    @Test
    fun givesNullWithoutWorkspace() {
        assertNull(MoonWorkspaceLocator.findWorkspaceRoot(temp.newFolder("project").toPath()))
    }

    @Test
    fun skipsMoonDirectoryWithoutWorkspaceFile() {
        // The moon home directory ~/.moon has no workspace file.
        val home = temp.newFolder("home")
        home.resolve(".moon/bin").mkdirs()
        assertNull(MoonWorkspaceLocator.findWorkspaceRoot(home.resolve("project").toPath()))
    }

    @Test
    fun acceptsOtherWorkspaceFileFormats() {
        val root = temp.newFolder("pkl")
        root.resolve(".moon").mkdirs()
        root.resolve(".moon/workspace.pkl").writeText("")
        assertEquals(root.toPath().toAbsolutePath().normalize(), MoonWorkspaceLocator.findWorkspaceRoot(root.toPath()))
    }
}
