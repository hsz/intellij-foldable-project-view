package ski.chrzanow.foldableprojectview.settings

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class FoldableProjectSettingsStateTest : BasePlatformTestCase() {

    fun `test defaults`() {
        val settings = FoldableProjectSettings()

        assertTrue(settings.foldingEnabled)
        assertTrue(settings.matchDirectories)
        assertFalse(settings.foldNestedDirectories)
        assertTrue(settings.hideEmptyGroups)
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
            hideEmptyGroups = false
            caseSensitive = false
            rules.add(Rule("Docs", "*.md"))
            rules.add(Rule("Gradle", "gradle*", action = RuleAction.HIDE))
        }
        val target = FoldableProjectSettings().apply { loadState(source) }

        assertFalse(target.foldingEnabled)
        assertFalse(target.hideEmptyGroups)
        assertFalse(target.caseSensitive)
        assertEquals(listOf(Rule("Docs", "*.md"), Rule("Gradle", "gradle*", action = RuleAction.HIDE)), target.rules)
    }

    fun `test loadState migrates legacy hide all groups option to the hide action`() {
        val source = FoldableProjectSettings().apply {
            hideAllGroups = true
            rules.add(Rule("Docs", "*.md"))
            rules.add(Rule("Gradle", "gradle*"))
        }
        val target = FoldableProjectSettings().apply { loadState(source) }

        assertFalse(target.hideAllGroups)
        assertEquals(listOf(RuleAction.HIDE, RuleAction.HIDE), target.rules.map { it.action })
        assertEquals(listOf(RuleAction.GROUP, RuleAction.GROUP), source.rules.map { it.action })
    }

    fun `test defaults with legacy hide all groups option are migrated`() {
        val defaults = service<FoldableProjectDefaults>()
        val original = defaults.state

        try {
            defaults.loadState(FoldableProjectSettings().apply {
                hideAllGroups = true
                rules.add(Rule("Docs", "*.md"))
            })
            val settings = FoldableProjectSettings().apply { noStateLoaded() }

            assertFalse(settings.hideAllGroups)
            assertEquals(listOf(RuleAction.HIDE), settings.rules.map { it.action })
        } finally {
            defaults.loadState(original ?: FoldableProjectSettings())
        }
    }

    fun `test project service is available and shared`() {
        assertSame(project.service<FoldableProjectSettings>(), project.service<FoldableProjectSettings>())
    }

    fun `test update applies block and notifies listeners`() {
        val settings = project.service<FoldableProjectSettings>()
        val received = mutableListOf<FoldableProjectSettings>()
        val original = settings.hideEmptyGroups

        project.messageBus.connect(testRootDisposable)
            .subscribe(FoldableProjectSettingsListener.TOPIC, FoldableProjectSettingsListener { received.add(it) })

        try {
            settings.update(project) { hideEmptyGroups = !original }

            assertEquals(!original, settings.hideEmptyGroups)
            assertEquals(1, received.size)
            assertSame(settings, received.single())
        } finally {
            settings.hideEmptyGroups = original
        }
    }
}
