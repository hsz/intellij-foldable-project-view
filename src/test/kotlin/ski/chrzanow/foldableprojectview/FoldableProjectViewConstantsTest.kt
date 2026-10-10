package ski.chrzanow.foldableprojectview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoldableProjectViewConstantsTest {

    @Test
    fun `constants have expected values`() {
        assertTrue(FoldableProjectViewConstants.STORAGE_FILE.endsWith(".xml"))
        assertEquals("Aa", FoldableProjectViewConstants.COLOR_COLUMN_TEXT)
        assertTrue(FoldableProjectViewConstants.MATCH_COUNT_LIMIT > 0)
    }
}
