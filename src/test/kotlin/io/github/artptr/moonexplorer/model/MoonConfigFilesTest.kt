package io.github.artptr.moonexplorer.model

import com.intellij.openapi.util.io.FileUtil
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

    private fun vfsPath(path: Path) = FileUtil.toSystemIndependentName(path.toString())

    private fun changed(file: String, data: ConfigWatch = watch) = MoonConfigFiles.isConfigChange(vfsPath(root.resolve(file)), data)

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
        assertTrue(changed(".moon/tasks"))
    }

    @Test
    fun filesInTheCacheAreNotAChange() {
        assertFalse(changed(".moon/cache/states/workspaceGraph.json"))
        assertFalse(changed(".moon/cache/hashes/abc.json"))
        assertFalse(changed(".moon/cache"))
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
        assertFalse(MoonConfigFiles.isConfigChange(vfsPath(Path.of("/work/other/moon.yml").toAbsolutePath()), watch))
        assertFalse(MoonConfigFiles.isConfigChange(vfsPath(Path.of("/work/other/.moon/workspace.yml").toAbsolutePath()), watch))
    }

    @Test
    fun projectDirectoryAndItsParentsAreAChange() {
        assertTrue(changed("apps/web"))
        assertTrue(changed("apps"))
        assertTrue(changed("packages/ui"))
    }

    @Test
    fun otherDirectoryIsNotAChange() {
        assertFalse(changed("apps/web/dist"))
        assertFalse(changed("apps/web-admin"))
        assertFalse(changed("tools"))
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
        val nested = ConfigWatch(root, root.resolve("packages/ui/icons"), watch.sources)
        assertTrue(changed("packages/ui/.moon/workspace.yml", data = nested))
        assertTrue(changed("packages/ui/icons/.moon/workspace.yml", data = nested))
        // The plugin does not look in other directories.
        assertFalse(changed("apps/web/.moon/workspace.yml", data = nested))
        // A workspace file above the root also applies. The refresh then finds the same root. This rare case does not need a special rule.
        assertTrue(MoonConfigFiles.isConfigChange(vfsPath(root.parent.resolve(".moon/workspace.yml")), nested))
    }
}
