package ski.chrzanow.foldprojectviewrootfiles.actionSystem

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import ski.chrzanow.foldprojectviewrootfiles.isFoldingEnabled
import ski.chrzanow.foldprojectviewrootfiles.setFoldingEnabled

class FoldRootFilesAction : ToggleAction() {

    override fun isSelected(e: AnActionEvent) = isFoldingEnabled()

    override fun setSelected(e: AnActionEvent, state: Boolean) = setFoldingEnabled(state)
}
