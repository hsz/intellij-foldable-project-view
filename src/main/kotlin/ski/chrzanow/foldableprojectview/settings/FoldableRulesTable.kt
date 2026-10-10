package ski.chrzanow.foldableprojectview.settings

import com.intellij.execution.util.ListTableWithButtons
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionToolbarPosition
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.util.transform
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

    override fun isEmpty(element: Rule) = element.type != RuleType.IGNORED_FILES && element.pattern.isBlank()

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

    private class NameColumn : ColumnInfo<Rule, String>(message("foldableProjectView.settings.name")) {

        override fun valueOf(item: Rule?) = item?.name
    }

    /**
     * Shows the rule patterns, or what the rule matches for the rules that do not list patterns directly.
     * Rules with invalid patterns are marked with a warning icon.
     */
    private class PatternsColumn : ColumnInfo<Rule, String>(message("foldableProjectView.settings.patterns")) {

        override fun valueOf(item: Rule?) = when (item?.type) {
            RuleType.IGNORED_FILES -> message("foldableProjectView.ignoredFiles.description")
            RuleType.PATTERNS_FILE -> message("foldableProjectView.patternsFile.description", item.pattern)
            else -> item?.pattern
        }

        override fun getRenderer(item: Rule?) = object : ColoredTableCellRenderer() {

            override fun customizeCellRenderer(table: JTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
                val attributes = when (item?.type) {
                    RuleType.PATTERN -> SimpleTextAttributes.REGULAR_ATTRIBUTES
                    else -> SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES
                }
                append(value?.toString().orEmpty(), attributes)

                val invalidPatterns = when (item?.type) {
                    RuleType.PATTERN -> GlobPatterns.invalidPatterns(item.pattern)
                    else -> emptyList()
                }
                if (invalidPatterns.isNotEmpty()) {
                    icon = AllIcons.General.Warning
                    toolTipText = message("foldableProjectView.settings.patterns.invalid", invalidPatterns.joinToString(" "))
                }
            }
        }
    }
}
