package ski.chrzanow.foldableprojectview.psi.search

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import ski.chrzanow.foldableprojectview.settings.FoldableProjectSettings
import ski.chrzanow.foldableprojectview.settings.Rule
import ski.chrzanow.foldableprojectview.settings.RuleType

class FoldableProjectScopesProviderTest : BasePlatformTestCase() {

    private val provider = FoldableProjectScopesProvider()
    private lateinit var settings: FoldableProjectSettings
    private lateinit var originalRules: List<Rule>

    override fun setUp() {
        super.setUp()
        settings = project.service()
        originalRules = settings.rules.toList()
        settings.rules.clear()
    }

    override fun tearDown() {
        try {
            settings.rules.clear()
            settings.rules.addAll(originalRules)
        } finally {
            super.tearDown()
        }
    }

    fun `test display name is not blank`() {
        assertFalse(provider.displayName.isBlank())
    }

    fun `test no rules produce no scopes`() {
        assertTrue(provider.getSearchScopes(project, DataContext.EMPTY_CONTEXT).isEmpty())
    }

    fun `test scope is created for every non blank rule`() {
        settings.rules.add(Rule("Docs", "*.md"))
        settings.rules.add(Rule("Blank", "   "))
        settings.rules.add(Rule("Gradle", "gradle*"))

        val scopes = provider.getSearchScopes(project, DataContext.EMPTY_CONTEXT)

        assertEquals(listOf("Docs", "Gradle"), scopes.map { it.displayName })
        assertTrue(scopes.all { it is FoldableProjectSearchScope })
    }

    fun `test patterns file rule produces a scope reading the patterns from the file`() {
        myFixture.addFileToProject(".foldignore", "*.md\n!CHANGELOG.md\n")
        val readme = myFixture.addFileToProject("README.md", "").virtualFile
        val changelog = myFixture.addFileToProject("CHANGELOG.md", "").virtualFile
        settings.rules.add(Rule("Docs", ".foldignore", type = RuleType.PATTERNS_FILE))

        val scope = provider.getSearchScopes(project, DataContext.EMPTY_CONTEXT).single() as FoldableProjectSearchScope

        assertEquals("Docs", scope.displayName)
        assertTrue(scope.contains(readme))
        assertFalse(scope.contains(changelog))
    }

    fun `test disabled rules and the ignored files rule produce no scopes`() {
        settings.rules.add(Rule.ignoredFiles())
        settings.rules.add(Rule("Docs", "*.md", enabled = false))
        settings.rules.add(Rule("Gradle", "gradle*"))

        val scopes = provider.getSearchScopes(project, DataContext.EMPTY_CONTEXT)

        assertEquals(listOf("Gradle"), scopes.map { it.displayName })
    }
}
