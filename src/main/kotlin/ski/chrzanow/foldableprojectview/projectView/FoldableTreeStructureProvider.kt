package ski.chrzanow.foldableprojectview.projectView

import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.components.service
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessModuleDir
import com.intellij.openapi.vcs.changes.ignore.cache.PatternCache
import com.intellij.openapi.vcs.changes.ignore.lang.Syntax
import ski.chrzanow.foldableprojectview.settings.FoldableProjectSettings
import ski.chrzanow.foldableprojectview.settings.FoldableProjectSettingsListener
import ski.chrzanow.foldableprojectview.settings.FoldableProjectState

class FoldableTreeStructureProvider(project: Project) : TreeStructureProvider {

    private val settings = project.service<FoldableProjectSettings>()
    private val patternCache = PatternCache.getInstance(project)
    private var previewState: FoldableProjectState? = null
    private val state get() = previewState ?: settings

    init {
        val view = ProjectView.getInstance(project)

        project.messageBus
            .connect(project)
            .subscribe(FoldableProjectSettingsListener.TOPIC, object : FoldableProjectSettingsListener {
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

        return when {
            !state.foldingEnabled -> children
            parent !is PsiDirectoryNode -> children
            !isModule(parent, project) -> children
            else -> children.match().let { matched ->
                when {
                    state.hideAllGroups -> children - matched
                    state.hideEmptyGroups && matched.isEmpty() -> children
                    else -> children - matched + FoldableProjectViewNode(project, viewSettings, matched)
                }
            }
        }
    }

    private fun isModule(node: PsiDirectoryNode, project: Project) = node.virtualFile?.let {
        ModuleUtil.findModuleForFile(it, project)?.guessModuleDir() == it
    } ?: false

    private fun MutableCollection<AbstractTreeNode<*>>.match() = this
        .filter {
            when (it) {
                is PsiDirectoryNode -> state.foldDirectories
                is PsiFileNode -> true
                else -> false
            }
        }
        .filter {
            when (it) {
                is ProjectViewNode -> it.virtualFile?.name ?: it.name
                else -> it.name
            }.caseInsensitive().let { name ->
                state.patterns
                    .caseInsensitive()
                    .split(' ')
                    .any { pattern -> patternCache?.createPattern(pattern, Syntax.GLOB)?.matcher(name)?.matches() ?: false }
            }
        }

    private fun String?.caseInsensitive() = when {
        this == null -> ""
        state.caseInsensitive -> toLowerCase()
        else -> this
    }

    fun withState(state: FoldableProjectState) {
        previewState = state
    }
}
