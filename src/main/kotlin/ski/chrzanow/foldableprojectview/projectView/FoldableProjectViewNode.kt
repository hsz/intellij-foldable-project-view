package ski.chrzanow.foldableprojectview.projectView

import com.intellij.icons.AllIcons.General.CollapseComponent
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.SimpleTextAttributes.GRAYED_ATTRIBUTES
import com.intellij.ui.SimpleTextAttributes.STYLE_PLAIN
import ski.chrzanow.foldableprojectview.settings.Rule

/**
 * Project View node that groups the [children] matched by the [rule].
 */
class FoldableProjectViewNode(
    project: Project,
    viewSettings: ViewSettings?,
    private val rule: Rule,
    private val children: Collection<AbstractTreeNode<*>>,
) : ProjectViewNode<Rule>(project, rule, viewSettings) {

    override fun update(presentation: PresentationData) {
        presentation.apply {
            val tooltip = rule.pattern.ifBlank { null }
            addText(ColoredFragment(rule.name, tooltip, SimpleTextAttributes(STYLE_PLAIN, rule.foreground)))
            addText(ColoredFragment(" ${children.size}", tooltip, GRAYED_ATTRIBUTES))
            setIcon(CollapseComponent)
        }
    }

    override fun getName() = rule.name

    override fun toString() = rule.name

    override fun computeBackgroundColor() = rule.background

    override fun getChildren() = children

    override fun contains(file: VirtualFile) = children.any {
        it is ProjectViewNode<*> && (it.virtualFile == file || it.contains(file))
    }

    override fun getTypeSortWeight(sortByType: Boolean) = TYPE_SORT_WEIGHT

    companion object {
        /** Sorts groups right after directories (weight `3`) and before plain files (weight `0`). */
        private const val TYPE_SORT_WEIGHT = 4
    }
}
