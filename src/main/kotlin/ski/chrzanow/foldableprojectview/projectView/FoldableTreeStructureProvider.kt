package ski.chrzanow.foldableprojectview.projectView

import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.ide.projectView.impl.nodes.ProjectViewProjectNode
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.components.service
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessModuleDir
import com.intellij.openapi.vcs.FileStatus
import ski.chrzanow.foldableprojectview.FoldableProjectViewBundle.message
import ski.chrzanow.foldableprojectview.settings.FoldableProjectSettings
import ski.chrzanow.foldableprojectview.settings.FoldableProjectState
import ski.chrzanow.foldableprojectview.settings.Rule

/**
 * Folds the root elements of the project modules into the [FoldableProjectViewNode] groups
 * according to the configured [Rule]s.
 */
class FoldableTreeStructureProvider(private val project: Project) : TreeStructureProvider, DumbAware {

    private var stateProvider: () -> FoldableProjectState = { project.service<FoldableProjectSettings>() }

    init {
        // Make sure the Project View gets refreshed when the settings or file statuses change.
        if (!project.isDefault) {
            project.service<FoldableProjectViewRefresher>()
        }
    }

    override fun modify(
        parent: AbstractTreeNode<*>,
        children: MutableCollection<AbstractTreeNode<*>>,
        viewSettings: ViewSettings?,
    ): Collection<AbstractTreeNode<*>> {
        val state = stateProvider()
        if (!state.foldingEnabled || parent !is PsiDirectoryNode || !parent.isModuleRoot()) {
            return children
        }

        val remaining = children.toMutableList()
        val groups = mutableListOf<FoldableProjectViewNode>()

        fun fold(rule: Rule, predicate: (AbstractTreeNode<*>) -> Boolean) {
            val matched = remaining.filter { it.isFoldable(state) && predicate(it) }
            remaining -= matched

            if (!state.hideAllGroups && (matched.isNotEmpty() || !state.hideEmptyGroups)) {
                groups += FoldableProjectViewNode(project, viewSettings, rule, matched)
            }
        }

        state.rules
            .filter { it.pattern.isNotBlank() }
            .forEach { rule ->
                val scope = rule.getScope(project, state)
                fold(rule) { node -> node.virtualFile?.let(scope::contains) ?: false }
            }

        if (state.foldIgnoredFiles) {
            fold(Rule(message("foldableProjectView.ignoredFiles"), "")) { node -> node.fileStatus == FileStatus.IGNORED }
        }

        return remaining + groups
    }

    /**
     * Makes the provider use the given [property] as the settings source and refresh the [pane] when it changes.
     * Used to render the live preview in the plugin settings.
     */
    fun withPreview(pane: ProjectViewPane, property: ObservableProperty<out FoldableProjectState>) = apply {
        stateProvider = property::get
        property.afterChange(pane) {
            pane.updateFromRoot(true)
        }
    }

    private fun PsiDirectoryNode.isModuleRoot(): Boolean {
        if (parent is ProjectViewProjectNode) {
            return true
        }
        val file = virtualFile ?: return false
        return ModuleUtilCore.findModuleForFile(file, project)?.guessModuleDir() == file
    }

    private fun AbstractTreeNode<*>.isFoldable(state: FoldableProjectState) = when (this) {
        is PsiDirectoryNode -> state.matchDirectories
        is PsiFileNode -> true
        else -> false
    }

    private val AbstractTreeNode<*>.virtualFile
        get() = (this as? ProjectViewNode<*>)?.virtualFile
}
