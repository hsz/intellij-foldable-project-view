package ski.chrzanow.foldableprojectview.actionSystem

import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import ski.chrzanow.foldableprojectview.settings.FoldableProjectSettings
import ski.chrzanow.foldableprojectview.settings.FoldableProjectSettingsListener
import ski.chrzanow.foldableprojectview.settings.Rule
import ski.chrzanow.foldableprojectview.settings.RuleType
import ski.chrzanow.foldableprojectview.settings.foldIgnoredFiles
import ski.chrzanow.foldableprojectview.settings.ignoredFilesRule

class FoldableToggleOptionActionTest : BasePlatformTestCase() {

    private lateinit var settings: FoldableProjectSettings
    private var foldingEnabled = true
    private lateinit var originalRules: List<Rule>

    override fun setUp() {
        super.setUp()
        settings = project.service()
        foldingEnabled = settings.foldingEnabled
        originalRules = settings.rules.toList()
    }

    override fun tearDown() {
        try {
            settings.foldingEnabled = foldingEnabled
            settings.rules.clear()
            settings.rules.addAll(originalRules)
        } finally {
            super.tearDown()
        }
    }

    private fun event(action: ToggleAction) = TestActionEvent.createTestEvent(action) { dataId ->
        if (dataId == "project") project else null
    }

    fun `test fold root files action reflects and changes folding enabled`() {
        val action = FoldRootFilesAction()
        settings.foldingEnabled = true
        assertTrue(action.isSelected(event(action)))

        action.setSelected(event(action), false)

        assertFalse(settings.foldingEnabled)
        assertFalse(action.isSelected(event(action)))
    }

    fun `test fold ignored files action reflects and changes fold ignored files`() {
        val action = FoldIgnoredFilesAction()
        settings.foldIgnoredFiles = false
        assertFalse(action.isSelected(event(action)))

        action.setSelected(event(action), true)

        assertTrue(settings.foldIgnoredFiles)
        assertTrue(settings.ignoredFilesRule!!.enabled)
    }

    fun `test fold ignored files action toggles the ignored files rule keeping its customization`() {
        val action = FoldIgnoredFilesAction()
        settings.rules.clear()
        settings.rules.add(Rule("Docs", "*.md"))
        settings.rules.add(Rule.ignoredFiles().copy(name = "Noise"))
        settings.rules.add(Rule("Gradle", "gradle*"))
        assertTrue(action.isSelected(event(action)))

        action.setSelected(event(action), false)

        assertFalse(action.isSelected(event(action)))
        assertEquals(listOf("Docs", "Noise", "Gradle"), settings.rules.map { it.name })
        assertEquals(Rule.ignoredFiles(enabled = false).copy(name = "Noise"), settings.rules[1])
        assertEquals(1, settings.rules.count { it.type == RuleType.IGNORED_FILES })
    }

    fun `test changing option notifies listeners`() {
        val action = FoldRootFilesAction()
        settings.foldingEnabled = true
        var notifications = 0
        project.messageBus.connect(testRootDisposable)
            .subscribe(FoldableProjectSettingsListener.TOPIC, FoldableProjectSettingsListener { notifications++ })

        action.setSelected(event(action), false)
        assertEquals(1, notifications)

        action.setSelected(event(action), false)
        assertEquals("unchanged value must not notify", 1, notifications)
    }
}
