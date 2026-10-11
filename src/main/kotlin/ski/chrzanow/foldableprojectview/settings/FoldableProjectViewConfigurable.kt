package ski.chrzanow.foldableprojectview.settings

import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.impl.AbstractProjectTreeStructure
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.components.service
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ClientProperty
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.TableUtil
import com.intellij.ui.components.JBViewport
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.tree.TreeVisitor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import ski.chrzanow.foldableprojectview.FoldableProjectViewBundle.message
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.HELP_TOPIC
import ski.chrzanow.foldableprojectview.bindSelected
import ski.chrzanow.foldableprojectview.createPredicate
import ski.chrzanow.foldableprojectview.projectView.FoldableProjectViewNode
import ski.chrzanow.foldableprojectview.projectView.FoldableTreeStructureProvider
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER

/**
 * Settings page: the options and the folding rules on the left, the live Project View preview filling the right side.
 * The page does its own scrolling (see [Configurable.NoScroll]), so the preview can follow the height of the Settings dialog
 * and stay visible while the rules are edited.
 */
class FoldableProjectViewConfigurable(private val project: Project) : BoundSearchableConfigurable(
    displayName = message("foldableProjectView.name"),
    helpTopic = HELP_TOPIC,
    _id = ID,
), Configurable.NoScroll {

    companion object {
        const val ID = "FoldableProjectViewConfigurable"

        /**
         * Name of the preview tree component, see the tests.
         */
        const val PREVIEW_TREE_NAME = "FoldableProjectView.preview"

        /**
         * Share of the page width taken by the settings; the rest is the preview.
         */
        private const val SETTINGS_PROPORTION = .6f

        /**
         * Number of the rules table rows shown without scrolling.
         */
        private const val VISIBLE_RULES = 6
    }

    private val defaults = service<FoldableProjectDefaults>()

    /**
     * Settings edited by this page: the settings of the [project], or the defaults for new projects
     * when the page is opened for the default project (e.g., in the "New Projects Setup" settings).
     */
    private val settings = when {
        project.isDefault -> defaults.createSettings()
        else -> project.service<FoldableProjectSettings>()
    }

    private val propertyGraph = PropertyGraph()
    private val settingsProperty = propertyGraph.lazyProperty { FoldableProjectSettings().apply { copyFrom(settings) } }
    private val foldingEnabledPredicate = settingsProperty.createPredicate(FoldableProjectSettings::foldingEnabled)
    private val differsFromDefaultsPredicate = settingsProperty.createPredicate { it != defaults.createSettings() }

    private val ruleProperty = propertyGraph
        .lazyProperty<Rule?> { null }
        .apply {
            afterChange { rule ->
                rulesTable.tableView.repaint()

                // Re-emit the settings so the preview and the modification state pick up the rule change.
                settingsProperty.set(settingsProperty.get())
                selectInPreview(rule)
            }
        }

    private val rulesTable = FoldableRulesTable(settingsProperty).apply {
        tableView.visibleRowCount = VISIBLE_RULES
        ClientProperty.put(tableView, JBViewport.FORCE_VISIBLE_ROW_COUNT_KEY, true)
    }

    /**
     * The preview pane and its tree structure provider; `null` for the default project, which has nothing to preview.
     */
    private var preview: ProjectViewPane? = null
    private var previewProvider: FoldableTreeStructureProvider? = null

    private fun createProjectViewPane() = object : ProjectViewPane(project) {

        private val treeStructureProvider = FoldableTreeStructureProvider(project)
            .withPreview(this, settingsProperty) { selectInPreview(ruleProperty.get()) }
            .also { previewProvider = it }

        override fun enableDnD() = Unit

        override fun createStructure() = object : AbstractProjectTreeStructure(project) {

            override fun getProviders() = listOf(treeStructureProvider)
        }
    }

    /**
     * The options, the folding rules, and the defaults, as a single scrollable column.
     */
    private fun createSettingsPanel() = panel {
        if (project.isDefault) {
            row {
                comment(message("foldableProjectView.settings.defaults.description"), MAX_LINE_LENGTH_WORD_WRAP)
            }
        }
        row {
            checkBox(message("foldableProjectView.settings.foldingEnabled"))
                .bindSelected(settingsProperty, FoldableProjectSettings::foldingEnabled)
                .comment(message("foldableProjectView.settings.foldingEnabled.comment"), MAX_LINE_LENGTH_WORD_WRAP)
                .applyToComponent { setMnemonic('e') }
        }

        // Indented to show that the options apply only when folding is enabled.
        indent {
            row {
                checkBox(message("foldableProjectView.settings.caseSensitive"))
                    .bindSelected(settingsProperty, FoldableProjectSettings::caseSensitive)
                    .comment(message("foldableProjectView.settings.caseSensitive.comment"), MAX_LINE_LENGTH_WORD_WRAP)
                    .applyToComponent { setMnemonic('c') }
            }

            row {
                checkBox(message("foldableProjectView.settings.matchDirectories"))
                    .bindSelected(settingsProperty, FoldableProjectSettings::matchDirectories)
                    .comment(message("foldableProjectView.settings.matchDirectories.comment"), MAX_LINE_LENGTH_WORD_WRAP)
                    .applyToComponent { setMnemonic('d') }
            }

            row {
                checkBox(message("foldableProjectView.settings.foldNestedDirectories"))
                    .bindSelected(settingsProperty, FoldableProjectSettings::foldNestedDirectories)
                    .comment(message("foldableProjectView.settings.foldNestedDirectories.comment"), MAX_LINE_LENGTH_WORD_WRAP)
                    .applyToComponent { setMnemonic('n') }
            }

            row {
                checkBox(message("foldableProjectView.settings.hideEmptyGroups"))
                    .bindSelected(settingsProperty, FoldableProjectSettings::hideEmptyGroups)
                    .comment(message("foldableProjectView.settings.hideEmptyGroups.comment"), MAX_LINE_LENGTH_WORD_WRAP)
                    .applyToComponent { setMnemonic('h') }
            }
        }.enabledIf(foldingEnabledPredicate)

        group(message("foldableProjectView.settings.foldingRules")) {
            row {
                cell(rulesTable.component)
                    .align(AlignX.FILL)
            }
            row {
                val rulesEditor = FoldableRulesEditor(ruleProperty, settingsProperty, project.takeUnless { it.isDefault }, disposable!!)
                cell(rulesEditor.createPanel().apply { registerValidators(disposable!!) })
                    .align(AlignX.FILL)
                    .applyIfEnabled()

                with(rulesTable.tableView) {
                    selectionModel.addListSelectionListener {
                        ruleProperty.set(selectedObject)
                    }
                }
            }
        }
        if (!project.isDefault) {
            row {
                button(message("foldableProjectView.settings.defaults.store")) { storeAsDefaults() }
                    .enabledIf(differsFromDefaultsPredicate)
                button(message("foldableProjectView.settings.defaults.load")) { copyFromDefaults() }
                    .enabledIf(differsFromDefaultsPredicate)
            }.rowComment(message("foldableProjectView.settings.defaults.comment"), MAX_LINE_LENGTH_WORD_WRAP)
        }
        group(message("foldableProjectView.settings.support")) {
            row {
                comment(message("foldableProjectView.settings.support.comment"), MAX_LINE_LENGTH_WORD_WRAP)
            }
        }
    }

    /**
     * The live Project View preview. It is read-only: the Project View context menu is not available, and double-clicking
     * or pressing Enter on a file does not open it behind the Settings dialog.
     */
    private fun createPreviewPanel(): JComponent {
        val pane = createProjectViewPane().also {
            Disposer.register(disposable!!, it)
            preview = it
        }
        val component = pane.createComponent().apply {
            border = JBUI.Borders.empty()
            preferredSize = Dimension()
        }
        pane.tree.apply {
            name = PREVIEW_TREE_NAME
            mouseListeners.filterIsInstance<PopupHandler>().forEach(::removeMouseListener)
            addTreeSelectionListener { selectInTable() }
        }
        TreeUtil.promiseExpand(pane.tree, 2)

        val readOnlyComponent = UiDataProvider.wrapComponent(component) { sink ->
            sink.setNull(CommonDataKeys.NAVIGATABLE)
            sink.setNull(CommonDataKeys.NAVIGATABLE_ARRAY)
            sink.setNull(CommonDataKeys.PSI_ELEMENT)
            sink.setNull(CommonDataKeys.VIRTUAL_FILE_ARRAY)
            sink.setNull(PlatformCoreDataKeys.SELECTED_ITEMS)
        }
        return panel {
            group(message("foldableProjectView.settings.preview"), indent = false) {
                row {
                    cell(readOnlyComponent)
                        .align(Align.FILL)
                }.resizableRow()
            }.resizableRow()
        }.apply {
            border = JBUI.Borders.emptyLeft(10)
        }
    }

    override fun createPanel() = panel {
        val settingsPanel = ScrollPaneFactory.createScrollPane(createSettingsPanel(), true).apply {
            horizontalScrollBarPolicy = HORIZONTAL_SCROLLBAR_NEVER
        }
        // The preview is not available for the default project (e.g., in the "New Projects Setup" settings).
        val content = when {
            project.isDefault -> settingsPanel
            else -> OnePixelSplitter(false, SETTINGS_PROPORTION).apply {
                firstComponent = settingsPanel.apply {
                    border = JBUI.Borders.emptyRight(10)
                }
                secondComponent = createPreviewPanel()
                setHonorComponentsMinimumSize(false)
            }
        }
        row {
            cell(content)
                .align(Align.FILL)
        }.resizableRow()
    }

    /**
     * Selects and shows the folding group of the [rule] in the preview, so the edited rule and its effect are visibly connected.
     * Only the module roots are searched, as the groups in nested directories would require loading the whole project tree.
     */
    private fun selectInPreview(rule: Rule?) {
        val tree = preview?.tree ?: return
        val provider = previewProvider ?: return
        if (rule == null) {
            tree.clearSelection()
            return
        }
        if (tree.selectedGroupRule() === rule) {
            return
        }
        TreeUtil.promiseSelect(tree, TreeVisitor { path ->
            val node = TreeUtil.getLastUserObject(path)
            when {
                node is FoldableProjectViewNode -> when {
                    node.value === rule -> TreeVisitor.Action.INTERRUPT
                    else -> TreeVisitor.Action.SKIP_CHILDREN
                }

                path.pathCount == 1 -> TreeVisitor.Action.CONTINUE
                node is ProjectViewNode<*> && with(provider) { node.isModuleRoot() } -> TreeVisitor.Action.CONTINUE
                else -> TreeVisitor.Action.SKIP_CHILDREN
            }
        }).onError {
            tree.clearSelection()
        }
    }

    /**
     * Selects the rule of the folding group selected in the preview, if any.
     */
    private fun selectInTable() {
        val rule = preview?.tree?.selectedGroupRule() ?: return
        if (rulesTable.tableView.selectedObject !== rule) {
            rulesTable.tableView.selection = listOf(rule)
        }
    }

    private fun JTree.selectedGroupRule() = (TreeUtil.getLastUserObject(selectionPath) as? FoldableProjectViewNode)?.value

    override fun isModified() = settingsProperty.get() != settings

    override fun apply() {
        when {
            project.isDefault -> {
                settings.copyFrom(settingsProperty.get())
                defaults.store(settings)
            }

            else -> settings.update(project) {
                copyFrom(settingsProperty.get())
            }
        }
        invalidateTable()
    }

    /**
     * Stores the currently edited settings as the defaults for new projects.
     */
    private fun storeAsDefaults() {
        defaults.store(settingsProperty.get())

        // Re-emit the settings so the defaults buttons pick up the new defaults.
        settingsProperty.set(settingsProperty.get())
    }

    /**
     * Replaces the currently edited settings with the defaults for new projects.
     */
    private fun copyFromDefaults() {
        settingsProperty.set(settingsProperty.get().apply {
            loadDefaults()
        })
        invalidateTable()
    }

    override fun reset() {
        settingsProperty.set(settingsProperty.get().apply {
            copyFrom(settings)
        })
        invalidateTable()
    }

    override fun disposeUIResources() {
        preview = null
        previewProvider = null
        super.disposeUIResources()
    }

    /**
     * Reloads the table from the edited settings, keeping the selected row (or selecting the first one), so the rule editor
     * does not go blank after Apply, Reset, or Copy from Defaults, and shows the first rule as soon as the page opens.
     */
    private fun invalidateTable() = rulesTable.apply {
        val selectedRow = tableView.selectedRow
        setValues(settingsProperty.get().rules)
        if (tableView.rowCount > 0) {
            val row = selectedRow.coerceIn(0, tableView.rowCount - 1)
            tableView.setRowSelectionInterval(row, row)
            TableUtil.scrollSelectionToVisible(tableView)
        }
    }
}
