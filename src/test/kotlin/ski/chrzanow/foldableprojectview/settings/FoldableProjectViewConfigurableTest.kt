package ski.chrzanow.foldableprojectview.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.util.ui.UIUtil
import ski.chrzanow.foldableprojectview.FoldableProjectViewBundle.message
import javax.swing.JButton
import javax.swing.JComponent

class FoldableProjectViewConfigurableTest : BasePlatformTestCase() {

    /**
     * Runs [block] with the application-level defaults replaced by [defaults], restoring the original ones afterwards.
     */
    private fun withDefaults(defaults: FoldableProjectSettings, block: () -> Unit) {
        val service = service<FoldableProjectDefaults>()
        val original = service.state

        try {
            service.loadState(defaults)
            block()
        } finally {
            service.loadState(original ?: FoldableProjectSettings())
        }
    }

    private fun <T> withConfigurable(configurable: Configurable, block: Configurable.(JComponent) -> T): T = try {
        val component = configurable.createComponent()!!
        configurable.reset()
        configurable.block(component)
    } finally {
        configurable.disposeUIResources()
    }

    private fun JComponent.buttonTexts() = UIUtil.findComponentsOfType(this, JButton::class.java).map { it.text }

    private fun JComponent.checkBox(text: String) = UIUtil.findComponentsOfType(this, JBCheckBox::class.java).single { it.text == text }

    fun `test invalid patterns are reported as a warning`() {
        val settings = project.service<FoldableProjectSettings>()
        val originalRules = settings.rules.toList()

        try {
            settings.rules.clear()
            settings.rules.add(Rule("Docs", "*.md [z-a].md"))

            withConfigurable(FoldableProjectViewConfigurable(project)) { component ->
                val patternField = UIUtil.findComponentsOfType(component, ExpandableTextField::class.java).single()
                val validator = ComponentValidator.getInstance(patternField).get()

                patternField.text = "*.md [z-a].md"
                val warning = validator.validationInfo!!
                assertTrue(warning.warning)
                assertEquals(message("foldableProjectView.settings.rules.invalid", "[z-a].md"), warning.message)

                patternField.text = "*.md"
                assertNull(validator.validationInfo)
            }
        } finally {
            settings.rules.clear()
            settings.rules.addAll(originalRules)
        }
    }

    fun `test default project page edits the defaults for new projects`() {
        val defaults = FoldableProjectSettings().apply {
            rules.add(Rule("Docs", "*.md"))
        }

        withDefaults(defaults) {
            withConfigurable(FoldableProjectViewConfigurable(ProjectManager.getInstance().defaultProject)) { component ->
                assertFalse(isModified)
                assertDoesntContain(
                    component.buttonTexts(),
                    message("foldableProjectView.settings.defaults.store"),
                    message("foldableProjectView.settings.defaults.load"),
                )

                component.checkBox(message("foldableProjectView.settings.foldingEnabled")).isSelected = false
                assertTrue(isModified)

                apply()

                val stored = service<FoldableProjectDefaults>().state!!
                assertFalse(stored.foldingEnabled)
                assertEquals(listOf(Rule("Docs", "*.md"), Rule.ignoredFiles()), stored.rules)
                assertFalse(isModified)
            }
        }
    }

    fun `test project page offers the defaults buttons and does not change the defaults on apply`() {
        val defaults = FoldableProjectSettings().apply {
            rules.add(Rule("Docs", "*.md"))
        }
        val settings = project.service<FoldableProjectSettings>()
        val original = settings.hideEmptyGroups

        withDefaults(defaults) {
            try {
                withConfigurable(FoldableProjectViewConfigurable(project)) { component ->
                    assertContainsElements(
                        component.buttonTexts(),
                        message("foldableProjectView.settings.defaults.store"),
                        message("foldableProjectView.settings.defaults.load"),
                    )

                    component.checkBox(message("foldableProjectView.settings.hideEmptyGroups")).isSelected = !original
                    assertTrue(isModified)

                    apply()

                    assertEquals(!original, settings.hideEmptyGroups)
                    assertEquals(listOf(Rule("Docs", "*.md")), service<FoldableProjectDefaults>().state!!.rules)
                }
            } finally {
                settings.hideEmptyGroups = original
            }
        }
    }
}
