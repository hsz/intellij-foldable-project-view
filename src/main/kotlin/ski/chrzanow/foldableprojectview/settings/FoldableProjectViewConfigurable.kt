package ski.chrzanow.foldableprojectview.settings

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.impl.AbstractProjectTreeStructure
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.util.transform
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.*
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.JBViewport
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.not
import com.intellij.ui.tree.TreeVisitor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.tree.TreeUtil
import ski.chrzanow.foldableprojectview.FoldableProjectViewBundle.message
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.HELP_TOPIC
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.SPONSOR_GITHUB_URL
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.SPONSOR_KOFI_URL
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.SPONSOR_PAYPAL_URL
import ski.chrzanow.foldableprojectview.bindSelected
import ski.chrzanow.foldableprojectview.createPredicate
import ski.chrzanow.foldableprojectview.projectView.FoldableProjectViewNode
import ski.chrzanow.foldableprojectview.projectView.FoldableTreeStructureProvider
import ski.chrzanow.foldableprojectview.rules.FoldableRulesResolver
import ski.chrzanow.foldableprojectview.rules.ResolvedState
import ski.chrzanow.foldableprojectview.rules.RuleSetOptions
import ski.chrzanow.foldableprojectview.rules.RuleSetRepository
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.*
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
import javax.swing.tree.DefaultTreeSelectionModel

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

        /**
         * Whether the preview is shown, remembered across the projects and the IDE restarts.
         */
        private const val PREVIEW_VISIBLE_KEY = "FoldableProjectView.settings.preview.visible"

        /**
         * Scrolling step of the settings column, in pixels. Without it the column would scroll by a single pixel per step.
         */
        private const val SCROLL_UNIT = 16

        /**
         * Name of the label listing the rule set files in effect, see the tests.
         */
        const val RULE_SETS_LABEL_NAME = "FoldableProjectView.ruleSets"
    }

    private val defaults = service<FoldableProjectDefaults>()
    private val appSettings = service<FoldableProjectViewAppSettings>()
    private val repository = service<RuleSetRepository>()
    private val resolver = project.service<FoldableRulesResolver>()

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

    /**
     * The edited settings merged with the rule set files, as the Project View sees them, see [FoldableRulesResolver].
     * Feeds the preview, the rule editor, and the information about the files in effect.
     */
    private val resolvedProperty = settingsProperty.transform { resolver.resolve(it) }

    private val foldingEnabledPredicate = resolvedProperty.createPredicate { it.foldingEnabled }
    private val differsFromDefaultsPredicate = settingsProperty.createPredicate { it != defaults.createSettings() }

    /**
     * The directory of the user-level rule sets, an application-level setting edited along with the project settings.
     */
    private val userDirectoryProperty = propertyGraph.property(appSettings.userDirectory)

    /**
     * Whether the option is free to edit, i.e., not set by one of the rule set files, see [ResolvedState.fileOptions].
     */
    private fun unlockedPredicate(option: (RuleSetOptions) -> Boolean?) = resolvedProperty.createPredicate { option(it.fileOptions) == null }

    /**
     * Whether the edited settings are to be stored as the defaults for new projects when they are applied.
     * Storing is deferred to [apply], so canceling the Settings dialog discards the request like any other change on the page.
     */
    private val storeAsDefaultsProperty = propertyGraph.property(false).apply {
        differsFromDefaultsPredicate.addListener { differs ->
            // Nothing to store once the edited settings equal the defaults.
            if (!differs) {
                set(false)
            }
        }
    }

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

    private val rulesTable = FoldableRulesTable(settingsProperty, project).apply {
        tableView.visibleRowCount = VISIBLE_RULES
        ClientProperty.put(tableView, JBViewport.FORCE_VISIBLE_ROW_COUNT_KEY, true)
    }

    /**
     * Whether the preview is shown next to the settings. It is a UI preference, not a setting: it does not make the page
     * modified, and is stored as soon as it is toggled, so the page reopens as the user left it.
     * The settings column follows the width of the page while the preview is hidden, and scrolls horizontally otherwise.
     */
    private val previewVisibleProperty = propertyGraph.property(PropertiesComponent.getInstance().getBoolean(PREVIEW_VISIBLE_KEY, true)).apply {
        afterChange { visible ->
            PropertiesComponent.getInstance().setValue(PREVIEW_VISIBLE_KEY, visible, true)
            previewPanel?.isVisible = visible
            settingsScrollPane?.apply {
                horizontalScrollBarPolicy = horizontalScrollBarPolicy(visible)
                parent?.apply {
                    revalidate()
                    repaint()
                }
            }
        }
    }

    private var previewPanel: JComponent? = null
    private var settingsScrollPane: JScrollPane? = null

    /**
     * The settings column scrolls horizontally only when the preview takes a part of the page width.
     */
    private fun horizontalScrollBarPolicy(previewVisible: Boolean) = when {
        previewVisible && !project.isDefault -> HORIZONTAL_SCROLLBAR_AS_NEEDED
        else -> HORIZONTAL_SCROLLBAR_NEVER
    }

    /**
     * The preview pane and its tree structure provider; `null` for the default project, which has nothing to preview.
     */
    private var preview: ProjectViewPane? = null
    private var previewProvider: FoldableTreeStructureProvider? = null

    private fun createProjectViewPane() = object : ProjectViewPane(project) {

        private val treeStructureProvider = FoldableTreeStructureProvider(project)
            .withPreview(this, resolvedProperty) { selectInPreview(ruleProperty.get()) }
            .also { previewProvider = it }

        override fun enableDnD() = Unit

        override fun createStructure() = object : AbstractProjectTreeStructure(project) {

            override fun getProviders() = listOf(treeStructureProvider)
        }
    }

    /**
     * The options, the folding rules, and the defaults, as a single scrollable column.
     */
    @Suppress("DialogTitleCapitalization")
    private fun createSettingsPanel() = panel {
        if (project.isDefault) {
            row {
                comment(message("foldableProjectView.settings.defaults.description"), MAX_LINE_LENGTH_WORD_WRAP)
            }
        }
        // The sponsorship links go first, where they are seen while the plugin is actually being configured: the text on its
        // own line and the links below it, so they are fully visible next to the preview, with GitHub Sponsors as the
        // primary channel, instead of a group buried at the bottom of the page.
        row {
            label(message("foldableProjectView.settings.support"))
        }
        row {
            browserLink(message("foldableProjectView.settings.support.github"), SPONSOR_GITHUB_URL)
            browserLink(message("foldableProjectView.settings.support.kofi"), SPONSOR_KOFI_URL)
            browserLink(message("foldableProjectView.settings.support.paypal"), SPONSOR_PAYPAL_URL)
        }
        separator()
            .topGap(TopGap.SMALL)
            .bottomGap(BottomGap.SMALL)
        if (!project.isDefault) {
            // A UI preference rather than a setting, see [previewVisibleProperty].
            row {
                checkBox(message("foldableProjectView.settings.preview.show"))
                    .bindSelected(previewVisibleProperty)
                    .applyToComponent { setMnemonic('p') }
            }
        }
        row {
            checkBox(message("foldableProjectView.settings.foldingEnabled"))
                .bindSelected(settingsProperty, FoldableProjectSettings::foldingEnabled)
                .comment(message("foldableProjectView.settings.foldingEnabled.comment"), MAX_LINE_LENGTH_WORD_WRAP)
                .applyToComponent { setMnemonic('e') }
                .enabledIf(unlockedPredicate(RuleSetOptions::foldingEnabled))
        }

        // Indented to show that the options apply only when folding is enabled.
        indent {
            row {
                checkBox(message("foldableProjectView.settings.caseSensitive"))
                    .bindSelected(settingsProperty, FoldableProjectSettings::caseSensitive)
                    .comment(message("foldableProjectView.settings.caseSensitive.comment"), MAX_LINE_LENGTH_WORD_WRAP)
                    .applyToComponent { setMnemonic('c') }
                    .enabledIf(unlockedPredicate(RuleSetOptions::caseSensitive))
            }

            row {
                checkBox(message("foldableProjectView.settings.matchDirectories"))
                    .bindSelected(settingsProperty, FoldableProjectSettings::matchDirectories)
                    .comment(message("foldableProjectView.settings.matchDirectories.comment"), MAX_LINE_LENGTH_WORD_WRAP)
                    .applyToComponent { setMnemonic('d') }
                    .enabledIf(unlockedPredicate(RuleSetOptions::matchDirectories))
            }

            row {
                checkBox(message("foldableProjectView.settings.foldNestedDirectories"))
                    .bindSelected(settingsProperty, FoldableProjectSettings::foldNestedDirectories)
                    .comment(message("foldableProjectView.settings.foldNestedDirectories.comment"), MAX_LINE_LENGTH_WORD_WRAP)
                    .applyToComponent { setMnemonic('n') }
                    .enabledIf(unlockedPredicate(RuleSetOptions::foldNestedDirectories))
            }

            row {
                checkBox(message("foldableProjectView.settings.hideEmptyGroups"))
                    .bindSelected(settingsProperty, FoldableProjectSettings::hideEmptyGroups)
                    .comment(message("foldableProjectView.settings.hideEmptyGroups.comment"), MAX_LINE_LENGTH_WORD_WRAP)
                    .applyToComponent { setMnemonic('h') }
                    .enabledIf(unlockedPredicate(RuleSetOptions::hideEmptyGroups))
            }
        }.enabledIf(foldingEnabledPredicate)
        row {
            cell(lockedOptionsLabel)
        }.visibleIf(resolvedProperty.createPredicate { !it.fileOptions.isEmpty })

        group(message("foldableProjectView.settings.foldingRules")) {
            row {
                cell(rulesTable.component)
                    .align(AlignX.FILL)
            }
            row {
                val rulesEditor = FoldableRulesEditor(ruleProperty, resolvedProperty, project.takeUnless { it.isDefault }, disposable!!)
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
                checkBox(message("foldableProjectView.settings.defaults.store"))
                    .bindSelected(storeAsDefaultsProperty)
                    .enabledIf(differsFromDefaultsPredicate)
                    .applyToComponent { setMnemonic('u') }
                button(message("foldableProjectView.settings.defaults.load")) { copyFromDefaults() }
                    .enabledIf(differsFromDefaultsPredicate)
            }
            row {
                comment(message("foldableProjectView.settings.defaults.comment"), MAX_LINE_LENGTH_WORD_WRAP)
            }.visibleIf(differsFromDefaultsPredicate)
            row {
                comment(message("foldableProjectView.settings.defaults.same"), MAX_LINE_LENGTH_WORD_WRAP)
            }.visibleIf(differsFromDefaultsPredicate.not())
        }
        group(message("foldableProjectView.settings.ruleSets")) {
            row {
                comment(message("foldableProjectView.settings.ruleSets.description"), MAX_LINE_LENGTH_WORD_WRAP)
            }
            if (!project.isDefault) {
                row {
                    cell(ruleSetsLabel)
                        .align(AlignX.FILL)
                }
                row {
                    cell(problemsLabel)
                        .align(AlignX.FILL)
                }.visibleIf(resolvedProperty.createPredicate { it.problems.isNotEmpty() })
            }
            row(message("foldableProjectView.settings.ruleSets.userDirectory")) {
                textFieldWithBrowseButton(
                    FileChooserDescriptorFactory.createSingleFolderDescriptor()
                        .withTitle(message("foldableProjectView.settings.ruleSets.userDirectory.title")),
                    project.takeUnless { it.isDefault },
                )
                    .align(AlignX.FILL)
                    .bindText(userDirectoryProperty)
                    .applyToComponent { (textField as? JBTextField)?.emptyText?.text = FoldableProjectViewAppSettings.defaultUserDirectoryPath.toPresentableUserPath() }
                    .comment(message("foldableProjectView.settings.ruleSets.userDirectory.comment"), MAX_LINE_LENGTH_WORD_WRAP)
            }
        }
    }

    /**
     * Lists the rule set files in effect for the project, see [ResolvedState.ruleSets]; refreshed with the settings.
     */
    private val ruleSetsLabel = JBLabel().apply {
        name = RULE_SETS_LABEL_NAME
        foreground = UIUtil.getContextHelpForeground()
        resolvedProperty.afterChange { text = ruleSetsText(it) }
        text = ruleSetsText(resolvedProperty.get())
    }

    private fun ruleSetsText(state: ResolvedState) = when {
        state.ruleSets.isEmpty() -> message("foldableProjectView.settings.ruleSets.none")
        else -> message("foldableProjectView.settings.ruleSets.inEffect", state.ruleSets.joinToString { it.path?.toPresentableUserPath() ?: it.name })
    }

    /**
     * Lists the rule set files that could not be read, see [ResolvedState.problems].
     */
    private val problemsLabel = JBLabel().apply {
        icon = AllIcons.General.Warning
        resolvedProperty.afterChange { text = problemsText(it) }
        text = problemsText(resolvedProperty.get())
    }

    private fun problemsText(state: ResolvedState) = state.problems
        .takeIf { it.isNotEmpty() }
        ?.let { problems -> message("foldableProjectView.settings.ruleSets.problems", problems.joinToString("; ") { it.presentableMessage }) }
        .orEmpty()

    /**
     * Lists the options set by the rule set files, which the checkboxes above cannot change, see [ResolvedState.fileOptions].
     */
    private val lockedOptionsLabel = JBLabel().apply {
        foreground = UIUtil.getContextHelpForeground()
        resolvedProperty.afterChange { text = lockedOptionsText(it) }
        text = lockedOptionsText(resolvedProperty.get())
    }

    private fun lockedOptionsText(state: ResolvedState) = with(state.fileOptions) {
        listOfNotNull(
            foldingEnabled?.let { message("foldableProjectView.settings.foldingEnabled") to it },
            caseSensitive?.let { message("foldableProjectView.settings.caseSensitive") to it },
            matchDirectories?.let { message("foldableProjectView.settings.matchDirectories") to it },
            foldNestedDirectories?.let { message("foldableProjectView.settings.foldNestedDirectories") to it },
            hideEmptyGroups?.let { message("foldableProjectView.settings.hideEmptyGroups") to it },
        )
    }
        .takeIf { it.isNotEmpty() }
        ?.let { options -> message("foldableProjectView.settings.ruleSets.lockedOptions", options.joinToString { (name, value) -> "$name = ${if (value) "on" else "off"}" }) }
        .orEmpty()

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
            treeSelectionListeners
                .filter { it.javaClass.name.contains("RestoreSelectionListener") }
                .forEach(::removeTreeSelectionListener)
            (selectionModel as? DefaultTreeSelectionModel)?.treeSelectionListeners
                ?.filter { it.javaClass.name.contains("RestoreSelectionListener") }
                ?.forEach(selectionModel::removeTreeSelectionListener)
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
            isVisible = previewVisibleProperty.get()
            previewPanel = this
        }
    }

    /**
     * Wraps the settings column so it is scrolled by a sensible step and, while the preview is hidden, follows the width of
     * its scroll pane: the comments wrap and the rules table shrinks instead of being cut off on the right. While the preview
     * is shown, the column keeps its preferred width and is scrolled horizontally instead.
     */
    private fun createScrollableSettingsPanel() = object : BorderLayoutPanel(), Scrollable {

        init {
            addToCenter(createSettingsPanel())
        }

        override fun getPreferredScrollableViewportSize(): Dimension = preferredSize

        override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) = JBUI.scale(SCROLL_UNIT)

        override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) = when (orientation) {
            SwingConstants.VERTICAL -> visibleRect.height
            else -> visibleRect.width
        }

        override fun getScrollableTracksViewportWidth() = project.isDefault || !previewVisibleProperty.get()

        override fun getScrollableTracksViewportHeight() = false
    }

    override fun createPanel() = panel {
        val settingsPanel = ScrollPaneFactory.createScrollPane(createScrollableSettingsPanel(), true).apply {
            horizontalScrollBarPolicy = horizontalScrollBarPolicy(previewVisibleProperty.get())
            settingsScrollPane = this
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
        if (rule.isSameAs(tree.selectedGroupRule())) {
            return
        }
        tree.clearSelection()
        TreeUtil.promiseSelect(tree) { path ->
            val node = TreeUtil.getLastUserObject(path)
            when {
                node is FoldableProjectViewNode -> when {
                    rule.isSameAs(node.value) -> TreeVisitor.Action.INTERRUPT
                    else -> TreeVisitor.Action.SKIP_CHILDREN
                }

                path.pathCount == 1 -> TreeVisitor.Action.CONTINUE
                node is ProjectViewNode<*> && with(provider) { node.isModuleRoot() } -> TreeVisitor.Action.CONTINUE
                else -> TreeVisitor.Action.SKIP_CHILDREN
            }
        }.onError {
            tree.clearSelection()
        }
    }

    /**
     * Selects the rule of the folding group selected in the preview, if any.
     */
    private fun selectInTable() {
        val rule = preview?.tree?.selectedGroupRule() ?: return
        if (!rule.isSameAs(rulesTable.tableView.selectedObject)) {
            rulesTable.tableView.items.firstOrNull { rule.isSameAs(it) }?.let { rulesTable.tableView.selection = listOf(it) }
        }
    }

    private fun JTree.selectedGroupRule() = (TreeUtil.getLastUserObject(selectionPath) as? FoldableProjectViewNode)?.value

    override fun isModified() = settingsProperty.get() != settings || storeAsDefaultsProperty.get() || userDirectoryProperty.get().trim() != appSettings.userDirectory

    override fun apply() {
        if (userDirectoryProperty.get().trim() != appSettings.userDirectory) {
            appSettings.userDirectory = userDirectoryProperty.get()
            userDirectoryProperty.set(appSettings.userDirectory)
            repository.watchUserDirectory()
        }
        when {
            project.isDefault -> {
                settings.copyFrom(settingsProperty.get())
                defaults.store(settings)
            }

            else -> {
                settings.update(project) {
                    copyFrom(settingsProperty.get())
                }
                if (storeAsDefaultsProperty.get()) {
                    storeAsDefaults()
                }
            }
        }
        invalidateTable()
    }

    /**
     * Stores the applied settings as the defaults for new projects, see [storeAsDefaultsProperty].
     */
    private fun storeAsDefaults() {
        defaults.store(settings)
        storeAsDefaultsProperty.set(false)

        // Re-emit the settings so the defaults controls pick up the new defaults.
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
        storeAsDefaultsProperty.set(false)
        userDirectoryProperty.set(appSettings.userDirectory)
        settingsProperty.set(settingsProperty.get().apply {
            copyFrom(settings)
        })
        invalidateTable()
    }

    override fun disposeUIResources() {
        preview = null
        previewProvider = null
        previewPanel = null
        settingsScrollPane = null
        super.disposeUIResources()
    }

    /**
     * Reloads the table from the edited settings merged with the rule set files, keeping the selected row (or selecting the
     * first one), so the rule editor does not go blank after Apply, Reset, or Copy from Defaults, and shows the first rule
     * as soon as the page opens.
     */
    private fun invalidateTable() = rulesTable.apply {
        val selectedRow = tableView.selectedRow
        reload()
        if (tableView.rowCount > 0) {
            val row = selectedRow.coerceIn(0, tableView.rowCount - 1)
            tableView.setRowSelectionInterval(row, row)
            TableUtil.scrollSelectionToVisible(tableView)
        }
    }
}
