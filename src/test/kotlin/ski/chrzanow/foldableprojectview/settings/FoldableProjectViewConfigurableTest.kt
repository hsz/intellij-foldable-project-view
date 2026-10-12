package ski.chrzanow.foldableprojectview.settings

import com.intellij.ide.DataManager
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.PropertiesComponent
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
import com.intellij.ui.OnePixelSplitter
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
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants
import ski.chrzanow.foldableprojectview.projectView.FoldableProjectViewNode
import ski.chrzanow.foldableprojectview.rules.RuleSetRepository
import ski.chrzanow.foldableprojectview.rules.isolateUserDirectory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.Scrollable
import javax.swing.SwingConstants

class FoldableProjectViewConfigurableTest : BasePlatformTestCase() {

    private lateinit var restoreUserDirectory: () -> Unit

    override fun setUp() {
        super.setUp()
        // The rule set files of the developer must not leak into the tests.
        restoreUserDirectory = isolateUserDirectory().second
    }

    override fun tearDown() {
        try {
            restoreUserDirectory()
        } finally {
            super.tearDown()
        }
    }

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

    private fun JComponent.button(text: String) = UIUtil.findComponentsOfType(this, JButton::class.java).single { it.text == text }

    private fun JComponent.checkBoxTexts() = UIUtil.findComponentsOfType(this, JBCheckBox::class.java).map { it.text }

    private fun JComponent.checkBox(text: String) = UIUtil.findComponentsOfType(this, JBCheckBox::class.java).single { it.text == text }

    private fun JComponent.comment(text: String) = UIUtil.findComponentsOfType(this, JEditorPane::class.java).single { it.text.contains(text) }

    private fun JComponent.label(text: String) = UIUtil.findComponentsOfType(this, JLabel::class.java).single { it.text == text }

    private fun JComponent.settingsScrollPane() = UIUtil.getParentOfType(JScrollPane::class.java, checkBox(message("foldableProjectView.settings.foldingEnabled")))!!

    private fun JComponent.previewPanel() = UIUtil.getParentOfType(OnePixelSplitter::class.java, previewTree())!!.secondComponent

    /**
     * The lines of an HTML tooltip in a stable order, as the matched elements are listed in the file system order.
     */
    private fun String.sortedLines() = removeSurrounding("<html>", "</html>").split("<br>").sorted().joinToString("<br>", "<html>", "</html>")

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

    fun `test built-in ignored files rule shows a description instead of the patterns`() = withRules(Rule("Docs", "*.md"), Rule.ignoredFiles()) {
        withConfigurable(FoldableProjectViewConfigurable(project)) { component ->
            val (patternField, filesField) = component.patternFields()
            val sourceLabel = component.label(message("foldableProjectView.settings.type"))
            val description = UIUtil.findComponentsOfType(component, JComponent::class.java).single { it.name == FoldableRulesEditor.IGNORED_FILES_DESCRIPTION_NAME }

            component.selectRule(0)
            assertTrue(sourceLabel.isVisible)
            assertTrue(patternField.isVisible)
            assertFalse(description.isVisible)

            component.selectRule(1)
            assertFalse(sourceLabel.isVisible)
            assertFalse(patternField.isVisible)
            assertFalse(filesField.isVisible)
            assertTrue(description.isVisible)
            assertEquals("", patternField.text)
            assertNull(ComponentValidator.getInstance(patternField).get().validationInfo)
        }
    }

    fun `test rule editor labels are bound to their fields`() = withRules(Rule("Docs", "*.md")) {
        withConfigurable(FoldableProjectViewConfigurable(project)) { component ->
            val labels = UIUtil.findComponentsOfType(component, JLabel::class.java)
            fun labelOf(field: JComponent) = UIUtil.removeMnemonic(labels.single { it.labelFor === field }.text)

            assertEquals(message("foldableProjectView.settings.name"), labelOf(component.nameField()))
            assertEquals(message("foldableProjectView.settings.patterns"), labelOf(component.patternFields()[0]))
            assertEquals(message("foldableProjectView.settings.patternsFile"), labelOf(component.patternFields()[1]))
        }
    }

