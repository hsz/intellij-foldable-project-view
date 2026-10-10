package ski.chrzanow.foldableprojectview.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color

class RuleTest {

    @Test
    fun defaults() {
        val rule = Rule()

        assertEquals("", rule.name)
        assertEquals("", rule.pattern)
        assertNull(rule.background)
        assertNull(rule.foreground)
        assertEquals(RuleAction.GROUP, rule.action)
        assertEquals(RuleType.PATTERN, rule.type)
        assertTrue(rule.enabled)
        assertFalse(rule.isActive)
    }

    @Test
    fun `display name falls back to a placeholder`() {
        assertEquals("Docs", Rule("Docs", "*.md").displayName)
        assertEquals("Unnamed rule", Rule().displayName)
        assertEquals("Unnamed rule", Rule("  ", "*.md").displayName)
    }

    @Test
    fun `ignored files rule`() {
        val rule = Rule.ignoredFiles()

        assertEquals("Ignored files", rule.name)
        assertEquals("", rule.pattern)
        assertEquals(RuleType.IGNORED_FILES, rule.type)
        assertEquals(RuleAction.GROUP, rule.action)
        assertTrue(rule.enabled)
        assertFalse(Rule.ignoredFiles(enabled = false).enabled)
    }

    @Test
    fun `is active`() {
        assertTrue(Rule("a", "*.kt").isActive)
        assertFalse(Rule("a", "*.kt", enabled = false).isActive)
        assertFalse(Rule("a", "  ").isActive)
        assertTrue(Rule.ignoredFiles().isActive)
        assertFalse(Rule.ignoredFiles(enabled = false).isActive)
        assertTrue(Rule("a", ".foldignore", type = RuleType.PATTERNS_FILE).isActive)
        assertFalse(Rule("a", " ", type = RuleType.PATTERNS_FILE).isActive)
    }

    @Test
    fun `uses patterns`() {
        assertTrue(Rule("a", "*.kt").usesPatterns)
        assertTrue(Rule("a", ".foldignore", type = RuleType.PATTERNS_FILE).usesPatterns)
        assertFalse(Rule.ignoredFiles().usesPatterns)
    }

    @Test
    fun `patterns file paths`() {
        assertEquals(listOf(".foldignore", "docker/.dockerignore"), Rule("a", " .foldignore /docker/.dockerignore/ ", type = RuleType.PATTERNS_FILE).patternsFilePaths)
        assertEquals(listOf("My Notes.ignore"), Rule("a", "\"My Notes.ignore\"", type = RuleType.PATTERNS_FILE).patternsFilePaths)
        assertEquals(emptyList<String>(), Rule("a", "/ ", type = RuleType.PATTERNS_FILE).patternsFilePaths)
        assertEquals(emptyList<String>(), Rule("a", ".foldignore").patternsFilePaths)
        assertEquals(emptyList<String>(), Rule.ignoredFiles().patternsFilePaths)
    }

    @Test
    fun equality() {
        assertEquals(Rule("a", "*.kt", Color.RED), Rule("a", "*.kt", Color.RED))
        assertNotEquals(Rule("a", "*.kt"), Rule("a", "*.md"))
        assertNotEquals(Rule("a", "*.kt", Color.RED), Rule("a", "*.kt", Color.BLUE))
        assertNotEquals(Rule("a", "*.kt"), Rule("a", "*.kt", action = RuleAction.HIDE))
        assertNotEquals(Rule("a", "*.kt"), Rule("a", "*.kt", enabled = false))
        assertNotEquals(Rule("a", ""), Rule("a", "", type = RuleType.IGNORED_FILES))
        assertNotEquals(Rule("a", ".foldignore"), Rule("a", ".foldignore", type = RuleType.PATTERNS_FILE))
    }

    @Test
    fun `copy overrides only given fields`() {
        val copy = Rule("a", "*.kt", Color.RED, Color.BLUE).copy(name = "b")

        assertEquals(Rule("b", "*.kt", Color.RED, Color.BLUE), copy)
    }
}
