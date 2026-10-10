package ski.chrzanow.foldableprojectview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ski.chrzanow.foldableprojectview.psi.search.GlobPattern

class FoldableProjectViewConstantsTest {

    @Test
    fun `default rule pattern is a valid glob`() {
        val pattern = GlobPattern.compile(FoldableProjectViewConstants.DEFAULT_RULE_PATTERN)

        assertNotNull(pattern)
        assertTrue(pattern!!.matches("README.md"))
    }

    @Test
    fun `constants have expected values`() {
        assertTrue(FoldableProjectViewConstants.STORAGE_FILE.endsWith(".xml"))
        assertTrue(FoldableProjectViewConstants.DEFAULT_RULE_NAME.isNotBlank())
        assertEquals("Aa", FoldableProjectViewConstants.COLOR_COLUMN_TEXT)
    }
}
