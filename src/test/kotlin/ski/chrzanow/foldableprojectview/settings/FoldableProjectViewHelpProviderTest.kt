package ski.chrzanow.foldableprojectview.settings

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.DOCUMENTATION_URL
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.HELP_TOPIC
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.PLUGIN_ID

class FoldableProjectViewHelpProviderTest : BasePlatformTestCase() {

    /**
     * The provider as the platform instantiates it from `plugin.xml`: the plugin descriptor is injected
     * and defines the help topic prefix.
     */
    private val provider: FoldableProjectViewHelpProvider
        get() = FoldableProjectViewHelpProvider().apply {
            setPluginDescriptor(requireNotNull(PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID))))
        }

    fun `test provider handles the topics prefixed with the plugin id`() {
        assertEquals("$PLUGIN_ID.", provider.helpTopicPrefix)
    }

    fun `test settings help topic is routed to the provider and resolves to the documentation`() {
        assertTrue(HELP_TOPIC.startsWith(provider.helpTopicPrefix))
        assertEquals(DOCUMENTATION_URL, provider.getHelpPageUrl(HELP_TOPIC))
        assertEquals(HELP_TOPIC, FoldableProjectViewConfigurable(project).helpTopic)
    }

    fun `test unknown topics are not handled`() {
        assertNull(provider.getHelpPageUrl("$PLUGIN_ID.unknown"))
    }
}
