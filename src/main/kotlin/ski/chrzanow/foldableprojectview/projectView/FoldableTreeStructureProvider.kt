package ski.chrzanow.foldableprojectview.projectView

import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ignore.cache.PatternCache
import com.intellij.openapi.vcs.changes.ignore.lang.Syntax
import ski.chrzanow.foldableprojectview.isFoldingEnabled
import ski.chrzanow.foldableprojectview.options.FoldableProjectConfiguration

class FoldableTreeStructureProvider(private val project: Project) : TreeStructureProvider {

    private val settings = project.service<FoldableProjectConfiguration>()
    private val patternCache = PatternCache.getInstance(project)

    init {
        settings.patternsProperty.afterChange {
            val view = ProjectView.getInstance(project)
            view.currentProjectViewPane?.updateFromRoot(true)
        }
    }

    override fun modify(
        parent: AbstractTreeNode<*>,
        children: MutableCollection<AbstractTreeNode<*>>,
        viewSettings: ViewSettings?,
    ): Collection<AbstractTreeNode<*>> {
        val project = parent.project ?: return children
        if (!isFoldingEnabled()) {
            return children
        }
        if (parent !is PsiDirectoryNode) {
            return children
        }
//        if (parent.virtualFile != project.guessProjectDir()?.canonicalFile) {
//            return children
//        }

//            .filterIsInstance(PsiFileNode::class.java)
        val rootFiles = children.filter { node ->
            val name = when (node) {
                is ProjectViewNode -> node.virtualFile?.name ?: node.name
                else -> node.name
            } ?: ""

            settings.patterns.any { pattern ->
                patternCache.createPattern(pattern, Syntax.GLOB)?.matcher(name)?.matches() ?: false
            }
        }
        val node = FoldableProjectViewNode(project, viewSettings, rootFiles)
        return children - rootFiles + node
    }
}
