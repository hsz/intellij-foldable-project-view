package ski.chrzanow.foldableprojectview.actionSystem

import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import ski.chrzanow.foldableprojectview.settings.FoldableProjectSettings
import ski.chrzanow.foldableprojectview.settings.FoldableProjectSettingsListener

class FoldableToggleOptionActionTest : BasePlatformTestCase() {

    private lateinit var settings: FoldableProjectSettings
    private var foldingEnabled = true
    private var foldIgnoredFiles = true

    override fun setUp() {
        super.setUp()
        settings = project.service()
        foldingEnabled = settings.foldingEnabled
        foldIgnoredFiles = settings.foldIgnoredFiles
    }

    override fun tearDown() {
        try {
            settings.foldingEnabled = foldingEnabled
            settings.foldIgnoredFiles = foldIgnoredFiles
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
