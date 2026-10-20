package io.github.artptr.moonexplorer.model

import io.github.artptr.moonexplorer.testData.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Path

class ProjectFinderTest {

    private val root = TestData.workspaceRoot

    private val recorded = TestData.recordedTasks().sources

    private fun find(file: String, sources: List<ProjectSource> = recorded) = findProjectForFile(root, root.resolve(file), sources)?.id

    @Test
    fun fileInAProjectGivesTheProject() {
        assertEquals("web", find("apps/web/moon.yml"))
        assertEquals("web", find("apps/web/src/main.ts"))
    }

    @Test
    fun fileInANestedProjectGivesTheNestedProject() {
        assertEquals("icons", find("packages/ui/icons/moon.yml"))
        assertEquals("icons", find("packages/ui/icons/svg/arrow.svg"))
    }

    @Test
    fun fileInTheParentOfANestedProjectGivesTheParentProject() {
        assertEquals("ui", find("packages/ui/moon.yml"))
        assertEquals("ui", find("packages/ui/icons-old/arrow.svg"))
    }

    @Test
    fun projectDirectoryGivesTheProject() {
        assertEquals("web", find("apps/web"))
    }

    @Test
    fun fileInAProjectWithoutTasksGivesTheProject() {
        assertEquals("config", find("packages/config/moon.yml"))
    }

    @Test
    fun directoryWithTheSameStartIsNotInTheProject() {
        assertNull(find("apps/web-admin/index.ts"))
    }

    @Test
    fun fileThatIsNotInAProjectGivesNull() {
        assertNull(find("package.json"))
        assertNull(find(".moon/workspace.yml"))
    }

    @Test
    fun fileOutsideTheWorkspaceGivesNull() {
        assertNull(findProjectForFile(root, root.parent.resolve("other/apps/web/moon.yml"), recorded))
    }

    @Test
    fun pathWithParentSegmentsIsNormalized() {
        assertEquals("ui", find("apps/web/../../packages/ui/moon.yml"))
        assertNull(find("apps/web/../readme.md"))
    }

    @Test
    fun rootProjectContainsAllFilesWithTheLowestPriority() {
        val sources = recorded + ProjectSource("root", ".")
        assertEquals("root", find("package.json", sources))
        assertEquals("web", find("apps/web/moon.yml", sources))
    }

    @Test
    fun orderOfTheSourcesDoesNotChangeTheResult() {
        assertEquals("icons", find("packages/ui/icons/moon.yml", recorded.reversed()))
    }

    @Test
    fun noSourcesGivesNull() {
        assertNull(findProjectForFile(root, Path.of(root.toString(), "apps", "web", "moon.yml"), emptyList()))
    }
}
