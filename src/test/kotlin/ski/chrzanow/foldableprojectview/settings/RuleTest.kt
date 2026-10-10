package ski.chrzanow.foldableprojectview.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.DEFAULT_RULE_NAME
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.DEFAULT_RULE_PATTERN
import java.awt.Color

class RuleTest {

    @Test
    fun `defaults`() {
        val rule = Rule()

        assertEquals(DEFAULT_RULE_NAME, rule.name)
        assertEquals(DEFAULT_RULE_PATTERN, rule.pattern)
        assertNull(rule.background)
        assertNull(rule.foreground)
        assertEquals(RuleAction.GROUP, rule.action)
    }

    @Test
    fun `equality`() {
        assertEquals(Rule("a", "*.kt", Color.RED), Rule("a", "*.kt", Color.RED))
        assertNotEquals(Rule("a", "*.kt"), Rule("a", "*.md"))
        assertNotEquals(Rule("a", "*.kt", Color.RED), Rule("a", "*.kt", Color.BLUE))
        assertNotEquals(Rule("a", "*.kt"), Rule("a", "*.kt", action = RuleAction.HIDE))
    }

    @Test
    fun `copy overrides only given fields`() {
        val copy = Rule("a", "*.kt", Color.RED, Color.BLUE).copy(name = "b")

        assertEquals(Rule("b", "*.kt", Color.RED, Color.BLUE), copy)
    }
}
