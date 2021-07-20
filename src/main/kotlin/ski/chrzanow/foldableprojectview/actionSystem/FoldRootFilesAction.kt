package ski.chrzanow.foldableprojectview.actionSystem

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.actionSystem.ToggleOptionAction
import com.intellij.openapi.project.DumbAware
import ski.chrzanow.foldableprojectview.isFoldingEnabled
import ski.chrzanow.foldableprojectview.setFoldingEnabled
import java.util.function.Function

class FoldRootFilesAction : DumbAware, ToggleOptionAction(Function {
    object : Option {

        override fun isSelected() = isFoldingEnabled()

        override fun setSelected(selected: Boolean) {
            val updated = selected != isSelected

            setFoldingEnabled(selected)

            if (updated) {
                val view = ProjectView.getInstance(it.project)
                view.currentProjectViewPane?.updateFromRoot(true)
            }
        }
    }
})
