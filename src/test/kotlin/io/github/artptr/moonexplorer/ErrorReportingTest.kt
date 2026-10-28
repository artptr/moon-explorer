package io.github.artptr.moonexplorer

import com.intellij.diagnostic.JetBrainsMarketplaceErrorReportSubmitter
import com.intellij.openapi.diagnostic.ErrorReportSubmitter
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ErrorReportingTest : BasePlatformTestCase() {

    fun testMarketplaceErrorHandlerIsRegistered() {
        val submitter = ErrorReportSubmitter.EP_NAME.extensionList
            .filterIsInstance<JetBrainsMarketplaceErrorReportSubmitter>()
            .singleOrNull { it.pluginDescriptor?.pluginId?.idString == "io.github.artptr.moonexplorer" }
        assertNotNull(
            "The errorHandler JetBrainsMarketplaceErrorReportSubmitter must be registered in plugin.xml",
            submitter,
        )
    }
}
