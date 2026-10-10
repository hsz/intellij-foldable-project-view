package ski.chrzanow.foldableprojectview.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.DEFAULT_RULE_NAME
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.DEFAULT_RULE_PATTERN
import java.awt.Color

class RuleTest {

    @Test
    fun defaults() {
        val rule = Rule()

        assertEquals(DEFAULT_RULE_NAME, rule.name)
        assertEquals(DEFAULT_RULE_PATTERN, rule.pattern)
        assertNull(rule.background)
        assertNull(rule.foreground)
        assertEquals(RuleAction.GROUP, rule.action)
        assertEquals(RuleType.PATTERN, rule.type)
        assertTrue(rule.enabled)
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
    }

    @Test
    fun equality() {
        assertEquals(Rule("a", "*.kt", Color.RED), Rule("a", "*.kt", Color.RED))
        assertNotEquals(Rule("a", "*.kt"), Rule("a", "*.md"))
        assertNotEquals(Rule("a", "*.kt", Color.RED), Rule("a", "*.kt", Color.BLUE))
        assertNotEquals(Rule("a", "*.kt"), Rule("a", "*.kt", action = RuleAction.HIDE))
        assertNotEquals(Rule("a", "*.kt"), Rule("a", "*.kt", enabled = false))
        assertNotEquals(Rule("a", ""), Rule("a", "", type = RuleType.IGNORED_FILES))
    }

    @Test
    fun `copy overrides only given fields`() {
        val copy = Rule("a", "*.kt", Color.RED, Color.BLUE).copy(name = "b")

        assertEquals(Rule("b", "*.kt", Color.RED, Color.BLUE), copy)
    }
}
