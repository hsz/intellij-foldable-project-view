package ski.chrzanow.foldableprojectview.sponsor

import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import ski.chrzanow.foldableprojectview.FoldableProjectViewBundle.message
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.SPONSOR_GITHUB_URL
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.SPONSOR_KOFI_URL
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.SPONSOR_PAYPAL_URL
import ski.chrzanow.foldableprojectview.settings.FoldableProjectSettings

/**
 * Counts the project sessions with folding enabled and, once the user has gotten value from the plugin,
 * shows a single dismissable notification inviting to sponsor its development, see [SponsorNotificationState].
 *
 * The notification is never shown on install, in headless or unit test mode, and at most once per IDE run.
 */
class SponsorNotificationActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val application = ApplicationManager.getApplication()
        if (application.isUnitTestMode || application.isHeadlessEnvironment || project.isDefault) {
            return
        }
        if (!project.service<FoldableProjectSettings>().foldingEnabled) {
            return
        }

        val state = SponsorNotificationState.getInstance()
        state.recordSession()
        if (!state.shouldShow()) {
            return
        }
        // Closing the balloon or ignoring it is the same as `Remind me later`.
        state.snooze()

        @Suppress("DialogTitleCapitalization")
        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(
                message("foldableProjectView.sponsor.notification.title"),
                message("foldableProjectView.sponsor.notification.content"),
                NotificationType.INFORMATION,
            )
            .setSuggestionType(true)
            .addAction(NotificationAction.createSimpleExpiring(message("foldableProjectView.sponsor.notification.github")) {
                BrowserUtil.browse(SPONSOR_GITHUB_URL)
            })
            .addAction(NotificationAction.createSimpleExpiring(message("foldableProjectView.sponsor.notification.paypal")) {
                BrowserUtil.browse(SPONSOR_PAYPAL_URL)
            })
            .addAction(NotificationAction.createSimpleExpiring(message("foldableProjectView.sponsor.notification.kofi")) {
                BrowserUtil.browse(SPONSOR_KOFI_URL)
            })
            .addAction(NotificationAction.createSimpleExpiring(message("foldableProjectView.sponsor.notification.later")) {
                state.snooze()
            })
            .addAction(NotificationAction.createSimpleExpiring(message("foldableProjectView.sponsor.notification.never")) {
                state.dismiss()
            })
            .notify(project)
    }

    companion object {

        /**
         * Id of the notification group declared in `plugin.xml`.
         */
        const val GROUP_ID = "Foldable Project View"
    }
}
