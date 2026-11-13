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

    private fun changed(file: String, data: ConfigWatch = watch, structural: Boolean = true) = changedAt(root.resolve(file), data, structural)

    private fun changedAt(path: Path, data: ConfigWatch = watch, structural: Boolean = true) =
        MoonConfigFiles.isConfigChange(vfsPath(path), data, structural)

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
        assertFalse(changedAt(Path.of("/work/other/moon.yml").toAbsolutePath()))
        assertFalse(changedAt(Path.of("/work/other/.moon/workspace.yml").toAbsolutePath()))
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
        assertTrue(changedAt(root.parent.resolve(".moon/workspace.yml"), nested))
    }

    @Test
    fun contentChangeOfAProjectDirectoryIsNotAChange() {
        assertFalse(changed("apps/web", structural = false))
        assertFalse(changed("apps", structural = false))
        assertTrue(changed("apps/web/moon.yml", structural = false))
        assertTrue(changed(".moon/tasks/all.yml", structural = false))
    }

    @Test
    fun fileInADirectoryNextToTheRootIsNotAChange() {
        // The name of this directory starts with the name of the root.
        val other = root.resolveSibling("repo-other")
        assertFalse(changedAt(other.resolve("moon.yml")))
        assertFalse(changedAt(other.resolve(".moon/tasks/all.yml")))
        assertFalse(changedAt(other.resolve("apps/web")))
        // This path does not go through the check of the root. The plugin compares the directory of the workspace file with the project directory.
        assertFalse(changedAt(other.resolve(".moon/workspace.yml")))
    }

    @Test
    fun nameThatOnlyEndsLikeAConfigFileIsNotAChange() {
        val nested = ConfigWatch(root, root.resolve("apps/web"), watch.sources)
        assertFalse(changed("apps/web/xmoon.yml", data = nested))
        assertFalse(changed("apps/web/.xmoon/workspace.yml", data = nested))
        assertFalse(changed("apps/web/.moon/xworkspace.yml", data = nested))
    }

    @Test
    fun directoryWithANameThatStartsLikeTheMoonDirectoryIsNotTheMoonDirectory() {
        assertFalse(changed(".moonx/tasks.yml"))
        assertTrue(changed(".moon/cachex/a.yml"))
        assertTrue(changed(".moon"))
    }

    @Test
    fun workspaceInTheFileSystemRootIsAChange() {
        // The path of the root of the file system, for example `/` or `C:/`, already ends with `/`.
        val fsRoot = root.root
        val atFsRoot = ConfigWatch(fsRoot, fsRoot)
        fun changedAtFsRoot(file: String) = changedAt(fsRoot.resolve(file), atFsRoot)
        assertTrue(changedAtFsRoot(".moon/workspace.yml"))
        assertTrue(changedAtFsRoot(".moon/tasks/all.yml"))
        assertTrue(changedAtFsRoot("apps/web/moon.yml"))
        assertFalse(changedAtFsRoot(".moon/cache/states/x.json"))
        assertFalse(changedAtFsRoot("README.md"))
    }
}
