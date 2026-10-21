package io.github.artptr.moonexplorer.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

class MoonConfigFilesTest {

    private val root = Path.of("/work/repo").toAbsolutePath()

    private val watch = ConfigWatch(
        root,
        root,
        listOf(ProjectSource("web", "apps/web"), ProjectSource("icons", "packages/ui/icons")),
    )

    private fun changed(file: String, removedDirectory: Boolean = false, data: ConfigWatch = watch) =
        MoonConfigFiles.isConfigChange(root.resolve(file), removedDirectory, data)

    @Test
    fun projectConfigFileIsAChange() {
        assertTrue(changed("apps/web/moon.yml"))
        assertTrue(changed("apps/new/moon.yaml"))
        assertTrue(changed("packages/ui/icons/moon.pkl"))
    }

    @Test
    fun filesInTheMoonDirectoryAreAChange() {
        assertTrue(changed(".moon/workspace.yml"))
        assertTrue(changed(".moon/tasks/all.yml"))
        assertTrue(changed(".moon/tasks/node/frontend.yml"))
        assertTrue(changed(".moon/toolchains.yml"))
        assertTrue(changed(".moon/tasks", removedDirectory = true))
    }

    @Test
    fun filesInTheCacheAreNotAChange() {
        assertFalse(changed(".moon/cache/states/workspaceGraph.json"))
        assertFalse(changed(".moon/cache/hashes/abc.json"))
        assertFalse(changed(".moon/cache", removedDirectory = true))
    }

    @Test
    fun otherFilesAreNotAChange() {
        assertFalse(changed("README.md"))
        assertFalse(changed("apps/web/src/moon.ts"))
        assertFalse(changed("apps/web/moon.yml.bak"))
        assertFalse(changed("apps/web/moonx.yml"))
    }

    @Test
    fun fileOutsideTheRootIsNotAChange() {
        assertFalse(MoonConfigFiles.isConfigChange(Path.of("/work/other/moon.yml").toAbsolutePath(), false, watch))
        assertFalse(MoonConfigFiles.isConfigChange(Path.of("/work/other/.moon/workspace.yml").toAbsolutePath(), false, watch))
    }

    @Test
    fun removedProjectDirectoryIsAChange() {
        assertTrue(changed("apps/web", removedDirectory = true))
        assertTrue(changed("apps", removedDirectory = true))
        assertTrue(changed("packages/ui", removedDirectory = true))
    }

    @Test
    fun otherRemovedDirectoryIsNotAChange() {
        assertFalse(changed("apps/web/dist", removedDirectory = true))
        assertFalse(changed("apps/web-admin", removedDirectory = true))
        assertFalse(changed("tools", removedDirectory = true))
    }

    @Test
    fun withoutWorkspaceOnlyANewWorkspaceFileIsAChange() {
        val noWorkspace = ConfigWatch(null, root.resolve("apps/web"))
        assertTrue(changed(".moon/workspace.yml", data = noWorkspace))
        assertTrue(changed("apps/web/.moon/workspace.json", data = noWorkspace))
        assertFalse(changed("apps/web/moon.yml", data = noWorkspace))
        assertFalse(changed(".moon/tasks/all.yml", data = noWorkspace))
        assertFalse(changed("apps/admin/.moon/workspace.yml", data = noWorkspace))
    }

    @Test
    fun withoutDataNothingIsAChange() {
        assertFalse(changed(".moon/workspace.yml", data = ConfigWatch(null, null)))
    }

    @Test
    fun newWorkspaceFileBetweenTheProjectDirectoryAndTheRootIsAChange() {
        val nested = watch.copy(projectDir = root.resolve("packages/ui/icons"))
        assertTrue(changed("packages/ui/.moon/workspace.yml", data = nested))
        assertTrue(changed("packages/ui/icons/.moon/workspace.yml", data = nested))
        // The plugin does not look in other directories.
        assertFalse(changed("apps/web/.moon/workspace.yml", data = nested))
        // A workspace file above the root does not change the root. The nearer root remains.
        assertFalse(MoonConfigFiles.isConfigChange(root.parent.resolve(".moon/workspace.yml"), false, nested))
    }
}
