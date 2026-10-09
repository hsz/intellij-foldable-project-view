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
    fun `default rule uses constants and no colors`() {
        val rule = Rule()

        assertEquals(DEFAULT_RULE_NAME, rule.name)
        assertEquals(DEFAULT_RULE_PATTERN, rule.pattern)
        assertNull(rule.background)
        assertNull(rule.foreground)
    }

    @Test
    fun `rules with same values are equal`() {
        assertEquals(Rule("a", "*.kt", Color.RED, Color.BLUE), Rule("a", "*.kt", Color.RED, Color.BLUE))
    }

    @Test
    fun `rules with different values are not equal`() {
        assertNotEquals(Rule("a", "*.kt"), Rule("a", "*.md"))
        assertNotEquals(Rule("a", "*.kt", Color.RED), Rule("a", "*.kt", Color.BLUE))
    }

    @Test
    fun `copy keeps colors and overrides given fields`() {
        val copy = Rule("a", "*.kt", Color.RED, Color.BLUE).copy(name = "b")

        assertEquals("b", copy.name)
        assertEquals("*.kt", copy.pattern)
        assertEquals(Color.RED, copy.background)
        assertEquals(Color.BLUE, copy.foreground)
    }
}
