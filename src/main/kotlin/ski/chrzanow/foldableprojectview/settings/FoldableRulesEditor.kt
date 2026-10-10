package ski.chrzanow.foldableprojectview.settings

import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.util.isNotNull
import com.intellij.openapi.options.UiDslUnnamedConfigurable
import com.intellij.ui.ColorPanel
import com.intellij.ui.JBColor
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.layout.ComponentPredicate
import ski.chrzanow.foldableprojectview.FoldableProjectViewBundle.message
import ski.chrzanow.foldableprojectview.bindColor
import ski.chrzanow.foldableprojectview.bindColorControl
import ski.chrzanow.foldableprojectview.bindSelectedItem
import ski.chrzanow.foldableprojectview.bindText

class FoldableRulesEditor(private val ruleProperty: ObservableMutableProperty<Rule?>) : UiDslUnnamedConfigurable.Simple() {

    private val selectedRowPredicate = object : ComponentPredicate() {

        override fun invoke() = ruleProperty.isNotNull().get()

        override fun addListener(listener: (Boolean) -> Unit) =
            ruleProperty.afterChange {
                listener(it != null)
            }
    }

    /**
     * Colors apply to the folding group only, so they are relevant for the [RuleAction.GROUP] action only.
     */
    private val groupActionPredicate = rulePredicate { it.action == RuleAction.GROUP }

    /**
     * Patterns are editable for the [RuleType.PATTERN] rules only; the built-in ignored files rule has none.
     */
    private val patternRulePredicate = rulePredicate { it.type == RuleType.PATTERN }

    private fun rulePredicate(predicate: (Rule) -> Boolean) = object : ComponentPredicate() {

        override fun invoke() = ruleProperty.get()?.let(predicate) == true

        override fun addListener(listener: (Boolean) -> Unit) =
            ruleProperty.afterChange {
                listener(it?.let(predicate) == true)
            }
    }

    override fun Panel.createContent() {
        rowsRange {
            row(message("foldableProjectView.settings.name")) {
                textField()
                    .align(Align.FILL)
                    .bindText(ruleProperty, Rule::name)
            }
            row(message("foldableProjectView.settings.rules")) {
                expandableTextField()
                    .align(Align.FILL)
                    .comment(message("foldableProjectView.settings.rules.comment"), 40)
                    .bindText(ruleProperty, Rule::pattern)
            }.enabledIf(patternRulePredicate)
            row(message("foldableProjectView.settings.action")) {
                segmentedButton(RuleAction.entries) { text = it.text }
                    .bindSelectedItem(ruleProperty, Rule::action, RuleAction.GROUP)
            }.rowComment(message("foldableProjectView.settings.action.comment"), 40)
            row {
                val foregroundCheckBox = checkBox(message("foldableProjectView.settings.foreground"))
                    .bindColorControl(ruleProperty, Rule::foreground, JBColor.foreground().brighter())

                cell(ColorPanel())
                    .align(Align.FILL)
                    .enabledIf(foregroundCheckBox.selected)
                    .bindColor(ruleProperty, Rule::foreground)
            }.enabledIf(groupActionPredicate)
            row {
                val backgroundCheckBox = checkBox(message("foldableProjectView.settings.background"))
                    .bindColorControl(ruleProperty, Rule::background, JBColor.background().darker())

                cell(ColorPanel())
                    .align(Align.FILL)
                    .enabledIf(backgroundCheckBox.selected)
                    .bindColor(ruleProperty, Rule::background)
            }.enabledIf(groupActionPredicate)
        }.enabledIf(selectedRowPredicate)
    }

    private val RuleAction.text
        get() = when (this) {
            RuleAction.GROUP -> message("foldableProjectView.settings.action.group")
            RuleAction.HIDE -> message("foldableProjectView.settings.action.hide")
        }
}
