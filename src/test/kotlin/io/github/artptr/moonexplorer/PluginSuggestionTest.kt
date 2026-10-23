package io.github.artptr.moonexplorer

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PluginSuggestionTest : BasePlatformTestCase() {

    fun testNpmDependencySupportIsRegistered() {
        // The bean class of this extension point is internal in the platform. Read its public fields by name.
        val entries = ApplicationManager.getApplication().extensionArea
            .getExtensionPoint<Any>("com.intellij.dependencySupport")
            .extensionList
            .map { bean -> listOf("kind", "coordinate", "displayName").map { bean.javaClass.getField(it).get(bean) } }
        assertTrue(
            "The dependencySupport entry for @moonrepo/cli must be registered in plugin.xml",
            listOf("javascript", "npm:@moonrepo/cli", "moon") in entries,
        )
    }
}
