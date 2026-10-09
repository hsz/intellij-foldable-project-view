package ski.chrzanow.foldableprojectview.settings

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class FoldableProjectSettingsStateTest : BasePlatformTestCase() {

    fun `test defaults`() {
        val settings = FoldableProjectSettings()

        assertTrue(settings.foldingEnabled)
        assertTrue(settings.matchDirectories)
        assertTrue(settings.hideEmptyGroups)
        assertFalse(settings.hideAllGroups)
        assertTrue(settings.caseSensitive)
        assertTrue(settings.foldIgnoredFiles)
        assertTrue(settings.rules.isEmpty())
    }

    fun `test getState returns itself`() {
        val settings = FoldableProjectSettings()

        assertSame(settings, settings.state)
    }

    fun `test loadState copies values`() {
        val source = FoldableProjectSettings().apply {
            foldingEnabled = false
            hideAllGroups = true
            caseSensitive = false
            rules.add(Rule("Docs", "*.md"))
        }
        val target = FoldableProjectSettings().apply { loadState(source) }

        assertFalse(target.foldingEnabled)
        assertTrue(target.hideAllGroups)
        assertFalse(target.caseSensitive)
        assertEquals(listOf(Rule("Docs", "*.md")), target.rules)
    }

    fun `test project service is available and shared`() {
        assertSame(project.service<FoldableProjectSettings>(), project.service<FoldableProjectSettings>())
    }

    fun `test update applies block and notifies listeners`() {
        val settings = project.service<FoldableProjectSettings>()
        val received = mutableListOf<FoldableProjectSettings>()
        val original = settings.hideAllGroups

        project.messageBus.connect(testRootDisposable)
            .subscribe(FoldableProjectSettingsListener.TOPIC, FoldableProjectSettingsListener { received.add(it) })

        try {
            settings.update(project) { hideAllGroups = !original }

            assertEquals(!original, settings.hideAllGroups)
            assertEquals(1, received.size)
            assertSame(settings, received.single())
        } finally {
            settings.hideAllGroups = original
        }
    }
}