    fun `test rule editor shows how many elements the rule matches`() = withRules(Rule("Docs", "*.md")) {
        myFixture.addFileToProject("README.md", "")
        myFixture.addFileToProject("CHANGELOG.md", "")
        myFixture.addFileToProject("docs/GUIDE.md", "")
        myFixture.addFileToProject("build.gradle.kts", "")

        withConfigurable(FoldableProjectViewConfigurable(project)) { component ->
            val label = component.matchesLabel()

            component.selectRule(0)
            label.assertTextEventually("Matches 2 elements in the module roots")
            assertEquals("<html>CHANGELOG.md<br>README.md</html>", label.toolTipText.sortedLines())

            // the count is not recomputed on every keystroke, but once the typing pauses
            component.patternFields()[0].text = "R"
            component.patternFields()[0].text = "README"
            component.patternFields()[0].text = "README.md"
            assertEquals("Matches 2 elements in the module roots", label.text)
            label.assertTextEventually("Matches one element in the module roots")

            component.patternFields()[0].text = "*.txt"
            label.assertTextEventually("Matches no elements in the module roots")
            assertNull(label.toolTipText)

            // the tooltip lists the matched elements relative to their content roots
            component.patternFields()[0].text = "*.md"
            component.checkBox(message("foldableProjectView.settings.foldNestedDirectories")).isSelected = true
            label.assertTextEventually("Matches 3 elements in the project")
            assertEquals("<html>CHANGELOG.md<br>README.md<br>docs/GUIDE.md</html>", label.toolTipText.sortedLines())

            component.patternFields()[0].text = ""
            label.assertTextEventually("")
            assertNull(label.toolTipText)
        }
    }

    fun `test matches tooltip lists the first elements only`() = withRules(Rule("Docs", "*.md")) {
        val count = FoldableProjectViewConstants.MATCH_PREVIEW_LIMIT + 3
        repeat(count) { myFixture.addFileToProject("file$it.md", "") }

        withConfigurable(FoldableProjectViewConfigurable(project)) { component ->
            val label = component.matchesLabel()

            component.selectRule(0)
            label.assertTextEventually("Matches $count elements in the module roots")

            val lines = label.toolTipText.removeSurrounding("<html>", "</html>").split("<br>")
            assertEquals(FoldableProjectViewConstants.MATCH_PREVIEW_LIMIT + 1, lines.size)
            assertEquals(message("foldableProjectView.settings.matches.more", 3), lines.last())
            lines.dropLast(1).forEach { assertTrue(it, it.matches(Regex("file\\d+\\.md"))) }
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

            // the table selection survives the apply, so the editor still shows the rule
            assertEquals(FoldableRulesEditor.CUSTOM_COLOR, comboBox.selectedItem)
            assertTrue(colorPanel.isVisible)

            comboBox.selectedItem = RuleColors.foreground("Blue")
            assertFalse(colorPanel.isVisible)
            apply()
            assertSame(RuleColors.foreground("Blue"), project.service<FoldableProjectSettings>().rules[0].foreground)

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

            // the first rule is selected when the page opens, and so is its group in the preview
            assertEquals(0, table.selectedRow)
            tree.assertSelectedGroupEventually(table.selectedObject as Rule)
            assertEquals("Docs", tree.selectedGroupRule!!.name)

            // table -> preview
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

            // the preview selection is cleared when no rule is selected
            table.clearSelection()
            tree.assertSelectedGroupEventually(null)
        }
    }

    fun `test rule selection is kept after apply, reset, and copy from defaults`() = withRules(
        Rule("Docs", "*.md"),
        Rule("Build", "*.kts"),
        Rule("CI", ".github"),
    ) {
        withDefaults(FoldableProjectSettings().apply { rules.add(Rule("Defaults", "*.txt")) }) {
            withConfigurable(FoldableProjectViewConfigurable(project)) { component ->
                val table = component.rulesTable()
                val nameField = component.nameField()

                // the first rule is selected when the page opens, so the editor is never blank
                assertEquals(0, table.selectedRow)
                assertEquals("Docs", nameField.text)
                assertTrue(nameField.isEnabled)

                component.selectRule(1)
                nameField.text = "Gradle"
                apply()
                assertEquals(1, table.selectedRow)
                assertEquals("Gradle", nameField.text)
                assertEquals("Gradle", project.service<FoldableProjectSettings>().rules[1].name)

                nameField.text = "Maven"
                reset()
                assertEquals(1, table.selectedRow)
                assertEquals("Gradle", nameField.text)
                assertFalse(isModified)

                // the selected row is clamped when fewer rules are loaded
                component.selectRule(2)
                component.button(message("foldableProjectView.settings.defaults.load")).doClick()
                assertEquals(listOf("Defaults", message("foldableProjectView.ignoredFiles")), table.items.map { (it as Rule).name })
                assertEquals(1, table.selectedRow)
                assertEquals(message("foldableProjectView.ignoredFiles"), nameField.text)
            }
        }
    }

