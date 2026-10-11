package ski.chrzanow.foldableprojectview.settings

import com.intellij.ide.DataManager
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.ColorPanel
import com.intellij.ui.PopupHandler
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.ui.table.TableView
import com.intellij.ui.tree.TreeVisitor
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import ski.chrzanow.foldableprojectview.FoldableProjectViewBundle.message
import ski.chrzanow.foldableprojectview.projectView.FoldableProjectViewNode
import ski.chrzanow.foldableprojectview.psi.search.GlobPatterns
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JTree

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

    /**
     * The pattern fields of the rule editor: the first one lists the patterns, the second one the patterns files.
     */
    private fun JComponent.patternFields() = UIUtil.findComponentsOfType(this, ExpandableTextField::class.java)

    private fun JComponent.nameField() = UIUtil.findComponentsOfType(this, JBTextField::class.java)
        .single { it.emptyText.text == message("foldableProjectView.settings.name.placeholder") }

    private fun JComponent.matchesLabel() = UIUtil.findComponentsOfType(this, JBLabel::class.java).single { it.name == FoldableRulesEditor.MATCHES_LABEL_NAME }

    /**
     * The matches label is updated with a delay after the last change, see [FoldableRulesEditor].
     */
    private fun JBLabel.assertTextEventually(expected: String) =
        PlatformTestUtil.waitWithEventsDispatching({ "Expected '$expected', but was '$text'" }, { text == expected }, 5)

    private fun JComponent.rulesTable() = UIUtil.findComponentsOfType(this, TableView::class.java).single()

    private fun JComponent.selectRule(index: Int) = rulesTable().setRowSelectionInterval(index, index)

    private fun JComponent.previewTree() = UIUtil.findComponentsOfType(this, JTree::class.java).single { it.name == FoldableProjectViewConfigurable.PREVIEW_TREE_NAME }

    /**
     * The rule of the folding group selected in the preview, if any.
     */
    private val JTree.selectedGroupRule: Rule?
        get() = (TreeUtil.getLastUserObject(selectionPath) as? FoldableProjectViewNode)?.value

    /**
     * The preview is restructured asynchronously, so its selection follows the table with a delay.
     */
    private fun JTree.assertSelectedGroupEventually(expected: Rule?) =
        PlatformTestUtil.waitWithEventsDispatching({ "Expected '$expected' selected in the preview, but was '$selectedGroupRule'" }, { selectedGroupRule == expected }, 10)

    /**
     * The color combo box and the color picker editing the given rule color property (`foreground` or `background`).
     */
    private fun JComponent.colorChooser(property: String): Pair<ComboBox<*>, ColorPanel> {
        val comboBoxes = UIUtil.findComponentsOfType(this, ComboBox::class.java)
        val colorPanels = UIUtil.findComponentsOfType(this, ColorPanel::class.java)
        val index = comboBoxes.indexOfFirst { it.name == property }
        return comboBoxes[index] to colorPanels[index]
    }

    /**
     * Runs [block] with the project rules replaced by [rules], restoring the original ones afterwards.
     */
    private fun withRules(vararg rules: Rule, block: () -> Unit) {
        val settings = project.service<FoldableProjectSettings>()
        val originalRules = settings.rules.toList()

        try {
            settings.rules.clear()
            settings.rules.addAll(rules)
            block()
        } finally {
            settings.rules.clear()
            settings.rules.addAll(originalRules)
        }
    }

    fun `test invalid patterns are reported as a warning`() = withRules(Rule("Docs", "*.md [z-a].md")) {
        withConfigurable(FoldableProjectViewConfigurable(project)) { component ->
            component.selectRule(0)
            val patternField = component.patternFields()[0]
            val validator = ComponentValidator.getInstance(patternField).get()

            patternField.text = "*.md [z-a].md"
            val warning = validator.validationInfo!!
            assertTrue(warning.warning)
            assertEquals(message("foldableProjectView.settings.patterns.invalid", "[z-a].md"), warning.message)

            patternField.text = "*.md"
            assertNull(validator.validationInfo)
        }
    }

    fun `test blank name and patterns are reported as warnings`() = withRules(Rule("Docs", "*.md")) {
        withConfigurable(FoldableProjectViewConfigurable(project)) { component ->
            component.selectRule(0)
            val nameField = component.nameField()
            val patternField = component.patternFields()[0]
            val nameValidator = ComponentValidator.getInstance(nameField).get()
            val patternValidator = ComponentValidator.getInstance(patternField).get()

            assertEquals("Docs", nameField.text)
            assertNull(nameValidator.validationInfo)
            assertNull(patternValidator.validationInfo)

            nameField.text = " "
            assertEquals(message("foldableProjectView.settings.name.empty"), nameValidator.validationInfo!!.message)
            assertTrue(nameValidator.validationInfo!!.warning)

            patternField.text = ""
            assertEquals(message("foldableProjectView.settings.patterns.empty.tooltip"), patternValidator.validationInfo!!.message)
            assertTrue(patternValidator.validationInfo!!.warning)

            nameField.text = "Documentation"
            patternField.text = "*.md"
            assertNull(nameValidator.validationInfo)
            assertNull(patternValidator.validationInfo)
        }
    }

    fun `test blank patterns of the built-in ignored files rule are not reported`() = withRules(Rule.ignoredFiles()) {
        withConfigurable(FoldableProjectViewConfigurable(project)) { component ->
            component.selectRule(0)
            val patternField = component.patternFields()[0]

            assertEquals("", patternField.text)
            assertNull(ComponentValidator.getInstance(patternField).get().validationInfo)
        }
    }

    fun `test rule editor shows how many elements the rule matches`() = withRules(Rule("Docs", "*.md")) {
        myFixture.addFileToProject("README.md", "")
        myFixture.addFileToProject("CHANGELOG.md", "")
        myFixture.addFileToProject("docs/GUIDE.md", "")
        myFixture.addFileToProject("build.gradle.kts", "")

        withConfigurable(FoldableProjectViewConfigurable(project)) { component ->
            val label = component.matchesLabel()
            assertEquals("", label.text)

            component.selectRule(0)
            label.assertTextEventually("Matches 2 elements in the module roots")

            // the count is not recomputed on every keystroke, but once the typing pauses
            component.patternFields()[0].text = "R"
            component.patternFields()[0].text = "README"
            component.patternFields()[0].text = "README.md"
            assertEquals("Matches 2 elements in the module roots", label.text)
            label.assertTextEventually("Matches one element in the module roots")

            component.patternFields()[0].text = "*.txt"
            label.assertTextEventually("Matches no elements in the module roots")

            component.patternFields()[0].text = "*.md"
            component.checkBox(message("foldableProjectView.settings.foldNestedDirectories")).isSelected = true
            label.assertTextEventually("Matches 3 elements in the project")

            component.patternFields()[0].text = ""
            label.assertTextEventually("")
        }
    }

    fun `test rule colors are picked from the theme-aware palette or customized`() = withRules(Rule("Docs", "*.md")) {
        withConfigurable(FoldableProjectViewConfigurable(project)) { component ->
            component.selectRule(0)
            val (comboBox, colorPanel) = component.colorChooser("foreground")
            val checkBox = component.checkBox(message("foldableProjectView.settings.foreground"))

            assertEquals(RuleColors.foregrounds + FoldableRulesEditor.CUSTOM_COLOR, (0 until comboBox.itemCount).map { comboBox.getItemAt(it) })
            assertFalse(checkBox.isSelected)
            assertFalse(comboBox.isEnabled)
            assertFalse(colorPanel.isVisible)

            checkBox.isSelected = true
            assertTrue(comboBox.isEnabled)
            assertEquals(RuleColors.foregrounds.first(), comboBox.selectedItem)
            assertFalse(colorPanel.isVisible)

            comboBox.selectedItem = RuleColors.foreground("Green")
            assertFalse(colorPanel.isVisible)

            // the picker starts from the previously selected named color, as a custom color used as is in both themes
            comboBox.selectedItem = FoldableRulesEditor.CUSTOM_COLOR
            assertTrue(colorPanel.isVisible)
            assertEquals(RuleColors.foreground("Green")!!.rgb, colorPanel.selectedColor!!.rgb)
            assertTrue(isModified)
            apply()

            val stored = project.service<FoldableProjectSettings>().rules[0].foreground!!
            assertTrue(stored.isCustom)
            assertEquals(RuleColors.foreground("Green")!!.rgb, stored.rgb)

            component.selectRule(0) // the table selection is reset on apply
            assertEquals(FoldableRulesEditor.CUSTOM_COLOR, comboBox.selectedItem)
            assertTrue(colorPanel.isVisible)

            comboBox.selectedItem = RuleColors.foreground("Blue")
            assertFalse(colorPanel.isVisible)
            apply()
            assertSame(RuleColors.foreground("Blue"), project.service<FoldableProjectSettings>().rules[0].foreground)

            component.selectRule(0)
            checkBox.isSelected = false
            apply()
            assertNull(project.service<FoldableProjectSettings>().rules[0].foreground)
        }
    }

    fun `test selected rule and its folding group are kept in sync between the table and the preview`() = withRules(
        Rule("Docs", "*.md"),
        Rule("Build", "*.kts"),
    ) {
        myFixture.addFileToProject("README.md", "")
        myFixture.addFileToProject("build.gradle.kts", "")

        withConfigurable(FoldableProjectViewConfigurable(project)) { component ->
            val tree = component.previewTree()
            val table = component.rulesTable()
            assertNull(tree.selectedGroupRule)

            // table -> preview
            component.selectRule(0)
            tree.assertSelectedGroupEventually(table.selectedObject as Rule)
            assertEquals("Docs", tree.selectedGroupRule!!.name)

            component.selectRule(1)
            tree.assertSelectedGroupEventually(table.selectedObject as Rule)
            assertEquals("Build", tree.selectedGroupRule!!.name)

            // preview -> table
            val docsRule = table.items[0]
            PlatformTestUtil.waitForPromise(TreeUtil.promiseSelect(tree) { path ->
                when ((TreeUtil.getLastUserObject(path) as? FoldableProjectViewNode)?.value) {
                    docsRule -> TreeVisitor.Action.INTERRUPT
                    null -> TreeVisitor.Action.CONTINUE
                    else -> TreeVisitor.Action.SKIP_CHILDREN
                }
            })
            assertSame(docsRule, table.selectedObject)

            // the selection is cleared when no rule is selected
            apply()
            tree.assertSelectedGroupEventually(null)
        }
    }

    fun `test preview is read-only`() = withRules(Rule("Docs", "*.md")) {
        myFixture.addFileToProject("README.md", "")

        val configurable = FoldableProjectViewConfigurable(project)
        assertTrue(configurable is Configurable.NoScroll)

        withConfigurable(configurable) { component ->
            val tree = component.previewTree()
            assertEmpty(tree.mouseListeners.filterIsInstance<PopupHandler>())

            component.selectRule(0)
            tree.assertSelectedGroupEventually(component.rulesTable().selectedObject as Rule)

            // select the file folded into the group: it must not be exposed to the navigation and the Project View actions
            PlatformTestUtil.waitForPromise(TreeUtil.promiseSelect(tree) { path ->
                when (TreeUtil.getLastUserObject(path)) {
                    is PsiFileNode -> TreeVisitor.Action.INTERRUPT
                    else -> TreeVisitor.Action.CONTINUE
                }
            })
            assertNull(tree.selectedGroupRule)

            val dataContext = DataManager.getInstance().getDataContext(tree)
            assertNull(CommonDataKeys.NAVIGATABLE_ARRAY.getData(dataContext))
            assertNull(CommonDataKeys.NAVIGATABLE.getData(dataContext))
            assertNull(CommonDataKeys.PSI_ELEMENT.getData(dataContext))
            assertNull(CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext))
        }
    }

    fun `test blank rules are kept in the table`() = withRules(Rule("Docs", "*.md")) {
        val settingsProperty = PropertyGraph().property(FoldableProjectSettings().apply { copyFrom(project.service<FoldableProjectSettings>()) })
        val table = FoldableRulesTable(settingsProperty)

        table.addRule(Rule())
        UIUtil.dispatchAllInvocationEvents()
        table.addRule(RulePresets.create().first())
        UIUtil.dispatchAllInvocationEvents()

        assertEquals(listOf("Docs", "", "Documentation"), settingsProperty.get().rules.map { it.name })
        assertEquals(RulePresets.create().first(), table.tableView.selection.single())
    }

    fun `test presets are valid`() {
        val presets = RulePresets.create()

        assertTrue(presets.isNotEmpty())
        assertEquals(presets.size, presets.map { it.name }.distinct().size)
        presets.forEach { preset ->
            assertTrue(preset.name.isNotBlank())
            assertTrue(preset.isActive)
            assertEquals(RuleType.PATTERN, preset.type)
            assertEmpty(GlobPatterns.invalidPatterns(preset.pattern))
        }
    }

    fun `test patterns file field is shown for the patterns file rules and reports missing files`() = withRules(
        Rule("Docs", "*.md"),
        Rule("Ignore", ".foldignore", type = RuleType.PATTERNS_FILE),
    ) {
        myFixture.addFileToProject(".foldignore", "*.md\n")

        withConfigurable(FoldableProjectViewConfigurable(project)) { component ->
            val (patternField, filesField) = component.patternFields()

            component.selectRule(0)
            assertTrue(patternField.isVisible)
            assertFalse(filesField.isVisible)

            component.selectRule(1)
            assertFalse(patternField.isVisible)
            assertTrue(filesField.isVisible)
            assertEquals(".foldignore", filesField.text)

            val validator = ComponentValidator.getInstance(filesField).get()

            filesField.text = ".foldignore .dockerignore"
            val warning = validator.validationInfo!!
            assertTrue(warning.warning)
            assertEquals(message("foldableProjectView.settings.patternsFile.notFound", ".dockerignore"), warning.message)

            filesField.text = ".foldignore"
            assertNull(validator.validationInfo)
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
