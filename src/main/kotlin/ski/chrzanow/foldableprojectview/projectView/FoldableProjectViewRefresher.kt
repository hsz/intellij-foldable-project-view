package ski.chrzanow.foldableprojectview.projectView

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatusListener
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import ski.chrzanow.foldableprojectview.settings.FoldableProjectSettings
import ski.chrzanow.foldableprojectview.settings.FoldableProjectSettingsListener
import ski.chrzanow.foldableprojectview.settings.foldIgnoredFiles
import ski.chrzanow.foldableprojectview.settings.patternsFilePaths

/**
 * Refreshes the Project View whenever the plugin settings, file statuses (e.g., ignored files),
 * or the patterns files used by the folding rules (e.g., `.foldignore`) change.
 */
@Service(Service.Level.PROJECT)
class FoldableProjectViewRefresher(private val project: Project) : Disposable {

    private val settings by lazy { project.service<FoldableProjectSettings>() }

    init {
        project.messageBus
            .connect(this)
            .apply {
                subscribe(FoldableProjectSettingsListener.TOPIC, FoldableProjectSettingsListener { refresh() })
                subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
                    override fun after(events: List<VFileEvent>) {
                        if (!settings.foldingEnabled) {
                            return
                        }
                        val paths = settings.patternsFilePaths
                        if (paths.isNotEmpty() && events.any { it.affectsPatternsFile(paths) }) {
                            refresh()
                        }
                    }
                })
            }

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

    companion object {

        /**
         * Whether the event concerns a file with one of the [paths] relative to a content root, see [patternsFilePaths].
         */
        fun VFileEvent.affectsPatternsFile(paths: Collection<String>) = paths.any { path.endsWith("/$it") }
    }
}