    fun `test preview is read-only`() = withRules(Rule("Docs", "*.md")) {
        myFixture.addFileToProject("README.md", "")

        val configurable = FoldableProjectViewConfigurable(project)
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

    fun `test preview can be hidden to give the settings the whole page`() = withRules(Rule("Docs", "*.md")) {
        val properties = PropertiesComponent.getInstance()
        val key = "FoldableProjectView.settings.preview.visible"
        val original = properties.getValue(key)

        try {
            properties.unsetValue(key)
            withConfigurable(FoldableProjectViewConfigurable(project)) { component ->
                val previewPanel = component.previewPanel()
                val previewCheckBox = component.checkBox(message("foldableProjectView.settings.preview.show"))
                val scrollPane = component.settingsScrollPane()
                val view = scrollPane.viewport.view as Scrollable

                // the toggle goes right above "Enable folding"
                val checkBoxTexts = component.checkBoxTexts()
                assertEquals(checkBoxTexts.indexOf(message("foldableProjectView.settings.foldingEnabled")) - 1, checkBoxTexts.indexOf(previewCheckBox.text))

                // shown by default, with the settings column scrolled horizontally
                assertTrue(previewCheckBox.isSelected)
                assertTrue(previewPanel.isVisible)
                assertFalse(view.scrollableTracksViewportWidth)
                assertEquals(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED, scrollPane.horizontalScrollBarPolicy)

                // hiding is a UI preference: it is stored at once and does not make the page modified
                previewCheckBox.doClick()
                assertFalse(previewPanel.isVisible)
                assertTrue(view.scrollableTracksViewportWidth)
                assertEquals(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER, scrollPane.horizontalScrollBarPolicy)
                assertFalse(properties.getBoolean(key, true))
                assertFalse(isModified)

                previewCheckBox.doClick()
                assertTrue(previewPanel.isVisible)
                assertFalse(view.scrollableTracksViewportWidth)
                assertTrue(properties.getBoolean(key, true))
            }

            // the page reopens as it was left
            properties.setValue(key, false, true)
            withConfigurable(FoldableProjectViewConfigurable(project)) { component ->
                assertFalse(component.checkBox(message("foldableProjectView.settings.preview.show")).isSelected)
                assertFalse(component.previewPanel().isVisible)
                assertTrue((component.settingsScrollPane().viewport.view as Scrollable).scrollableTracksViewportWidth)
            }
        } finally {
            properties.setValue(key, original)
        }
    }

    fun `test settings column scrolls by a sensible step`() = withRules(Rule("Docs", "*.md")) {
        withConfigurable(FoldableProjectViewConfigurable(project)) { component ->
            val scrollPane = component.settingsScrollPane()
            val view = scrollPane.viewport.view as Scrollable

            assertFalse(view.scrollableTracksViewportHeight)
            assertTrue(view.getScrollableUnitIncrement(scrollPane.viewport.viewRect, SwingConstants.VERTICAL, 1) > 1)
        }
    }

    fun `test settings follow the page width without the preview for the default project`() {
        withConfigurable(FoldableProjectViewConfigurable(ProjectManager.getInstance().defaultProject)) { component ->
            val scrollPane = component.settingsScrollPane()

            assertDoesntContain(component.checkBoxTexts(), message("foldableProjectView.settings.preview.show"))
            assertTrue((scrollPane.viewport.view as Scrollable).scrollableTracksViewportWidth)
            assertEquals(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER, scrollPane.horizontalScrollBarPolicy)
        }
    }

    fun `test blank rules are kept in the table`() = withRules(Rule("Docs", "*.md")) {
        val repository = service<RuleSetRepository>()
        val settingsProperty = PropertyGraph().property(FoldableProjectSettings().apply { copyFrom(project.service<FoldableProjectSettings>()) })
        val table = FoldableRulesTable(settingsProperty, project)
        val preset = repository.copyRules(repository.builtinPresets.first()).single()

        table.addRule(Rule())
        UIUtil.dispatchAllInvocationEvents()
        table.addRules(listOf(preset))
        UIUtil.dispatchAllInvocationEvents()

        assertEquals(listOf("Docs", "", "Documentation"), settingsProperty.get().rules.map { it.name })
        assertEquals(preset, table.tableView.selection.single())
        assertEquals("docs", preset.id)
        assertNull("preset rules are copied into the settings", preset.origin)
    }

    fun `test file rules are listed read-only with their origin and the enabled override is stored`() = withRules(Rule("Docs", "*.md")) {
        myFixture.addFileToProject(
            FoldableProjectViewConstants.RULE_SET_FILE,
            """
            rules:
              - id: gradle
                name: Gradle
                patterns: ["*.kts"]
            """.trimIndent()
        )

        withConfigurable(FoldableProjectViewConfigurable(project)) { component ->
            val table = component.rulesTable()
            val rules = (0 until table.rowCount).map { table.getRow(it) as Rule }

            assertEquals(listOf("Gradle", "Docs"), rules.map { it.name })
            assertTrue(rules[0].isFromFile)
            assertEquals(message("foldableProjectView.settings.origin.project"), rules[0].origin!!.displayName)
            assertFalse(rules[1].isFromFile)

            val fileRuleLabel = UIUtil.findComponentsOfType(component, JBLabel::class.java).single { it.name == FoldableRulesEditor.FILE_RULE_LABEL_NAME }
            val ruleSetsLabel = UIUtil.findComponentsOfType(component, JBLabel::class.java).single { it.name == FoldableProjectViewConfigurable.RULE_SETS_LABEL_NAME }
            assertTrue(ruleSetsLabel.text, ruleSetsLabel.text.contains(FoldableProjectViewConstants.RULE_SET_FILE))

            component.selectRule(0)
            assertTrue(fileRuleLabel.isVisible)
            assertFalse(component.nameField().isEnabled)

            component.selectRule(1)
            assertFalse(fileRuleLabel.isVisible)
            assertTrue(component.nameField().isEnabled)

            // Disabling a file rule is stored as an override instead of a rule.
            table.setValueAt(false, 0, 0)
            assertTrue(isModified)
            apply()

            val settings = project.service<FoldableProjectSettings>()
            assertEquals(listOf("Docs"), settings.rules.map { it.name })
            assertEquals(listOf(RuleOverride("gradle", enabled = false)), settings.ruleOverrides)

            // Re-enabling it removes the override.
            table.setValueAt(true, 0, 0)
            apply()
            assertEmpty(settings.ruleOverrides)
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
                assertDoesntContain(component.checkBoxTexts(), message("foldableProjectView.settings.defaults.store"))
                assertDoesntContain(component.buttonTexts(), message("foldableProjectView.settings.defaults.load"))

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

    fun `test project page offers the defaults controls and does not change the defaults on apply`() {
        val defaults = FoldableProjectSettings().apply {
            rules.add(Rule("Docs", "*.md"))
        }
        val settings = project.service<FoldableProjectSettings>()
        val original = settings.hideEmptyGroups

        withDefaults(defaults) {
            try {
                withConfigurable(FoldableProjectViewConfigurable(project)) { component ->
                    assertContainsElements(component.checkBoxTexts(), message("foldableProjectView.settings.defaults.store"))
                    assertContainsElements(component.buttonTexts(), message("foldableProjectView.settings.defaults.load"))

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

    fun `test settings are stored as the defaults on apply only`() = withRules(Rule("Docs", "*.md"), Rule.ignoredFiles()) {
        val defaults = FoldableProjectSettings().apply {
            rules.add(Rule("Defaults", "*.txt"))
        }
        val settings = project.service<FoldableProjectSettings>()

        withDefaults(defaults) {
            withConfigurable(FoldableProjectViewConfigurable(project)) { component ->
                val storeCheckBox = component.checkBox(message("foldableProjectView.settings.defaults.store"))
                val loadButton = component.button(message("foldableProjectView.settings.defaults.load"))
                val differsComment = component.comment("Check the box")
                val sameComment = component.comment("These settings are the defaults")

                assertTrue(storeCheckBox.isEnabled)
                assertTrue(loadButton.isEnabled)
                assertTrue(differsComment.isVisible)
                assertFalse(sameComment.isVisible)
                assertFalse(isModified)

                // Checking the box alone makes the page modified, but changes nothing until it is applied
                storeCheckBox.isSelected = true
                assertTrue(isModified)
                assertEquals(listOf(Rule("Defaults", "*.txt")), service<FoldableProjectDefaults>().state!!.rules)

                // Reset (Cancel) discards the request
                reset()
                assertFalse(storeCheckBox.isSelected)
                assertFalse(isModified)
                assertEquals(listOf(Rule("Defaults", "*.txt")), service<FoldableProjectDefaults>().state!!.rules)

                // Apply stores the applied settings as the defaults and clears the box
                storeCheckBox.isSelected = true
                apply()
                assertEquals(settings.rules, service<FoldableProjectDefaults>().state!!.rules)
                assertEquals("Docs", service<FoldableProjectDefaults>().state!!.rules.first().name)
                assertFalse(storeCheckBox.isSelected)
                assertFalse(isModified)

                // Once the settings equal the defaults, the controls are disabled and the hint explains why
                assertFalse(storeCheckBox.isEnabled)
                assertFalse(loadButton.isEnabled)
                assertFalse(differsComment.isVisible)
                assertTrue(sameComment.isVisible)

                // Editing the settings away from the defaults enables the controls again; going back clears the box
                component.selectRule(0)
                val nameField = component.nameField()
                nameField.text = "Documentation"
                assertTrue(storeCheckBox.isEnabled)
                assertTrue(differsComment.isVisible)
                storeCheckBox.isSelected = true
                nameField.text = "Docs"
                assertFalse(storeCheckBox.isEnabled)
                assertFalse(storeCheckBox.isSelected)
                assertFalse(isModified)
            }
        }
    }
}
