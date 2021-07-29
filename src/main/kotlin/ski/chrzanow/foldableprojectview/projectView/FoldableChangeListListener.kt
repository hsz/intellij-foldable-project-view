package ski.chrzanow.foldableprojectview.projectView

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeList
import com.intellij.openapi.vcs.changes.ChangeListListener
import ski.chrzanow.foldableprojectview.settings.FoldableProjectSettings

class FoldableChangeListListener(project: Project) : ChangeListListener {

    private val settings = project.service<FoldableProjectSettings>()
    private val view = ProjectView.getInstance(project)

    override fun changeListChanged(list: ChangeList?) {
        if (!settings.foldIgnoredFiles) {
            return
        }
        view.currentProjectViewPane?.updateFromRoot(true)
    }
}
