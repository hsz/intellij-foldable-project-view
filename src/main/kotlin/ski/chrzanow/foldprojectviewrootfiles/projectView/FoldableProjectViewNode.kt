package ski.chrzanow.foldprojectviewrootfiles.projectView

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.SimpleTextAttributes
import ski.chrzanow.foldprojectviewrootfiles.FoldProjectViewRootFilesBundle

class FoldableProjectViewNode(
    project: Project,
    settings: ViewSettings?,
    private val children: List<PsiFileNode>,
) : ProjectViewNode<String>(project, "Foldable ProjectView", settings) {

    override fun update(presentation: PresentationData) {
        presentation.apply {
            val text = FoldProjectViewRootFilesBundle.message("node.projectview.foldable", children.size)
            val toolTip = children.mapNotNull { it.name }.joinToString(", ")
            val textAttributes = SimpleTextAttributes.GRAY_SMALL_ATTRIBUTES
            coloredText += ColoredFragment(text, toolTip, textAttributes)
        }
    }

    override fun getChildren() = children

    override fun contains(file: VirtualFile) = children.firstOrNull { it.virtualFile == file } != null
}
