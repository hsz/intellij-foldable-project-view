package ski.chrzanow.foldableprojectview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FoldableProjectViewBundleTest {

    @Test
    fun `message resolves known key`() {
        val message = FoldableProjectViewBundle.message("foldableProjectView.name")

        assertFalse(message.isBlank())
        assertFalse(message.startsWith("!"))
    }

    @Test
    fun `message pointer resolves to the same text as message`() {
        assertEquals(
            FoldableProjectViewBundle.message("foldableProjectView.name"),
            FoldableProjectViewBundle.messagePointer("foldableProjectView.name").get(),
        )
    }
}
