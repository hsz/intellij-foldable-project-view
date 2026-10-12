package ski.chrzanow.foldableprojectview.settings

import com.intellij.execution.util.ListTableWithButtons
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeCoreBundle
import com.intellij.openapi.actionSystem.ActionToolbarPosition
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.util.transform
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.*
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import ski.chrzanow.foldableprojectview.FoldableProjectViewBundle.message
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.COLOR_COLUMN_TEXT
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.RULE_SET_FILE
import ski.chrzanow.foldableprojectview.psi.search.GlobPatterns
import ski.chrzanow.foldableprojectview.rules.FoldableRulesResolver
import ski.chrzanow.foldableprojectview.rules.RuleSet
import ski.chrzanow.foldableprojectview.rules.RuleSetRepository
import ski.chrzanow.foldableprojectview.rules.RuleSetYaml
import java.awt.Component
import java.awt.Cursor
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Path
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer
import kotlin.io.path.exists

/**
 * Table of the folding rules in effect for the [project]: the rules of the rule set files followed by the rules of the
 * edited settings, see [FoldableRulesResolver]. The rules of the files are read-only, except for the `Enabled` checkbox,
 * which is stored per project as an override; the rest is written back to the edited settings.
 */
class FoldableRulesTable(
    private val settingsProperty: ObservableMutableProperty<FoldableProjectSettings>,
    private val project: Project,
) : ListTableWithButtons<Rule>() {

    private val resolver
        get() = project.service<FoldableRulesResolver>()

    private val repository
        get() = service<RuleSetRepository>()

    private val rulesProperty = settingsProperty.transform(
        { resolver.resolve(it).rules },
        { elements ->
            settingsProperty.get().apply {
                val defaults = resolver.resolve(this).fileRuleDefaults
                with(rules) {
                    clear()
                    addAll(elements.filterNot(Rule::isFromFile))
                }
                with(ruleOverrides) {
                    clear()
                    elements
                        .filter(Rule::isFromFile)
                        .filter { defaults[it.effectiveId] != it.enabled }
                        .forEach { add(RuleOverride(it.effectiveId, it.enabled)) }
                }
            }
        },
    )

    init {
        tableView.apply {
            columnSelectionAllowed = false
            tableHeader.reorderingAllowed = false
            val headerIcons = listOf(
                AllIcons.General.GreenCheckmark,
                AllIcons.Actions.Colors,
                AllIcons.Actions.ToggleVisibility,
                AllIcons.FileTypes.Image,
            )
            headerIcons.forEachIndexed { index, headerIcon ->
                columnModel.getColumn(index).apply {
                    maxWidth = JBUI.scale(24)
                    minWidth = JBUI.scale(24)
                    headerRenderer = TableCellRenderer { table, value, isSelected, hasFocus, row, column ->
                        tableHeader.defaultRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                            .apply {
                                if (this is JLabel) {
                                    text = ""
                                    icon = headerIcon
                                    horizontalAlignment = JLabel.CENTER
                                    val insets = insets
                                    border = BorderFactory.createEmptyBorder(insets.top, 0, insets.bottom, 0)
                                }
                            }
                    }
                }
            }
            tableHeader.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (!SwingUtilities.isLeftMouseButton(e) || e.clickCount != 1) {
                        return
                    }
                    if (tableHeader.cursor.type == Cursor.E_RESIZE_CURSOR || tableHeader.cursor.type == Cursor.W_RESIZE_CURSOR) {
                        return
                    }
                    val column = tableHeader.columnAtPoint(e.point)
                    if (column !in headerIcons.indices) {
                        return
                    }
                    val explanation = tableHeader.getToolTipText(e) ?: return
                    val bounds = tableHeader.getHeaderRect(column)
                    JBPopupFactory.getInstance()
                        .createHtmlTextBalloonBuilder(explanation, MessageType.INFO, null)
                        .setHideOnClickOutside(true)
                        .setHideOnKeyOutside(true)
                        .createBalloon()
                        .show(RelativePoint(tableHeader, Point(bounds.x + bounds.width / 2, bounds.y + bounds.height)), Balloon.Position.below)
                }
            })
            model.addTableModelListener {
                rulesProperty.set(elements)
            }
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            setShowGrid(false)
            setValues(rulesProperty.get())
        }
        rulesProperty.afterChange {
            refreshValues()
        }
    }

    override fun createToolbarDecorator() = ToolbarDecorator
        .createDecorator(tableView, null)
        .setToolbarPosition(ActionToolbarPosition.RIGHT)
        .setPanelBorder(JBUI.Borders.empty())
        .addExtraAction(exportAction())
        .addExtraAction(saveUserDefaultsAction())

    override fun isUpDownSupported() = true

    override fun shouldEditRowOnCreation() = false

    override fun createListModel() = ListTableModel<Rule>(EnabledColumn(), ColorsColumn(), ActionColumn(), IconColumn(), NameColumn(), PatternsColumn(), OriginColumn())

    override fun createElement() = Rule()

    /**
     * The `+` button offers an empty rule and the presets – built-in and from the user directory, see [RuleSetRepository] –
     * instead of adding a rule right away. A preset is copied into the settings, so it can be adjusted afterward.
     */
    override fun createAddAction() = AnActionButtonRunnable { button ->
        val group = DefaultActionGroup().apply {
            add(addRuleAction(message("foldableProjectView.settings.presets.empty")) { listOf(createElement()) })
            add(Separator.create(message("foldableProjectView.settings.presets.builtin")))
            repository.builtinPresets.forEach { add(presetAction(it)) }
            add(Separator.create(message("foldableProjectView.settings.presets.user")))
            val userPresets = repository.userPresets().ruleSets
            if (userPresets.isEmpty()) {
                add(DumbAwareAction.create(message("foldableProjectView.settings.presets.user.empty", repository.userPresetsDirectory.toPresentableUserPath())) {}
                    .apply { templatePresentation.isEnabled = false })
            }
            userPresets.forEach { add(presetAction(it)) }
        }
        JBPopupFactory.getInstance()
            .createActionGroupPopup(null, group, button.dataContext, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true)
            .show(button.preferredPopupPoint)
    }

    private fun presetAction(preset: RuleSet) = addRuleAction(preset.name, preset.description) { repository.copyRules(preset) }

    private fun addRuleAction(text: String, description: String? = null, rules: () -> List<Rule>) = DumbAwareAction.create(text) {
        addRules(rules())
    }.apply {
        templatePresentation.description = description?.takeIf(String::isNotBlank)
    }

    /**
     * Reloads the rows from the edited settings merged with the rule set files, see [FoldableRulesResolver].
     */
    fun reload() = setValues(rulesProperty.get())

    /**
     * Appends the [rule] to the table and selects it.
     */
    fun addRule(rule: Rule) = addNewElement(rule)

    /**
     * Appends the [rules] to the table and selects the first of them.
     */
    fun addRules(rules: List<Rule>) {
        rules.forEach { super.addNewElement(it) }
        rules.firstOrNull()?.let { first ->
            SwingUtilities.invokeLater {
                tableView.selection = listOf(first)
            }
        }
    }

    override fun addNewElement(newElement: Rule) {
        super.addNewElement(newElement)
        SwingUtilities.invokeLater {
            tableView.selection = listOf(newElement)
        }
    }

    /**
     * The built-in ignored files rule is pinned: it can be disabled, but not removed. The rules of the files are removed
     * by editing the files.
     */
    override fun canDeleteElement(selection: Rule) = selection.type != RuleType.IGNORED_FILES && !selection.isFromFile

    /**
     * Writes the edited options and rules to a `.foldableprojectview.yaml` chosen by the user, see [RuleSetYaml.export].
     */
    private fun exportAction() = object : DumbAwareAction(
        message("foldableProjectView.settings.ruleSets.export"),
        message("foldableProjectView.settings.ruleSets.export.description"),
        AllIcons.ToolbarDecorator.Export,
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            val descriptor = FileSaverDescriptor(
                message("foldableProjectView.settings.ruleSets.export.title"),
                message("foldableProjectView.settings.ruleSets.export.message"),
            ).apply {
                withExtensionFilter(
                    IdeCoreBundle.message("file.chooser.files.label", "YAML"),
                    "yaml",
                    "yml",
                )
            }
            val baseDir = project.takeUnless { it.isDefault }?.guessProjectDir()
            val target = FileChooserFactory.getInstance()
                .createSaveFileDialog(descriptor, project.takeUnless { it.isDefault })
                .save(baseDir, RULE_SET_FILE)
                ?.file
                ?.toPath()
                ?: return
            val name = project.takeUnless { it.isDefault }?.name ?: message("foldableProjectView.name")
            writeRuleSet(target, RuleSetYaml.export(settingsProperty.get(), name))
        }
    }

    /**
     * Writes the edited options and rules to the user `rules.yaml` applied to every project, see [RuleSetRepository.writeUserRules].
     */
    private fun saveUserDefaultsAction() = object : DumbAwareAction(
        message("foldableProjectView.settings.ruleSets.saveUserDefaults"),
        message("foldableProjectView.settings.ruleSets.saveUserDefaults.description"),
        AllIcons.Actions.MenuSaveall,
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            val target = repository.userRulesFile
            if (target.exists()) {
                val answer = Messages.showYesNoDialog(
                    project,
                    message("foldableProjectView.settings.ruleSets.saveUserDefaults.confirm", target.toPresentableUserPath()),
                    message("foldableProjectView.settings.ruleSets.saveUserDefaults.title"),
                    Messages.getQuestionIcon(),
                )
                if (answer != Messages.YES) {
                    return
                }
            }
            writeRuleSet(target, RuleSetYaml.export(settingsProperty.get(), message("foldableProjectView.name")))
        }
    }

    /**
     * Writes the [ruleSet] to the [path], opens it in the editor, and re-emits the settings so the table and the preview
     * pick up the new file.
     */
    private fun writeRuleSet(path: Path, ruleSet: RuleSet) {
        runCatching { repository.write(path, ruleSet) }
            .onFailure {
                Messages.showErrorDialog(project, message("foldableProjectView.settings.ruleSets.failed", path, it.message.orEmpty()), message("foldableProjectView.name"))
                return
            }
        if (!project.isDefault) {
            LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)?.let { OpenFileDescriptor(project, it).navigate(true) }
        }
        settingsProperty.set(settingsProperty.get())
    }

    /**
     * The rows are copies of the resolved rules; the origin is not a part of the data class, so it is carried over by hand.
     */
    override fun cloneElement(variable: Rule) = variable.copy().also { it.origin = variable.origin }

    /**
     * Blank rules are never dropped silently: they are kept, marked in the table, and not applied until filled in,
     * see [Rule.isActive].
     */
    override fun isEmpty(element: Rule) = false

    /**
     * Checkbox that enables or disables the rule without removing it. It is the only editable property of the rules
     * coming from the rule set files.
     */
    private class EnabledColumn : ColumnInfo<Rule, Boolean>(message("foldableProjectView.settings.enabled")) {

        override fun getName() = ""

        override fun getTooltipText() = message("foldableProjectView.settings.enabled.tooltip")

        override fun getColumnClass() = Boolean::class.java

        override fun valueOf(item: Rule?) = item?.enabled

        override fun isCellEditable(item: Rule?) = true

        override fun setValue(item: Rule?, value: Boolean?) {
            item?.enabled = value ?: true
        }

        override fun getRenderer(item: Rule?) = BooleanTableCellRenderer()

        override fun getEditor(item: Rule?) = BooleanTableCellEditor()
    }

    private class ColorsColumn : ColumnInfo<Rule, String>(message("foldableProjectView.settings.color")) {

        override fun getName() = ""

        override fun getTooltipText() = message("foldableProjectView.settings.color.tooltip")

        override fun valueOf(item: Rule?) = COLOR_COLUMN_TEXT

        override fun getRenderer(item: Rule?) = object : DefaultTableCellRenderer() {

            override fun getTableCellRendererComponent(table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
                val hasNoColor = item?.foreground == null && item?.background == null
                return super.getTableCellRendererComponent(table, value, isSelected && hasNoColor, hasFocus, row, column)
                    .apply {
                        foreground = item?.foreground
                        background = item?.background
                    }
            }
        }
    }

    /**
     * Marks the rules whose matched elements are hidden instead of being grouped.
     */
    private class ActionColumn : ColumnInfo<Rule, RuleAction>(message("foldableProjectView.settings.action")) {

        override fun getName() = ""

        override fun getTooltipText() = message("foldableProjectView.settings.action.tooltip")

        override fun valueOf(item: Rule?) = item?.action

        override fun getRenderer(item: Rule?) = object : DefaultTableCellRenderer() {

            override fun getTableCellRendererComponent(table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
                super.getTableCellRendererComponent(table, null, isSelected, hasFocus, row, column)
                val hidden = value == RuleAction.HIDE
                horizontalAlignment = CENTER
                icon = AllIcons.Actions.ToggleVisibility.takeIf { hidden }
                toolTipText = message("foldableProjectView.settings.action.hide.tooltip").takeIf { hidden }
                return this
            }
        }
    }

    private class IconColumn : ColumnInfo<Rule, Icon>(message("foldableProjectView.settings.icon")) {

        override fun getName() = ""

        override fun getTooltipText() = message("foldableProjectView.settings.icon.comment")

        // Same icon as the group in the Project View, so a stale id falls back to the default one; hidden rules have no group
        override fun valueOf(item: Rule?) = item?.takeIf { it.action == RuleAction.GROUP && it.icon != null }?.let(RuleIcons::icon)

        override fun getRenderer(item: Rule?) = object : DefaultTableCellRenderer() {

            override fun getTableCellRendererComponent(table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
                super.getTableCellRendererComponent(table, null, isSelected, hasFocus, row, column)
                horizontalAlignment = CENTER
                icon = value as? Icon
                return this
            }
        }
    }

    /**
     * Shows the rule name, or a grayed placeholder for the rules that have no name yet.
     */
    private class NameColumn : ColumnInfo<Rule, String>(message("foldableProjectView.settings.name")) {

        override fun valueOf(item: Rule?) = item?.displayName

        override fun getRenderer(item: Rule?) = object : ColoredTableCellRenderer() {

            override fun customizeCellRenderer(table: JTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
                val attributes = when {
                    item?.name.isNullOrBlank() -> SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES
                    else -> SimpleTextAttributes.REGULAR_ATTRIBUTES
                }
                append(value?.toString().orEmpty(), attributes)
            }
        }
    }

    /**
     * Shows the rule patterns, or what the rule matches for the rules that do not list patterns directly.
     * Rules with invalid patterns, and rules with no patterns at all, are marked with a warning icon.
     */
    private class PatternsColumn : ColumnInfo<Rule, String>(message("foldableProjectView.settings.patterns")) {

        override fun valueOf(item: Rule?) = when {
            item == null -> null
            item.type == RuleType.IGNORED_FILES -> message("foldableProjectView.ignoredFiles.description")
            item.pattern.isBlank() -> message("foldableProjectView.settings.patterns.empty")
            item.type == RuleType.PATTERNS_FILE -> message("foldableProjectView.patternsFile.description", item.pattern)
            else -> item.pattern
        }

        override fun getRenderer(item: Rule?) = object : ColoredTableCellRenderer() {

            override fun customizeCellRenderer(table: JTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
                val attributes = when {
                    item?.type == RuleType.PATTERN && item.pattern.isNotBlank() -> SimpleTextAttributes.REGULAR_ATTRIBUTES
                    else -> SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES
                }
                append(value?.toString().orEmpty(), attributes)

                val warning = when {
                    item == null || !item.usesPatterns -> null
                    item.pattern.isBlank() -> message("foldableProjectView.settings.patterns.empty.tooltip")
                    item.type == RuleType.PATTERN -> GlobPatterns.invalidPatterns(item.pattern)
                        .takeIf { it.isNotEmpty() }
                        ?.let { message("foldableProjectView.settings.patterns.invalid", it.joinToString(" ")) }

                    else -> null
                }
                if (warning != null) {
                    icon = AllIcons.General.Warning
                    toolTipText = warning
                }
            }
        }
    }

    /**
     * Shows where the rule is stored: the IDE settings, or the rule set file it comes from, see [Rule.origin].
     */
    private class OriginColumn : ColumnInfo<Rule, String>(message("foldableProjectView.settings.origin")) {

        override fun getTooltipText() = message("foldableProjectView.settings.origin.tooltip")

        override fun valueOf(item: Rule?) = item?.origin?.displayName ?: message("foldableProjectView.settings.origin.settings")

        override fun getRenderer(item: Rule?) = object : ColoredTableCellRenderer() {

            override fun customizeCellRenderer(table: JTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
                append(value?.toString().orEmpty(), SimpleTextAttributes.GRAYED_ATTRIBUTES)
                toolTipText = item?.origin?.path?.toString()
            }
        }
    }
}
