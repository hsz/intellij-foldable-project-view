package ski.chrzanow.foldableprojectview.projectView

import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.ide.projectView.impl.nodes.AbstractModuleNode
import com.intellij.ide.projectView.impl.nodes.AbstractProjectNode
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
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.VcsIgnoreManager
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
        if (!state.foldingEnabled || parent !is ProjectViewNode<*>) {
            return children
        }
        val isRoot = parent.isModuleRoot()
        if (!isRoot) {
            return children
        }

        val remaining = children.toMutableList()
        val groups = mutableListOf<FoldableProjectViewNode>()

        fun fold(rule: Rule, predicate: (AbstractTreeNode<*>) -> Boolean) {
            val matched = remaining.filter { it.isFoldable(state) && predicate(it) }
            remaining -= matched

            if (!state.hideAllGroups && (matched.isNotEmpty() || (isRoot && !state.hideEmptyGroups))) {
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
            fold(Rule(message("foldableProjectView.ignoredFiles"), "")) { node -> node.isIgnored() }
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

    private fun ProjectViewNode<*>.isDirectory() = this is PsiDirectoryNode || virtualFile?.isDirectory == true

    private fun ProjectViewNode<*>.isModuleRoot(): Boolean {
        if (this !is PsiDirectoryNode && virtualFile?.isDirectory != true) {
            return false
        }
        if (parent is ProjectViewProjectNode || parent is AbstractProjectNode || parent is AbstractModuleNode) {
            return true
        }
        // Custom scope views: the root directory is attached to a non-directory (scope/root) node.
        val parentNode = parent
        if (parentNode is ProjectViewNode<*> && parentNode !is PsiDirectoryNode &&
            parentNode.virtualFile?.isDirectory != true
        ) {
            return true
        }
        val file = virtualFile ?: return false
        if (ProjectFileIndex.getInstance(project).getContentRootForFile(file) == file) {
            return true
        }
        return ModuleUtilCore.findModuleForFile(file, project)?.guessModuleDir() == file
    }

    private fun AbstractTreeNode<*>.isFoldable(state: FoldableProjectState) = when (this) {
        is PsiDirectoryNode -> state.matchDirectories
        is PsiFileNode -> true
        is ProjectViewNode<*> -> virtualFile?.let { !it.isDirectory || state.matchDirectories } ?: false
        else -> false
    }

    private fun AbstractTreeNode<*>.isIgnored(): Boolean {
        if (this !is ProjectViewNode<*>) {
            return false
        }
        if (fileStatus == FileStatus.IGNORED) {
            return true
        }
        val file = virtualFile ?: return false
        return ChangeListManager.getInstance(project).isIgnoredFile(file) ||
            VcsIgnoreManager.getInstance(project).isPotentiallyIgnoredFile(file)
    }

    private val AbstractTreeNode<*>.virtualFile
        get() = (this as? ProjectViewNode<*>)?.virtualFile
}
