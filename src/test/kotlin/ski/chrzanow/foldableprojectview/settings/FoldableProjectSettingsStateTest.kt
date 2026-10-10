package ski.chrzanow.foldableprojectview.settings

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class FoldableProjectSettingsStateTest : BasePlatformTestCase() {

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

    fun `test defaults`() {
        val settings = FoldableProjectSettings()

        assertTrue(settings.foldingEnabled)
        assertTrue(settings.matchDirectories)
        assertFalse(settings.foldNestedDirectories)
        assertTrue(settings.hideEmptyGroups)
        assertTrue(settings.caseSensitive)
        assertTrue(settings.legacyFoldIgnoredFiles)
        assertTrue(settings.rules.isEmpty())
        assertNull(settings.ignoredFilesRule)
        assertFalse(settings.foldIgnoredFiles)
    }

    fun `test noStateLoaded adds the enabled ignored files rule`() = withDefaults(FoldableProjectSettings()) {
        val settings = FoldableProjectSettings().apply { noStateLoaded() }

        val rule = settings.ignoredFilesRule!!
        assertTrue(rule.enabled)
        assertEquals(RuleType.IGNORED_FILES, rule.type)
        assertEquals("Ignored files", rule.name)
        assertTrue(settings.foldIgnoredFiles)
        assertEquals(listOf(rule), settings.rules)
    }

    fun `test loadState appends the ignored files rule after the existing rules`() {
        val source = FoldableProjectSettings().apply {
            rules.add(Rule("Docs", "*.md"))
        }
        val target = FoldableProjectSettings().apply { loadState(source) }

        assertEquals(listOf("Docs", "Ignored files"), target.rules.map { it.name })
        assertTrue(target.foldIgnoredFiles)
    }

    fun `test loadState migrates legacy fold ignored files option to the disabled ignored files rule`() {
        val source = FoldableProjectSettings().apply {
            legacyFoldIgnoredFiles = false
        }
        val target = FoldableProjectSettings().apply { loadState(source) }

        assertFalse(target.foldIgnoredFiles)
        assertFalse(target.ignoredFilesRule!!.enabled)
        assertTrue("legacy option must be reset to its default", target.legacyFoldIgnoredFiles)
    }

    fun `test loadState keeps the existing ignored files rule`() {
        val source = FoldableProjectSettings().apply {
            legacyFoldIgnoredFiles = false
            rules.add(Rule.ignoredFiles().copy(name = "Noise", action = RuleAction.HIDE))
            rules.add(Rule("Docs", "*.md"))
        }
        val target = FoldableProjectSettings().apply { loadState(source) }

        assertEquals(listOf("Noise", "Docs"), target.rules.map { it.name })
        assertEquals(Rule.ignoredFiles().copy(name = "Noise", action = RuleAction.HIDE), target.ignoredFilesRule)
        assertTrue(target.foldIgnoredFiles)
    }

    fun `test fold ignored files toggles the ignored files rule`() {
        val settings = FoldableProjectSettings().apply { rules.add(Rule.ignoredFiles()) }
        val modificationCount = settings.modificationCount

        settings.foldIgnoredFiles = false

        assertFalse(settings.ignoredFilesRule!!.enabled)
        assertTrue("toggling must be noticed by the modification tracking", settings.modificationCount > modificationCount)
        assertEquals(1, settings.rules.size)

        settings.foldIgnoredFiles = true

        assertTrue(settings.ignoredFilesRule!!.enabled)
        assertEquals(1, settings.rules.size)
    }

    fun `test fold ignored files adds the missing ignored files rule`() {
        val settings = FoldableProjectSettings()

        settings.foldIgnoredFiles = false

        assertEquals(listOf(Rule.ignoredFiles(enabled = false)), settings.rules)
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
        assertEquals(
            listOf(Rule("Docs", "*.md"), Rule("Gradle", "gradle*", action = RuleAction.HIDE), Rule.ignoredFiles()),
            target.rules,
        )
    }

    fun `test loadState migrates legacy hide all groups option to the hide action`() {
        val source = FoldableProjectSettings().apply {
            hideAllGroups = true
            rules.add(Rule("Docs", "*.md"))
            rules.add(Rule("Gradle", "gradle*"))
        }
        val target = FoldableProjectSettings().apply { loadState(source) }

        assertFalse(target.hideAllGroups)
        // `Hide all groups` used to hide the ignored files group as well.
        assertEquals(listOf(RuleAction.HIDE, RuleAction.HIDE, RuleAction.HIDE), target.rules.map { it.action })
        assertEquals(RuleAction.HIDE, target.ignoredFilesRule!!.action)
        assertEquals(listOf(RuleAction.GROUP, RuleAction.GROUP), source.rules.map { it.action })
    }

    fun `test defaults with legacy hide all groups option are migrated`() {
        val defaults = FoldableProjectSettings().apply {
            hideAllGroups = true
            rules.add(Rule("Docs", "*.md"))
        }

        withDefaults(defaults) {
            val settings = FoldableProjectSettings().apply { noStateLoaded() }

            assertFalse(settings.hideAllGroups)
            assertEquals(listOf("Docs", "Ignored files"), settings.rules.map { it.name })
            assertEquals(listOf(RuleAction.HIDE, RuleAction.HIDE), settings.rules.map { it.action })
        }
    }

    fun `test defaults with legacy fold ignored files option are migrated`() {
        val defaults = FoldableProjectSettings().apply {
            legacyFoldIgnoredFiles = false
        }

        withDefaults(defaults) {
            val settings = FoldableProjectSettings().apply { noStateLoaded() }

            assertFalse(settings.foldIgnoredFiles)
            assertEquals(listOf(Rule.ignoredFiles(enabled = false)), settings.rules)
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
