package ski.chrzanow.foldableprojectview.settings

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.impl.ProjectViewTree
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessModuleDir
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vcs.changes.ignore.cache.PatternCache
import com.intellij.openapi.vcs.changes.ignore.lang.Syntax
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.layout.CCFlags
import com.intellij.ui.layout.applyToComponent
import com.intellij.ui.layout.panel
import com.intellij.util.IconUtil
import com.intellij.util.PlatformIcons
import com.intellij.util.ui.tree.AbstractFileTreeTable
import ski.chrzanow.foldableprojectview.FoldableProjectViewBundle.message
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel

class FoldableProjectViewConfigurable(private val project: Project) : SearchableConfigurable {

    private val settings = project.service<FoldableProjectSettings>()
    private val panel = panel {
        row {
            checkBox(
                message("foldableProjectView.settings.foldingEnabled"),
                settings::foldingEnabled,
            )
                .comment(message("foldableProjectView.settings.foldingEnabled.comment"), 120)
                .applyToComponent { setMnemonic('e') }
        }

        row {
            checkBox(
                message("foldableProjectView.settings.foldDirectories"),
                settings::foldDirectories,
            )
                .comment(message("foldableProjectView.settings.foldDirectories.comment"), 120)
                .applyToComponent { setMnemonic('d') }
        }

        // TODO: [X] Fold submodules

        row {
            checkBox(
                message("foldableProjectView.settings.hideEmptyGroups"),
                settings::hideEmptyGroups,
            )
                .comment(message("foldableProjectView.settings.hideEmptyGroups.comment"), 120)
                .applyToComponent { setMnemonic('h') }
        }

        row {
            checkBox(
                message("foldableProjectView.settings.caseInsensitive"),
                settings::caseInsensitive,
            )
                .comment(message("foldableProjectView.settings.caseInsensitive.comment"), 120)
                .applyToComponent { setMnemonic('c') }
        }

        titledRow("Folding rules") {
            row {
                expandableTextField(
                    { settings.patterns ?: "" },
                    { settings.patterns = it },
                )
                    .comment(message("foldableProjectView.settings.patterns.comment"), 120)
                    .constraints(CCFlags.growX)
                    .applyToComponent {
                        emptyText.text = message("foldableProjectView.settings.patterns")
                    }
            }

            row(message("foldableProjectView.settings.preview")) {
                preview()
            }
        }
    }

    companion object {
        const val ID = "ski.chrzanow.foldableprojectview.options.FoldableProjectViewConfigurable"
    }

    override fun getId() = ID

    override fun getDisplayName() = message("foldableProjectView.name")

    override fun createComponent() = panel

    override fun isModified() = panel.isModified()

    override fun apply() {
        panel.apply()
        ApplicationManager.getApplication()
            .messageBus
            .syncPublisher(FoldableProjectSettingsListener.TOPIC)
            .settingsChanged(settings)
    }

    override fun reset() = panel.reset()

    private fun preview(): JComponent {
        val patternCache = PatternCache.getInstance(project)
        val fileIndex = ProjectRootManager.getInstance(project).fileIndex
        val modules = ModuleManager.getInstance(project).modules.map(Module::guessModuleDir)
        val rootNode = AbstractFileTreeTable.ProjectRootNode(project) { true }
        val model = DefaultTreeModel(rootNode)
        val tree = ProjectViewTree(model).apply {
            isRootVisible = false
            showsRootHandles = true
            cellRenderer = object : DefaultTreeCellRenderer() {
                private val myComponent = SimpleColoredComponent()

                override fun getTreeCellRendererComponent(
                    tree: JTree,
                    value: Any,
                    sel: Boolean,
                    expanded: Boolean,
                    leaf: Boolean,
                    row: Int,
                    hasFocus: Boolean,
                ) = myComponent.apply {
                    val file = (value as AbstractFileTreeTable.FileNode).`object`

                    clear()

                    val name = file.name.caseInsensitive()
                    val isDirectory = file.isDirectory
                    val isModule = modules.any { it == file }
                    val isMatched = settings.patterns
                        .caseInsensitive()
                        .split(' ')
                        .any { patternCache?.createPattern(it, Syntax.GLOB)?.matcher(name)?.matches() ?: false }

                    append(file.name, when {
                        isModule -> SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
                        isDirectory && !settings.foldDirectories -> SimpleTextAttributes.REGULAR_ATTRIBUTES
                        isMatched -> SimpleTextAttributes.GRAY_ATTRIBUTES
                        else -> SimpleTextAttributes.REGULAR_ATTRIBUTES
                    })

                    icon = when {
                        !isDirectory -> IconUtil.getIcon(file, 0, null)
                        fileIndex.isExcluded(file) -> AllIcons.Modules.ExcludeRoot
                        else -> PlatformIcons.FOLDER_ICON
                    }
                }

                private fun String?.caseInsensitive() = when {
                    this == null -> ""
                    settings.caseInsensitive -> toLowerCase()
                    else -> this
                }
            }

            expandRow(0)
        }

        return ScrollPaneFactory.createScrollPane(tree)
    }
}
