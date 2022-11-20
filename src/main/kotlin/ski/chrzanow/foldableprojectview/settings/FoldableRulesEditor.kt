package ski.chrzanow.foldableprojectview.settings

import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.util.isNotNull
import com.intellij.openapi.options.UiDslUnnamedConfigurable
import com.intellij.ui.ColorPanel
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.layout.ComponentPredicate
import ski.chrzanow.foldableprojectview.FoldableProjectViewBundle.message
import ski.chrzanow.foldableprojectview.bindColor
import ski.chrzanow.foldableprojectview.bindColorControl
import ski.chrzanow.foldableprojectview.bindText
import ski.chrzanow.foldableprojectview.settings.FoldableProjectState.Rule

class FoldableRulesEditor(val ruleProperty: ObservableMutableProperty<Rule?>) : UiDslUnnamedConfigurable.Simple() {

    private lateinit var backgroundCheckBox: Cell<JBCheckBox>
    private lateinit var backgroundColorPanel: Cell<ColorPanel>
    private lateinit var foregroundCheckBox: Cell<JBCheckBox>
    private lateinit var foregroundColorPanel: Cell<ColorPanel>
    private lateinit var nameTextField: Cell<JBTextField>
    private lateinit var patternTextField: Cell<ExpandableTextField>

    private val selectedRowPredicate = object : ComponentPredicate() {

        override fun invoke() = ruleProperty.isNotNull().get()

        override fun addListener(listener: (Boolean) -> Unit) =
            ruleProperty.afterChange {
                listener(it != null)
            }
    }

    override fun Panel.createContent() {
        rowsRange {
            row(message("foldableProjectView.settings.name")) {
                nameTextField = textField()
                    .horizontalAlign(HorizontalAlign.FILL)
                    .bindText(ruleProperty, Rule::name)

            }
            row(message("foldableProjectView.settings.rules")) {
                patternTextField = expandableTextField()
                    .horizontalAlign(HorizontalAlign.FILL)
                    .comment(message("foldableProjectView.settings.rules.comment"), 40)
                    .bindText(ruleProperty, Rule::pattern)
            }
            row {
                foregroundCheckBox = checkBox(message("foldableProjectView.settings.foreground"))
                    .bindColorControl(ruleProperty, Rule::foreground, JBColor.foreground().brighter())

                foregroundColorPanel = cell(ColorPanel())
                    .horizontalAlign(HorizontalAlign.RIGHT)
                    .enabledIf(foregroundCheckBox.selected)
                    .bindColor(ruleProperty, Rule::foreground)
            }
            row {
                backgroundCheckBox = checkBox(message("foldableProjectView.settings.background"))
                    .bindColorControl(ruleProperty, Rule::background, JBColor.background().darker())

                backgroundColorPanel = cell(ColorPanel())
                    .horizontalAlign(HorizontalAlign.RIGHT)
                    .enabledIf(backgroundCheckBox.selected)
                    .bindColor(ruleProperty, Rule::background)
            }
        }.enabledIf(selectedRowPredicate)
    }
}
