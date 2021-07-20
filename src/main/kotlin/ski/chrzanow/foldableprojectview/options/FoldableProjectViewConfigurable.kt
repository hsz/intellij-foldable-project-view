package ski.chrzanow.foldableprojectview.options

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.impl.ProjectViewTree
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessModuleDir
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vcs.changes.ignore.cache.PatternCache
import com.intellij.openapi.vcs.changes.ignore.lang.Syntax
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBLabel
import com.intellij.util.IconUtil
import com.intellij.util.PlatformIcons
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.AbstractFileTreeTable
import ski.chrzanow.foldableprojectview.FoldableProjectViewBundle
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.event.DocumentEvent
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel

class FoldableProjectViewConfigurable(private val project: Project) : SearchableConfigurable {

    private val builder = FormBuilder.createFormBuilder()
    private val settings = project.service<FoldableProjectConfiguration>()

    companion object {
        const val ID = "ski.chrzanow.foldableprojectview.options.FoldableProjectViewConfigurable"
    }

    private val patterns = RawCommandLineEditor().apply {
        textField.toolTipText = FoldableProjectViewBundle.message("foldableProjectView.pattern.toolTip")
        textField.text = settings.patterns.joinToString(" ")
    }

    private val patternsList: List<String>
        get() = patterns.text.trim().split(' ').filter(String::isNotEmpty)


    override fun createComponent(): JComponent? {
        builder.addComponent(
            TitledSeparator(FoldableProjectViewBundle.message("foldableProjectView.name")),
            0
        )

        // TODO: [X] isFoldingEnabled

        builder.addLabeledComponent(
            FoldableProjectViewBundle.message("foldableProjectView.pattern"),
            patterns,
        )
        builder.addLabeledComponent(
            null,
            JBLabel(FoldableProjectViewBundle.message("foldableProjectView.pattern.toolTip")).apply {
                componentStyle = UIUtil.ComponentStyle.SMALL
                fontColor = UIUtil.FontColor.BRIGHTER
            },
        )

        // TODO: [X] Filter files only
        // TODO: [X] Hide group if no items were folded
        // TODO: [X] Fold submodules

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

                    val isModule = modules.any { it == file }
                    val isMatched = patternsList.any {
                        patternCache.createPattern(it, Syntax.GLOB)?.matcher(file.name)?.matches() ?: false
                    }
                    append(file.name, when {
                        isModule -> SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
                        isMatched -> SimpleTextAttributes.GRAY_ATTRIBUTES
                        else -> SimpleTextAttributes.REGULAR_ATTRIBUTES
                    })

                    icon = when {
                        !file.isDirectory -> IconUtil.getIcon(file, 0, null)
                        fileIndex.isExcluded(file) -> AllIcons.Modules.ExcludeRoot
                        else -> PlatformIcons.FOLDER_ICON
                    }
                }
            }

            expandRow(0)
        }

        patterns.editorField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                tree.component.repaint()
            }
        })

        builder.addLabeledComponent(
            FoldableProjectViewBundle.message("foldableProjectView.preview"),
            ScrollPaneFactory.createScrollPane(tree),
        )

        return builder.panel
    }

    override fun isModified() = !settings.patterns.containsAll(patternsList) || !patternsList.containsAll(settings.patterns)

    override fun apply() {
        settings.patterns = patternsList
    }

    override fun getDisplayName() = FoldableProjectViewBundle.message("foldableProjectView.name")

    override fun getId() = ID
}
