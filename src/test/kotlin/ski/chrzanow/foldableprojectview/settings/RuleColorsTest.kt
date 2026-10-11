package ski.chrzanow.foldableprojectview.settings

import com.intellij.ui.JBColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color

class RuleColorsTest {

    @Test
    fun `palettes offer the same named colors for backgrounds and foregrounds`() {
        val ids = RuleColors.backgrounds.map { it.id }

        assertTrue(ids.isNotEmpty())
        assertEquals(ids, ids.distinct())
        assertEquals(ids, RuleColors.foregrounds.map { it.id })
        assertTrue(RuleColors.backgrounds.all { it.displayName.isNotBlank() && it.displayName != it.id.lowercase() })
    }

    @Test
    fun `colors are looked up by id`() {
        assertSame(RuleColors.backgrounds.first(), RuleColors.background(RuleColors.backgrounds.first().id))
        assertSame(RuleColors.foregrounds.last(), RuleColors.foreground(RuleColors.foregrounds.last().id))
        assertNull(RuleColors.background("Purple"))
        assertNull(RuleColors.foreground(""))
    }

    @Test
    fun `background and foreground of the same name are different colors`() {
        val background = RuleColors.background("Blue")!!
        val foreground = RuleColors.foreground("Blue")!!

        assertEquals(RuleColors.background("Blue"), background)
        assertNotEquals(background, foreground)
        assertNotEquals(background.rgb, foreground.rgb)
        assertEquals("RuleColor(Blue)", background.toString())
    }

    @Test
    fun `palette colors are theme-aware while picked colors are custom`() {
        assertFalse(RuleColors.background("Green").isCustom)
        assertFalse((null as Color?).isCustom)
        assertTrue(Color.RED.isCustom)
        assertTrue(JBColor(Color.RED, Color.RED).isCustom)
    }
}
