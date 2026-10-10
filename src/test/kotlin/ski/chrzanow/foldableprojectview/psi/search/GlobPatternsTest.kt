package ski.chrzanow.foldableprojectview.psi.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobPatternsTest {

    private fun String.matches(path: String, isDirectory: Boolean = false, caseSensitive: Boolean = true) =
        GlobPatterns.parse(this, caseSensitive).matches(path, isDirectory)

    @Test
    fun `patterns are separated with whitespace and commas`() {
        assertEquals(listOf("README.md", "*.yml", "gradle*", ".github"), GlobPatterns.split("README.md  *.yml,gradle*\n\t.github"))
        assertEquals(GlobPatterns.split(""), emptyList<String>())
        assertEquals(GlobPatterns.split("  , "), emptyList<String>())
    }

    @Test
    fun `quoted pattern may contain spaces`() {
        assertEquals(listOf("My Notes.md", "*.md"), GlobPatterns.split("\"My Notes.md\" *.md"))
        assertEquals(listOf("docs/My Notes.md"), GlobPatterns.split("docs/\"My Notes\".md"))
        assertEquals(listOf("a,b"), GlobPatterns.split("\"a,b\""))
        assertEquals(GlobPatterns.split("\"\""), emptyList<String>())
    }

    @Test
    fun `unterminated quote spans to the end of the text`() {
        assertEquals(listOf("My Notes.md *.md"), GlobPatterns.split("\"My Notes.md *.md"))
    }

    @Test
    fun `escaped characters are preserved`() {
        assertEquals(listOf("My\\ Notes.md", "*.md"), GlobPatterns.split("My\\ Notes.md *.md"))
        assertEquals(listOf("\\\"quoted\\\".md"), GlobPatterns.split("\\\"quoted\\\".md"))
        assertEquals(listOf("trailing\\"), GlobPatterns.split("trailing\\"))
    }

    @Test
    fun `any pattern matches`() {
        assertTrue("README.md *.yml".matches("README.md"))
        assertTrue("README.md *.yml".matches("ci.yml"))
        assertFalse("README.md *.yml".matches("LICENSE"))
    }

    @Test
    fun `empty patterns match nothing`() {
        val patterns = GlobPatterns.parse("")

        assertTrue(patterns.isEmpty)
        assertFalse(patterns.matches("README.md"))
        assertTrue(GlobPatterns.parse("[z-a]").isEmpty)
    }

    @Test
    fun `negated pattern excludes the matched paths`() {
        assertTrue("*.yml !docker-compose.yml".matches("ci.yml"))
        assertFalse("*.yml !docker-compose.yml".matches("docker-compose.yml"))
        assertFalse("!docker-compose.yml".matches("docker-compose.yml"))
        assertFalse("!docker-compose.yml".matches("ci.yml"))
    }

    @Test
    fun `last matching pattern wins`() {
        assertTrue("*.yml !docker-compose.yml docker-*.yml".matches("docker-compose.yml"))
        assertTrue("!docker-compose.yml *.yml".matches("docker-compose.yml"))
    }

    @Test
    fun `negated pattern applies to directories only when it ends with a slash`() {
        assertFalse("build* !build/".matches("build", isDirectory = true))
        assertTrue("build* !build/".matches("build", isDirectory = false))
        assertTrue("build* !build/".matches("build.log"))
    }

    @Test
    fun `directory only pattern does not match files`() {
        assertTrue("build/".matches("build", isDirectory = true))
        assertFalse("build/".matches("build"))
        assertTrue("build/ *.log".matches("build.log"))
    }

    @Test
    fun `quoted and escaped names with spaces are matched`() {
        assertTrue("\"My Notes.md\"".matches("My Notes.md"))
        assertTrue("My\\ Notes.md".matches("My Notes.md"))
        assertTrue("\"My Notes.md\"".matches("docs/My Notes.md"))
        assertFalse("\"My Notes.md\"".matches("Notes.md"))
    }

    @Test
    fun `case sensitivity applies to all patterns`() {
        assertFalse("readme.md *.YML".matches("README.md"))
        assertTrue("readme.md *.YML".matches("README.md", caseSensitive = false))
        assertTrue("readme.md *.YML".matches("ci.yml", caseSensitive = false))
    }

    @Test
    fun `lines of an ignore-like file are single patterns`() {
        val text = """
            # Documentation
            *.md

              !CHANGELOG.md
            \#hash.md
            My Notes.md
            build/
        """.trimIndent()

        assertEquals(listOf("*.md", "!CHANGELOG.md", "\\#hash.md", "My Notes.md", "build/"), GlobPatterns.splitLines(text))
        assertEquals(GlobPatterns.splitLines(""), emptyList<String>())
        assertEquals(GlobPatterns.splitLines("# comment only\n\n"), emptyList<String>())

        val patterns = GlobPatterns.parseLines(text)
        assertTrue(patterns.matches("README.md"))
        assertFalse(patterns.matches("CHANGELOG.md"))
        assertTrue(patterns.matches("#hash.md"))
        assertTrue(patterns.matches("My Notes.md"))
        assertTrue(patterns.matches("build", isDirectory = true))
        assertFalse(patterns.matches("build"))
    }

    @Test
    fun `lines of an ignore-like file are not split on spaces and commas`() {
        val patterns = GlobPatterns.parseLines("a, b.md\n")

        assertEquals(listOf("a, b.md"), GlobPatterns.splitLines("a, b.md\n"))
        assertTrue(patterns.matches("a, b.md"))
        assertFalse(patterns.matches("b.md"))
    }

    @Test
    fun `lines of an ignore-like file honor case sensitivity`() {
        assertFalse(GlobPatterns.parseLines("readme.md").matches("README.md"))
        assertTrue(GlobPatterns.parseLines("readme.md", caseSensitive = false).matches("README.md"))
    }

    @Test
    fun `invalid patterns are reported and skipped`() {
        assertEquals(listOf("[z-a].md", "!"), GlobPatterns.invalidPatterns("*.md [z-a].md ! LICENSE"))
        assertEquals(GlobPatterns.invalidPatterns("*.md LICENSE"), emptyList<String>())
        assertEquals(GlobPatterns.invalidPatterns(""), emptyList<String>())

        assertTrue("*.md [z-a].md".matches("README.md"))
    }
}
