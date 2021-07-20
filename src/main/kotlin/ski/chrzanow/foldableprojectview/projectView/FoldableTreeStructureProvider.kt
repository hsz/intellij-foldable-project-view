package ski.chrzanow.foldableprojectview.projectView

import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ignore.cache.PatternCache
import com.intellij.openapi.vcs.changes.ignore.lang.Syntax
import ski.chrzanow.foldableprojectview.settings.FoldableProjectSettings
import ski.chrzanow.foldableprojectview.settings.FoldableProjectSettingsListener

class FoldableTreeStructureProvider(project: Project) : TreeStructureProvider {

    private val settings = project.service<FoldableProjectSettings>()
    private val patternCache = PatternCache.getInstance(project)
    private val connection = project.messageBus.connect(project)
    private val view = ProjectView.getInstance(project)

    init {
        connection.subscribe(FoldableProjectSettingsListener.TOPIC, object : FoldableProjectSettingsListener {
            override fun settingsChanged(settings: FoldableProjectSettings) {
                view.currentProjectViewPane?.updateFromRoot(true)
            }
        })
    }

    override fun modify(
        parent: AbstractTreeNode<*>,
        children: MutableCollection<AbstractTreeNode<*>>,
        viewSettings: ViewSettings?,
    ): Collection<AbstractTreeNode<*>> {
        val project = parent.project ?: return children

        if (!settings.foldingEnabled) {
            return children
        }
        if (parent !is PsiDirectoryNode) {
            return children
        }
//        if (parent.virtualFile != project.guessProjectDir()?.canonicalFile) {
//            return children
//        }

//            .filterIsInstance(PsiFileNode::class.java)
        val rootFiles = children
            .filter {
                when (it) {
                    is PsiDirectoryNode -> settings.foldDirectories
                    is PsiFileNode -> true
                    else -> false
                }
            }
            .filter { node ->
                val name = when (node) {
                    is ProjectViewNode -> node.virtualFile?.name ?: node.name
                    else -> node.name
                } ?: ""

                settings.patterns?.split(' ')?.any { pattern ->
                    patternCache?.createPattern(pattern, Syntax.GLOB)?.matcher(name)?.matches() ?: false
                } ?: false
            }

        val node = FoldableProjectViewNode(project, viewSettings, rootFiles)
        return children - rootFiles + node
    }
}
