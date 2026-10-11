package ski.chrzanow.foldableprojectview.settings

import com.intellij.openapi.help.WebHelpProvider
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.DOCUMENTATION_URL
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.HELP_TOPIC

/**
 * Resolves the plugin help topics (the `?` button of the settings page, see [FoldableProjectViewConfigurable]) to the README,
 * as there is no page for the plugin on the JetBrains help site.
 */
class FoldableProjectViewHelpProvider : WebHelpProvider() {

    override fun getHelpPageUrl(helpTopicId: String) = when (helpTopicId) {
        HELP_TOPIC -> DOCUMENTATION_URL
        else -> null
    }
}
