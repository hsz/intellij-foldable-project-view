package ski.chrzanow.foldableprojectview.projectView

import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.components.service
import com.intellij.openapi.vcs.FileStatus
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import ski.chrzanow.foldableprojectview.settings.FoldableProjectSettings
import ski.chrzanow.foldableprojectview.settings.Rule

class FoldableTreeStructureProviderTest : BasePlatformTestCase() {

    private lateinit var settings: FoldableProjectSettings
    private lateinit var provider: FoldableTreeStructureProvider

    override fun setUp() {
        super.setUp()
        settings = project.service<FoldableProjectSettings>().apply {
            copyFrom(FoldableProjectSettings())
            foldIgnoredFiles = false
        }
        provider = FoldableTreeStructureProvider(project)

        myFixture.addFileToProject("README.md", "")
        myFixture.addFileToProject("CHANGELOG.md", "")
        myFixture.addFileToProject("build.gradle.kts", "")
        myFixture.addFileToProject("src/Main.kt", "")
        myFixture.addFileToProject("docs/GUIDE.md", "")
    }

    override fun tearDown() {
        try {
            settings.copyFrom(FoldableProjectSettings())
        } finally {
            super.tearDown()
        }
    }

    private val rootNode
        get() = PsiManager.getInstance(project)
            .findDirectory(myFixture.tempDirFixture.getFile("")!!)!!
            .let { PsiDirectoryNode(project, it, ViewSettings.DEFAULT) }

    private fun modify(parent: PsiDirectoryNode = rootNode, children: Collection<AbstractTreeNode<*>> = parent.children) =
        provider.modify(parent, children.toMutableList(), ViewSettings.DEFAULT)

    private val AbstractTreeNode<*>.nodeName
        get() = (this as? ProjectViewNode<*>)?.virtualFile?.name ?: name.orEmpty()

    private fun Collection<AbstractTreeNode<*>>.names() = map { it.nodeName }.sorted()

    private fun Collection<AbstractTreeNode<*>>.groups() = filterIsInstance<FoldableProjectViewNode>()

    fun `test root children are not modified without rules`() {
        val children = rootNode.children
        val result = modify(children = children)

        assertSameElements(result, children)
    }

    fun `test matched files are moved into the rule group`() {
        settings.rules.add(Rule("Docs", "*.md"))

        val result = modify()
        val group = result.groups().single()

        assertEquals(listOf("Docs", "build.gradle.kts", "docs", "src"), result.names())
        assertEquals(listOf("CHANGELOG.md", "README.md"), group.children.names())
    }

    fun `test first rule takes precedence over following rules`() {
        settings.rules.add(Rule("Readme", "README.md"))
        settings.rules.add(Rule("Docs", "*.md"))

        val groups = modify().groups()

        assertEquals(listOf("Readme", "Docs"), groups.map { it.name })
        assertEquals(listOf("README.md"), groups[0].children.names())
        assertEquals(listOf("CHANGELOG.md"), groups[1].children.names())
    }

    fun `test directories are folded only when match directories is enabled`() {
        settings.rules.add(Rule("Sources", "src docs"))

        settings.matchDirectories = true
        assertEquals(listOf("docs", "src"), modify().groups().single().children.names())

        settings.matchDirectories = false
        assertEmpty(modify().groups())
    }

    fun `test empty groups are hidden when configured`() {
        settings.rules.add(Rule("Nothing", "*.nothing"))

        settings.hideEmptyGroups = true
        assertEmpty(modify().groups())

        settings.hideEmptyGroups = false
        assertEquals(listOf("Nothing"), modify().groups().map { it.name })
    }

    fun `test hide all groups removes matched elements and groups`() {
        settings.rules.add(Rule("Docs", "*.md"))
        settings.hideAllGroups = true

        val result = modify()

        assertEmpty(result.groups())
        assertEquals(listOf("build.gradle.kts", "docs", "src"), result.names())
    }

    fun `test folding disabled returns children untouched`() {
        settings.rules.add(Rule("Docs", "*.md"))
        settings.foldingEnabled = false

        val children = rootNode.children
        assertSameElements(modify(children = children), children)
    }

    fun `test rules with blank pattern are skipped`() {
        settings.rules.add(Rule("Blank", "  "))
        settings.hideEmptyGroups = false

        assertEmpty(modify().groups())
    }

    fun `test nested directories are not folded`() {
        settings.rules.add(Rule("Docs", "*.md"))

        val docs = rootNode.children.filterIsInstance<PsiDirectoryNode>().single { it.nodeName == "docs" }
        val result = modify(parent = docs)

        assertEmpty(result.groups())
        assertEquals(listOf("GUIDE.md"), result.names())
    }

    fun `test ignored files are folded into a dedicated group`() {
        settings.foldIgnoredFiles = true

        val children = rootNode.children.map { node ->
            when (node is PsiFileNode && node.nodeName == "build.gradle.kts") {
                true -> object : PsiFileNode(project, node.value, ViewSettings.DEFAULT) {
                    override fun getFileStatus() = FileStatus.IGNORED
                }

                false -> node
            }
        }

        val result = modify(children = children)
        val group = result.groups().single()

        assertEquals("Ignored files", group.name)
        assertEquals(listOf("build.gradle.kts"), group.children.names())
        assertEquals(listOf("CHANGELOG.md", "Ignored files", "README.md", "docs", "src"), result.names())
    }

    fun `test group contains matched files`() {
        settings.rules.add(Rule("Docs", "*.md"))

        val group = modify().groups().single()
        val readme = myFixture.tempDirFixture.getFile("README.md")!!
        val gradle = myFixture.tempDirFixture.getFile("build.gradle.kts")!!

        assertTrue(group.contains(readme))
        assertFalse(group.contains(gradle))
    }
}
