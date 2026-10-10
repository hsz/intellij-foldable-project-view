package ski.chrzanow.foldableprojectview.psi.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobPatternTest {

    private fun String.matches(path: String, caseSensitive: Boolean = true, isDirectory: Boolean = false) =
        GlobPattern.compile(this, caseSensitive)!!.matches(path, isDirectory)

    @Test
    fun `blank pattern is not compiled`() {
        assertNull(GlobPattern.compile(""))
        assertNull(GlobPattern.compile("   "))
        assertNull(GlobPattern.compile("/"))
        assertNull(GlobPattern.compile("!"))
        assertNull(GlobPattern.compile("!/"))
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
        assertTrue("build/".matches("module/build", isDirectory = true))
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
    fun `trailing slash in the path is optional`() {
        assertTrue("docs".matches("docs/"))
        assertTrue("docs".matches("docs/", isDirectory = true))
    }

    @Test
    fun `trailing slash matches directories only`() {
        val pattern = GlobPattern.compile("build/")!!

        assertTrue(pattern.directoryOnly)
        assertTrue(pattern.matches("build", isDirectory = true))
        assertFalse(pattern.matches("build", isDirectory = false))
        assertTrue("/build/".matches("build", isDirectory = true))
        assertFalse("/build/".matches("module/build", isDirectory = true))
    }

    @Test
    fun `pattern without trailing slash matches files and directories`() {
        val pattern = GlobPattern.compile("build")!!

        assertFalse(pattern.directoryOnly)
        assertTrue(pattern.matches("build", isDirectory = true))
        assertTrue(pattern.matches("build", isDirectory = false))
    }

    @Test
    fun `escaped trailing slash is matched literally`() {
        val pattern = GlobPattern.compile("docs\\/")!!

        assertFalse(pattern.directoryOnly)
        assertTrue(pattern.matches("docs/"))
    }

    @Test
    fun `leading exclamation mark negates the pattern`() {
        val pattern = GlobPattern.compile("!docker-compose.yml")!!

        assertTrue(pattern.negated)
        assertTrue(pattern.matches("docker-compose.yml"))
        assertEquals("!docker-compose.yml", pattern.source)
        assertTrue(GlobPattern.compile("!/README.md")!!.let { it.negated && it.matches("README.md") && !it.matches("docs/README.md") })
        assertTrue(GlobPattern.compile("!build/")!!.let { it.negated && it.directoryOnly })
    }

    @Test
    fun `escaped exclamation mark is matched literally`() {
        val pattern = GlobPattern.compile("\\!important.md")!!

        assertFalse(pattern.negated)
        assertTrue(pattern.matches("!important.md"))
    }

    @Test
    fun `escaped space is matched literally`() {
        assertTrue("My\\ Notes.md".matches("My Notes.md"))
        assertTrue("trailing\\ ".matches("trailing "))
        assertFalse("trailing\\ ".matches("trailing"))
    }

    @Test
    fun `isValid reports whether the pattern compiles`() {
        assertTrue(GlobPattern.isValid("*.md"))
        assertFalse(GlobPattern.isValid("[z-a].md"))
        assertFalse(GlobPattern.isValid(""))
    }

    @Test
    fun `pattern is trimmed before compiling`() {
        assertTrue("  *.md  ".matches("README.md"))
    }

    @Test
    fun `unclosed or empty bracket is treated literally`() {
        assertTrue("a[b".matches("a[b"))
        assertFalse("a[b".matches("ab"))
        assertTrue("a[]b".matches("a[]b"))
    }

    @Test
    fun `invalid character class range is not compiled`() {
        assertNull(GlobPattern.compile("[z-a].md"))
    }

    @Test
    fun `double star alone matches everything`() {
        assertTrue("**".matches("a"))
        assertTrue("**".matches("a/b/c.txt"))
    }

    @Test
    fun `double star inside a segment spans segments`() {
        assertTrue("a**b".matches("a/x/b"))
        assertTrue("a**b".matches("ab"))
    }

    @Test
    fun `trailing backslash is matched literally`() {
        assertTrue("a\\".matches("a\\"))
    }

    @Test
    fun `star does not cross segment boundary`() {
        assertFalse("src/*/Main.kt".matches("src/a/b/Main.kt"))
        assertTrue("src/*/Main.kt".matches("src/a/Main.kt"))
    }

    @Test
    fun `case sensitivity`() {
        assertFalse("readme.md".matches("README.md"))
        assertTrue("readme.md".matches("README.md", caseSensitive = false))
        assertTrue("README.*".matches("Readme.MD", caseSensitive = false))
    }
}
