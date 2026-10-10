package ski.chrzanow.foldableprojectview.settings

import com.intellij.execution.util.ListTableWithButtons
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionToolbarPosition
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.util.transform
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.*
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import ski.chrzanow.foldableprojectview.FoldableProjectViewBundle.message
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.COLOR_COLUMN_TEXT
import ski.chrzanow.foldableprojectview.psi.search.GlobPatterns
import java.awt.Component
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.table.DefaultTableCellRenderer

class FoldableRulesTable(private val settingsProperty: ObservableMutableProperty<FoldableProjectSettings>) : ListTableWithButtons<Rule>() {

    private val rulesProperty = settingsProperty.transform(
        { it.rules },
        {
            settingsProperty.get().apply {
                with(rules) {
                    clear()
                    addAll(it)
                }
            }
        },
    )

    init {
        tableView.apply {
            columnSelectionAllowed = false
            tableHeader.reorderingAllowed = false
            listOf(0, 1, 2).forEach { index ->
                columnModel.getColumn(index).apply {
                    maxWidth = JBUI.scale(24)
                    minWidth = JBUI.scale(24)
                }
            }
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

    override fun isUpDownSupported() = true

    override fun shouldEditRowOnCreation() = false

    override fun createListModel() = ListTableModel<Rule>(EnabledColumn(), ColorsColumn(), ActionColumn(), NameColumn(), PatternsColumn())

    override fun createElement() = Rule()

    /**
     * The `+` button offers an empty rule and the [RulePresets] instead of adding a rule right away.
     */
    override fun createAddAction() = AnActionButtonRunnable { button ->
        val group = DefaultActionGroup().apply {
            add(addRuleAction(message("foldableProjectView.settings.presets.empty")) { createElement() })
            add(Separator.create(message("foldableProjectView.settings.presets")))
            RulePresets.create().forEach { preset ->
                add(addRuleAction(preset.name) { preset })
            }
        }
        JBPopupFactory.getInstance()
            .createActionGroupPopup(null, group, button.dataContext, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true)
            .show(button.preferredPopupPoint)
    }

    private fun addRuleAction(text: String, rule: () -> Rule) = DumbAwareAction.create(text) {
        addRule(rule())
    }

    /**
     * Appends the [rule] to the table and selects it.
     */
    fun addRule(rule: Rule) = addNewElement(rule)

    override fun addNewElement(newElement: Rule) {
        super.addNewElement(newElement)
        SwingUtilities.invokeLater {
            tableView.selection = listOf(newElement)
        }
    }

    /**
     * The built-in ignored files rule is pinned: it can be disabled, but not removed.
     */
    override fun canDeleteElement(selection: Rule) = selection.type != RuleType.IGNORED_FILES

    override fun cloneElement(variable: Rule) = variable.copy()

    /**
     * Blank rules are never dropped silently: they are kept, marked in the table, and not applied until filled in,
     * see [Rule.isActive].
     */
    override fun isEmpty(element: Rule) = false

    /**
     * Checkbox that enables or disables the rule without removing it.
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
}
