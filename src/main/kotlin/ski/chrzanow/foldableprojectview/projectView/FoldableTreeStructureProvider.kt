package ski.chrzanow.foldableprojectview.projectView

import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.components.service
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessModuleDir
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.FileStatusListener
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vcs.changes.ignore.cache.PatternCache
import com.intellij.openapi.vcs.changes.ignore.lang.Syntax
import ski.chrzanow.foldableprojectview.or
import ski.chrzanow.foldableprojectview.settings.FoldableProjectSettings
import ski.chrzanow.foldableprojectview.settings.FoldableProjectSettingsListener

class FoldableTreeStructureProvider(private val project: Project) : TreeStructureProvider {

    private val settings by lazy { project.service<FoldableProjectSettings>() }
    private val patternCache = PatternCache.getInstance(project)
    private var previewProjectViewPane: ProjectViewPane? = null
    private var previewGraphProperty: ObservableMutableProperty<FoldableProjectSettings>? = null
    private val state get() = previewGraphProperty?.get() ?: settings

    init {
        project.messageBus
            .connect(project)
            .subscribe(FoldableProjectSettingsListener.TOPIC, FoldableProjectSettingsListener {
                refreshProjectView()
            })

        FileStatusManager.getInstance(project).addFileStatusListener(object : FileStatusListener {
            override fun fileStatusesChanged() {
                if (state.foldIgnoredFiles) {
                    refreshProjectView()
                }
            }
        }, project)
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
            else -> {
                val matched = mutableSetOf<AbstractTreeNode<*>>()

                // TODO: allow for duplicates? – checkbox in settings; otherwise the first rule will take the precedence
                val folders = state.rules.mapNotNull { (name, pattern) ->
                    (children - matched)
                        .match(pattern)
                        .also { matched.addAll(it) }
                        .takeUnless { state.hideAllGroups || (state.hideEmptyGroups && matched.isEmpty()) }
                        ?.run {
                            matched.addAll(this)
                            FoldableProjectViewNode(project, viewSettings, name, toSet())
                        }
                }

                children - matched + folders
            }
        }
    }

    fun withProjectViewPane(projectViewPane: ProjectViewPane) = apply {
        previewProjectViewPane = projectViewPane
    }

    fun withState(property: ObservableMutableProperty<FoldableProjectSettings>) = apply {
        previewGraphProperty = property.also {
            it.afterChange {
                refreshProjectView()
            }
        }
    }

    private fun isModule(node: PsiDirectoryNode, project: Project) =
        node.virtualFile
            ?.let { ModuleUtil.findModuleForFile(it, project)?.guessModuleDir() == it }
            ?: false

    private fun Collection<AbstractTreeNode<*>>.match(patterns: String) = this
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
                patterns
                    .caseInsensitive()
                    .split(' ')
                    .any { pattern ->
                        patternCache
                            ?.createPattern(pattern, Syntax.GLOB)
                            ?.matcher(name)
                            ?.matches()
                            .or(false)
                    }
            }.or(state.foldIgnoredFiles and (it.fileStatus.equals(FileStatus.IGNORED)))
        }

    private fun String?.caseInsensitive() = when {
        this == null -> ""
        state.caseInsensitive -> lowercase()
        else -> this
    }

    private fun refreshProjectView() = previewProjectViewPane
        .or { ProjectView.getInstance(project).currentProjectViewPane }
        ?.updateFromRoot(true)
}
