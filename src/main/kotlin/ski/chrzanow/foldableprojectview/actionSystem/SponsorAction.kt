package ski.chrzanow.foldableprojectview.actionSystem

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.SPONSOR_GITHUB_URL

/**
 * Opens the GitHub Sponsors page of the plugin. Available in the Project View options menu, next to the folding toggles,
 * so the sponsorship is always one click away for the users who actually use the plugin, without any nagging.
 */
class SponsorAction : AnAction(), DumbAware {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(event: AnActionEvent) = BrowserUtil.browse(SPONSOR_GITHUB_URL)
}
