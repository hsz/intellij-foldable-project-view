package ski.chrzanow.foldableprojectview.psi.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobPatternTest {

    private fun String.matches(path: String, caseSensitive: Boolean = true) =
        GlobPattern.compile(this, caseSensitive)!!.matches(path)

    @Test
    fun `blank pattern is not compiled`() {
        assertNull(GlobPattern.compile(""))
        assertNull(GlobPattern.compile("   "))
        assertNull(GlobPattern.compile("/"))
    }

    @Test
    fun `star matches within a single segment`() {
        assertTrue("*.md".matches("README.md"))
        assertTrue("gradle*".matches("gradlew"))
        assertTrue("gradle*".matches("gradle"))
        assertFalse("*.md".matches("README.txt"))
        assertFalse("src/*.kt".matches("src/main/Main.kt"))
    }

    @Test
    fun `pattern without slash matches on any level`() {
        assertTrue("*.md".matches("docs/GUIDE.md"))
        assertTrue("README.md".matches("a/b/README.md"))
        assertTrue("build/".matches("module/build"))
    }

    @Test
    fun `pattern with slash is anchored to the root`() {
        assertTrue("/README.md".matches("README.md"))
        assertFalse("/README.md".matches("docs/README.md"))
        assertTrue("src/main".matches("src/main"))
        assertFalse("src/main".matches("module/src/main"))
    }

    @Test
    fun `double star matches multiple segments`() {
        assertTrue("**/*.md".matches("README.md"))
        assertTrue("**/*.md".matches("a/b/README.md"))
        assertTrue("docs/**".matches("docs/a/b.md"))
        assertTrue("a/**/b".matches("a/b"))
        assertTrue("a/**/b".matches("a/x/y/b"))
        assertFalse("a/**/b".matches("a/x/c"))
    }

    @Test
    fun `question mark matches a single character`() {
        assertTrue("?.md".matches("a.md"))
        assertFalse("?.md".matches("ab.md"))
        assertFalse("?.md".matches("/.md"))
    }

    @Test
    fun `character classes`() {
        assertTrue("[ab].md".matches("a.md"))
        assertTrue("[a-z].md".matches("q.md"))
        assertFalse("[!a].md".matches("a.md"))
        assertTrue("[!a].md".matches("b.md"))
    }

    @Test
    fun `regex special characters are escaped`() {
        assertTrue("a.b".matches("a.b"))
        assertFalse("a.b".matches("axb"))
        assertTrue("a+b(c)".matches("a+b(c)"))
        assertTrue("{x}".matches("{x}"))
        assertTrue("\\*.md".matches("*.md"))
        assertFalse("\\*.md".matches("a.md"))
    }

    @Test
    fun `trailing slash is optional`() {
        assertTrue("docs/".matches("docs"))
        assertTrue("docs".matches("docs/"))
    }

    @Test
    fun `case sensitivity`() {
        assertFalse("readme.md".matches("README.md"))
        assertTrue("readme.md".matches("README.md", caseSensitive = false))
        assertTrue("README.*".matches("Readme.MD", caseSensitive = false))
    }
}
