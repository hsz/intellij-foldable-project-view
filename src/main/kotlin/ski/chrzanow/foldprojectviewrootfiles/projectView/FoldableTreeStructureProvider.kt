package ski.chrzanow.foldprojectviewrootfiles.projectView

import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.guessProjectDir
import ski.chrzanow.foldprojectviewrootfiles.isFoldingEnabled


class FoldableTreeStructureProvider : TreeStructureProvider {

    override fun modify(
        parent: AbstractTreeNode<*>,
        children: MutableCollection<AbstractTreeNode<*>>,
        settings: ViewSettings?,
    ): Collection<AbstractTreeNode<*>> {
        val project = parent.project ?: return children
        if (!isFoldingEnabled()) {
            return children
        }
        if (parent !is PsiDirectoryNode) {
            return children
        }
        if (parent.virtualFile != project.guessProjectDir()?.canonicalFile) {
            return children
        }

        val rootFiles = children.filterIsInstance(PsiFileNode::class.java)
        val node = FoldableProjectViewNode(project, settings, rootFiles)
        return children - rootFiles + node
    }
}
