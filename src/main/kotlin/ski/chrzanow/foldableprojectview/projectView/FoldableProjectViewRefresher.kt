package ski.chrzanow.foldableprojectview.projectView

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatusListener
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import kotlinx.coroutines.CoroutineScope
import ski.chrzanow.foldableprojectview.Debouncer
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.REFRESH_DELAY
import ski.chrzanow.foldableprojectview.rules.FoldableRulesResolver
import ski.chrzanow.foldableprojectview.settings.FoldableProjectSettingsListener
import ski.chrzanow.foldableprojectview.settings.ignoredFilesRule
import ski.chrzanow.foldableprojectview.settings.patternsFilePaths

/**
 * Refreshes the Project View whenever the plugin settings, file statuses (e.g., ignored files), the rule set files
 * (`.foldableprojectview.yaml`, the user `rules.yaml` and presets), or the patterns files used by the folding rules
 * (e.g., `.foldignore`) change.
 *
 * The VCS fires the file status events in bursts (e.g., during a refresh or a checkout), so the refreshes are coalesced:
 * the Project View is restructured once, [REFRESH_DELAY] after the last event.
 */
@Service(Service.Level.PROJECT)
class FoldableProjectViewRefresher(private val project: Project, coroutineScope: CoroutineScope) : Disposable {

    private val resolver by lazy { project.service<FoldableRulesResolver>() }

    private val debouncer = Debouncer(coroutineScope, REFRESH_DELAY) {
        ProjectView.getInstance(project).currentProjectViewPane?.updateFromRoot(true)
    }

    init {
        project.messageBus
            .connect(this)
            .apply {
                subscribe(FoldableProjectSettingsListener.TOPIC, FoldableProjectSettingsListener { refresh() })
                subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
                    override fun after(events: List<VFileEvent>) {
                        // The rule set files are watched even while folding is off, as a file may switch it on.
                        if (events.any { resolver.affects(it.path) }) {
                            refresh()
                            return
                        }
                        val state = resolver.resolve()
                        if (!state.foldingEnabled) {
                            return
                        }
                        val paths = state.patternsFilePaths
                        if (paths.isNotEmpty() && events.any { it.affectsPatternsFile(paths) }) {
                            refresh()
                        }
                    }
                })
            }

        FileStatusManager.getInstance(project).addFileStatusListener(object : FileStatusListener {
            override fun fileStatusesChanged() {
                val state = resolver.resolve()
                if (state.foldingEnabled && state.ignoredFilesRule?.enabled == true) {
                    refresh()
                }
            }
        }, this)
    }

    /**
     * Schedules a refresh of the Project View; the requests made within [REFRESH_DELAY] are merged into one.
     */
    fun refresh() = debouncer.request()

    override fun dispose() = Unit

    companion object {

        /**
         * Whether the event concerns a file with one of the [paths] relative to a content root, see [patternsFilePaths].
         */
        fun VFileEvent.affectsPatternsFile(paths: Collection<String>) = paths.any { path.endsWith("/$it") }
    }
}
