package ski.chrzanow.foldableprojectview.projectView

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatusListener
import com.intellij.openapi.vcs.FileStatusManager
import ski.chrzanow.foldableprojectview.settings.FoldableProjectSettings
import ski.chrzanow.foldableprojectview.settings.FoldableProjectSettingsListener

/**
 * Refreshes the Project View whenever the plugin settings or file statuses (e.g., ignored files) change.
 */
@Service(Service.Level.PROJECT)
class FoldableProjectViewRefresher(private val project: Project) : Disposable {

    private val settings by lazy { project.service<FoldableProjectSettings>() }

    init {
        project.messageBus
            .connect(this)
            .subscribe(FoldableProjectSettingsListener.TOPIC, FoldableProjectSettingsListener { refresh() })

        FileStatusManager.getInstance(project).addFileStatusListener(object : FileStatusListener {
            override fun fileStatusesChanged() {
                if (settings.foldingEnabled && settings.foldIgnoredFiles) {
                    refresh()
                }
            }
        }, this)
    }

    fun refresh() {
        ApplicationManager.getApplication().invokeLater({
            ProjectView.getInstance(project).currentProjectViewPane?.updateFromRoot(true)
        }, project.disposed)
    }

    override fun dispose() = Unit
}
