package ski.chrzanow.foldableprojectview.psi.search

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import ski.chrzanow.foldableprojectview.settings.FoldableProjectSettings
import ski.chrzanow.foldableprojectview.settings.Rule

class FoldableProjectSearchScopeTest : BasePlatformTestCase() {

    private val settings = FoldableProjectSettings()

    private fun scope(pattern: String) = FoldableProjectSearchScope(project, settings, Rule("Test", pattern))

    private fun file(path: String) = myFixture.addFileToProject(path, "").virtualFile

    fun `test matches file by extension glob`() {
        val readme = file("README.md")
        val gradle = file("build.gradle.kts")

        assertTrue(scope("*.md").contains(readme))
        assertFalse(scope("*.md").contains(gradle))
    }

    fun `test matches multiple space-separated patterns`() {
        val readme = file("README.md")
        val gradle = file("build.gradle.kts")
        val license = file("LICENSE")

        val scope = scope("README.md  *.kts")

        assertTrue(scope.contains(readme))
        assertTrue(scope.contains(gradle))
        assertFalse(scope.contains(license))
    }

    fun `test pattern without slash matches nested files`() {
        val nested = file("docs/GUIDE.md")

        assertTrue(scope("*.md").contains(nested))
        assertTrue(scope("GUIDE.md").contains(nested))
    }

    fun `test anchored pattern matches only content root level`() {
        val root = file("README.md")
        val nested = file("docs/README.md")

        assertTrue(scope("/README.md").contains(root))
        assertFalse(scope("/README.md").contains(nested))
    }

    fun `test matches directories`() {
        val directory = file(".github/workflows/build.yml").parent.parent

        assertTrue(scope(".github").contains(directory))
        assertTrue(scope(".*").contains(directory))
    }

    fun `test case sensitivity`() {
        val readme = file("Readme.MD")

        settings.caseSensitive = true
        assertFalse(scope("readme.md").contains(readme))

        settings.caseSensitive = false
        assertTrue(scope("readme.md").contains(readme))
        assertTrue(scope("README.*").contains(readme))
    }

    fun `test blank pattern matches nothing`() {
        val readme = file("README.md")

        assertFalse(scope("").contains(readme))
        assertFalse(scope("   ").contains(readme))
    }

    fun `test display name is rule name`() {
        val scope = FoldableProjectSearchScope(project, settings, Rule("Docs", "*.md"))

        assertEquals("Docs", scope.displayName)
    }
}
