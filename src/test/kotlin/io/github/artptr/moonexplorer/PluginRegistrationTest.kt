package io.github.artptr.moonexplorer

import com.intellij.diagnostic.JetBrainsMarketplaceErrorReportSubmitter
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.ErrorReportSubmitter
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.artptr.moonexplorer.run.MoonRunConfigurationType

/** Examines the entries of `plugin.xml` that have no other test. */
class PluginRegistrationTest : BasePlatformTestCase() {

    fun testMarketplaceErrorHandlerIsRegistered() {
        val submitter = ErrorReportSubmitter.EP_NAME.extensionList
            .filterIsInstance<JetBrainsMarketplaceErrorReportSubmitter>()
            .singleOrNull { it.pluginDescriptor?.pluginId?.idString == "io.github.artptr.moonexplorer" }
        assertNotNull(
            "The errorHandler JetBrainsMarketplaceErrorReportSubmitter must be registered in plugin.xml",
            submitter,
        )
    }

    fun testRunConfigurationTypeIsRegistered() {
        assertTrue(ConfigurationType.CONFIGURATION_TYPE_EP.extensionList.any { it is MoonRunConfigurationType })
    }

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
