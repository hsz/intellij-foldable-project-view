package ski.chrzanow.foldableprojectview.projectView

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.RULE_SET_FILE
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.USER_RULES_FILE
import ski.chrzanow.foldableprojectview.rules.isolateUserDirectory
import ski.chrzanow.foldableprojectview.settings.FoldableProjectSettings
import ski.chrzanow.foldableprojectview.settings.Rule
import ski.chrzanow.foldableprojectview.settings.RuleAction
import ski.chrzanow.foldableprojectview.settings.RuleType
import ski.chrzanow.foldableprojectview.settings.RuleOverride
import ski.chrzanow.foldableprojectview.settings.foldIgnoredFiles
import java.awt.Color
import java.nio.file.Path
import kotlin.io.path.writeText

class FoldableTreeStructureProviderTest : BasePlatformTestCase() {

    private lateinit var settings: FoldableProjectSettings
    private lateinit var provider: FoldableTreeStructureProvider
    private lateinit var userDirectory: Path
    private lateinit var restoreUserDirectory: () -> Unit

    override fun setUp() {
        super.setUp()
        // The rule set files of the developer must not leak into the tests.
        isolateUserDirectory().let { (directory, restore) ->
            userDirectory = directory
            restoreUserDirectory = restore
        }
        // A fresh state has no rules at all, including the built-in ignored files rule.
        settings = project.service<FoldableProjectSettings>().apply {
            copyFrom(FoldableProjectSettings())
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
            restoreUserDirectory()
        } finally {
            super.tearDown()
        }
    }

    private val rootNode
        get() = PsiManager.getInstance(project)
            .findDirectory(myFixture.tempDirFixture.getFile("")!!)!!
            .let { PsiDirectoryNode(project, it, ViewSettings.DEFAULT) }

    private val docsNode
        get() = rootNode.children.filterIsInstance<PsiDirectoryNode>().single { it.nodeName == "docs" }

    private fun modify(parent: ProjectViewNode<*> = rootNode, children: Collection<AbstractTreeNode<*>> = parent.children) =
        provider.modify(parent, children.toMutableList(), ViewSettings.DEFAULT)

    /**
     * Mimics the nodes of the Scope view (`ScopeViewTreeModel`), used by the "Project Files" and custom scopes panes:
     * directories are represented by the internal nodes that are not [PsiDirectoryNode]s, the content root node
     * is attached to the project node with no virtual file, and only the [PsiFileNode] children are passed to the providers.
     */
    private class ScopeViewNode(
        project: Project,
        parent: ProjectViewNode<*>?,
        private val file: VirtualFile?,
    ) : ProjectViewNode<Any>(project, file ?: Any(), ViewSettings.DEFAULT) {

        init {
            setParent(parent)
        }

        override fun getVirtualFile() = file

        override fun contains(file: VirtualFile) = this.file?.let { VfsUtilCore.isAncestor(it, file, false) } == true

        override fun getChildren(): Collection<AbstractTreeNode<*>> = file?.children.orEmpty()
            .filterNot(VirtualFile::isDirectory)
            .mapNotNull { PsiManager.getInstance(project).findFile(it) }
            .map { PsiFileNode(project, it, ViewSettings.DEFAULT) }

        override fun update(presentation: PresentationData) = Unit
    }

    private val scopeViewProjectNode
        get() = ScopeViewNode(project, null, null)

    private val scopeViewRootNode
        get() = ScopeViewNode(project, scopeViewProjectNode, myFixture.tempDirFixture.getFile("")!!)

    private val scopeViewDocsNode
        get() = ScopeViewNode(project, scopeViewRootNode, myFixture.tempDirFixture.getFile("docs")!!)

    private val AbstractTreeNode<*>.nodeName
        get() = (this as? ProjectViewNode<*>)?.virtualFile?.name ?: name.orEmpty()

    private fun Collection<AbstractTreeNode<*>>.names() = map { it.nodeName }.sorted()

    private fun Collection<AbstractTreeNode<*>>.groups() = filterIsInstance<FoldableProjectViewNode>()

    private fun patternsFileRule(name: String = "Docs", files: String = ".foldignore", action: RuleAction = RuleAction.GROUP) =
        Rule(name, files, type = RuleType.PATTERNS_FILE, action = action)

    /**
     * Root children with the `build.gradle.kts` file reported as ignored by the VCS.
     */
    private val childrenWithIgnoredGradle
        get() = rootNode.children.map { node ->
            when (node is PsiFileNode && node.nodeName == "build.gradle.kts") {
                true -> object : PsiFileNode(project, node.value, ViewSettings.DEFAULT) {
                    override fun getFileStatus() = FileStatus.IGNORED
                }

                false -> node
            }
        }

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

    fun `test negated pattern keeps the excluded files out of the group`() {
        settings.rules.add(Rule("Docs", "*.md !CHANGELOG.md"))

        val result = modify()
        val group = result.groups().single()

        assertEquals(listOf("CHANGELOG.md", "Docs", "build.gradle.kts", "docs", "src"), result.names())
        assertEquals(listOf("README.md"), group.children.names())
    }

    fun `test pattern with trailing slash folds directories only`() {
        settings.matchDirectories = true
        settings.rules.add(Rule("Docs", "docs/ README.md/"))

        val group = modify().groups().single()

        assertEquals(listOf("docs"), group.children.names())
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

    fun `test hide action removes matched elements without a group`() {
        settings.rules.add(Rule("Docs", "*.md", action = RuleAction.HIDE))

        val result = modify()

        assertEmpty(result.groups())
        assertEquals(listOf("build.gradle.kts", "docs", "src"), result.names())
    }

    fun `test hide action does not show an empty group`() {
        settings.rules.add(Rule("Nothing", "*.nothing", action = RuleAction.HIDE))
        settings.hideEmptyGroups = false

        assertEmpty(modify().groups())
    }

    fun `test hide and group actions are applied per rule`() {
        settings.rules.add(Rule("Readme", "README.md", action = RuleAction.HIDE))
        settings.rules.add(Rule("Docs", "*.md"))

        val result = modify()
        val group = result.groups().single()

        assertEquals("Docs", group.name)
        assertEquals(listOf("CHANGELOG.md"), group.children.names())
        assertEquals(listOf("Docs", "build.gradle.kts", "docs", "src"), result.names())
    }

    fun `test hide action is applied in nested directories`() {
        settings.rules.add(Rule("Docs", "*.md", action = RuleAction.HIDE))
        settings.foldNestedDirectories = true

        val result = modify(parent = docsNode)

        assertEmpty(result.groups())
        assertEmpty(result)
    }

    fun `test action changed in place is applied`() {
        val rule = Rule("Docs", "*.md")
        settings.rules.add(rule)

        assertEquals(listOf("Docs"), modify().groups().map { it.name })

        rule.action = RuleAction.HIDE
        assertEmpty(modify().groups())
        assertEquals(listOf("build.gradle.kts", "docs", "src"), modify().names())
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

    fun `test nested directories are not folded by default`() {
        settings.rules.add(Rule("Docs", "*.md"))

        val result = modify(parent = docsNode)

        assertEmpty(result.groups())
        assertEquals(listOf("GUIDE.md"), result.names())
    }

    fun `test nested directories are folded when enabled`() {
        settings.rules.add(Rule("Docs", "*.md"))
        settings.foldNestedDirectories = true

        val result = modify(parent = docsNode)
        val group = result.groups().single()

        assertEquals(listOf("Docs"), result.names())
        assertEquals(listOf("GUIDE.md"), group.children.names())
    }

    fun `test anchored patterns match root elements only`() {
        settings.rules.add(Rule("Docs", "/*.md"))
        settings.foldNestedDirectories = true

        assertEquals(listOf("CHANGELOG.md", "README.md"), modify().groups().single().children.names())
        assertEmpty(modify(parent = docsNode).groups())
    }

    fun `test empty groups are shown in module roots only`() {
        settings.rules.add(Rule("Nothing", "*.nothing"))
        settings.foldNestedDirectories = true
        settings.hideEmptyGroups = false

        assertEquals(listOf("Nothing"), modify().groups().map { it.name })
        assertEmpty(modify(parent = docsNode).groups())
    }

    fun `test ignored files are folded in module roots only`() {
        settings.foldNestedDirectories = true
        settings.rules.add(Rule.ignoredFiles())

        val docs = docsNode
        val children = docs.children.map { node ->
            object : PsiFileNode(project, (node as PsiFileNode).value, ViewSettings.DEFAULT) {
                override fun getFileStatus() = FileStatus.IGNORED
            }
        }

        val result = modify(parent = docs, children = children)

        assertEmpty(result.groups())
        assertEquals(listOf("GUIDE.md"), result.names())
    }

    fun `test rule edited in place is applied`() {
        val rule = Rule("Docs", "*.md")
        settings.rules.add(rule)

        assertEquals(listOf("CHANGELOG.md", "README.md"), modify().groups().single().children.names())

        rule.pattern = "*.kts"
        assertEquals(listOf("build.gradle.kts"), modify().groups().single().children.names())

        settings.caseSensitive = false
        rule.pattern = "readme.MD"
        assertEquals(listOf("README.md"), modify().groups().single().children.names())
    }

    fun `test ignored files are folded into a dedicated group`() {
        settings.rules.add(Rule.ignoredFiles())

        val result = modify(children = childrenWithIgnoredGradle)
        val group = result.groups().single()

        assertEquals("Ignored files", group.name)
        assertEquals(RuleType.IGNORED_FILES, group.value.type)
        assertEquals(listOf("build.gradle.kts"), group.children.names())
        assertEquals(listOf("CHANGELOG.md", "Ignored files", "README.md", "docs", "src"), result.names())
    }

    fun `test ignored files are not folded without the ignored files rule`() {
        settings.rules.add(Rule("Docs", "*.md"))

        val result = modify(children = childrenWithIgnoredGradle)

        assertEquals(listOf("Docs"), result.groups().map { it.name })
        assertEquals(listOf("Docs", "build.gradle.kts", "docs", "src"), result.names())
    }

    fun `test disabled ignored files rule is not applied`() {
        settings.rules.add(Rule.ignoredFiles(enabled = false))
        settings.hideEmptyGroups = false

        val result = modify(children = childrenWithIgnoredGradle)

        assertEmpty(result.groups())
        assertEquals(listOf("CHANGELOG.md", "README.md", "build.gradle.kts", "docs", "src"), result.names())
    }

    fun `test disabled pattern rule is not applied`() {
        settings.rules.add(Rule("Docs", "*.md", enabled = false))
        settings.rules.add(Rule("Gradle", "*.kts"))
        settings.hideEmptyGroups = false

        val result = modify()

        assertEquals(listOf("Gradle"), result.groups().map { it.name })
        assertEquals(listOf("CHANGELOG.md", "Gradle", "README.md", "docs", "src"), result.names())
    }

    fun `test ignored files rule can be renamed and colored`() {
        settings.rules.add(Rule.ignoredFiles().copy(name = "Noise", foreground = Color.RED))

        val group = modify(children = childrenWithIgnoredGradle).groups().single()

        assertEquals("Noise", group.name)
        assertEquals(Color.RED, group.value.foreground)
        assertEquals(listOf("build.gradle.kts"), group.children.names())
    }

    fun `test ignored files rule with hide action removes ignored elements`() {
        settings.rules.add(Rule.ignoredFiles().copy(action = RuleAction.HIDE))

        val result = modify(children = childrenWithIgnoredGradle)

        assertEmpty(result.groups())
        assertEquals(listOf("CHANGELOG.md", "README.md", "docs", "src"), result.names())
    }

    fun `test ignored files rule takes precedence according to its position`() {
        // Ignored first: the ignored gradle file is not reachable for the following pattern rule.
        settings.rules.add(Rule.ignoredFiles())
        settings.rules.add(Rule("Gradle", "*.kts"))
        settings.hideEmptyGroups = false

        var groups = modify(children = childrenWithIgnoredGradle).groups()
        assertEquals(listOf("Ignored files", "Gradle"), groups.map { it.name })
        assertEquals(listOf("build.gradle.kts"), groups[0].children.names())
        assertEmpty(groups[1].children)

        // Ignored last: the pattern rule wins over the ignored files rule.
        settings.rules.reverse()

        groups = modify(children = childrenWithIgnoredGradle).groups()
        assertEquals(listOf("Gradle", "Ignored files"), groups.map { it.name })
        assertEquals(listOf("build.gradle.kts"), groups[0].children.names())
        assertEmpty(groups[1].children)
    }

    fun `test ignored files rule toggled in place is applied`() {
        settings.rules.add(Rule.ignoredFiles())
        assertEquals(listOf("Ignored files"), modify(children = childrenWithIgnoredGradle).groups().map { it.name })

        settings.foldIgnoredFiles = false
        assertEmpty(modify(children = childrenWithIgnoredGradle).groups())

        settings.foldIgnoredFiles = true
        assertEquals(listOf("Ignored files"), modify(children = childrenWithIgnoredGradle).groups().map { it.name })
    }

    fun `test patterns file rule folds elements matching the patterns read from the file`() {
        myFixture.addFileToProject(".foldignore", "# Documentation\n*.md\n\n!CHANGELOG.md\n")
        settings.rules.add(patternsFileRule())

        val result = modify()
        val group = result.groups().single()

        assertEquals("Docs", group.name)
        assertEquals(RuleType.PATTERNS_FILE, group.value.type)
        assertEquals(listOf("README.md"), group.children.names())
        assertEquals(listOf(".foldignore", "CHANGELOG.md", "Docs", "build.gradle.kts", "docs", "src"), result.names())
    }

    fun `test patterns file changes are applied`() {
        val file = myFixture.addFileToProject(".foldignore", "*.md\n").virtualFile
        settings.rules.add(patternsFileRule())

        assertEquals(listOf("CHANGELOG.md", "README.md"), modify().groups().single().children.names())

        myFixture.saveText(file, "*.kts\n")
        assertEquals(listOf("build.gradle.kts"), modify().groups().single().children.names())

        myFixture.saveText(file, "# nothing\n")
        assertEmpty(modify().groups())
    }

    fun `test patterns from multiple files are combined in order`() {
        myFixture.addFileToProject(".foldignore", "*.md\n")
        myFixture.addFileToProject(".dockerignore", "!CHANGELOG.md\n")
        settings.rules.add(patternsFileRule(files = ".foldignore .dockerignore"))

        assertEquals(listOf("README.md"), modify().groups().single().children.names())

        settings.rules[0].pattern = ".dockerignore .foldignore"
        assertEquals(listOf("CHANGELOG.md", "README.md"), modify().groups().single().children.names())
    }

    fun `test missing patterns file matches nothing`() {
        settings.rules.add(patternsFileRule(files = ".missing"))
        settings.hideEmptyGroups = false

        val group = modify().groups().single()

        assertEquals("Docs", group.name)
        assertEmpty(group.children)
    }

    fun `test patterns file rule can hide the matched elements`() {
        myFixture.addFileToProject(".foldignore", "*.md\n")
        settings.rules.add(patternsFileRule(action = RuleAction.HIDE))

        val result = modify()

        assertEmpty(result.groups())
        assertEquals(listOf(".foldignore", "build.gradle.kts", "docs", "src"), result.names())
    }

    fun `test patterns file rule is applied in nested directories when enabled`() {
        myFixture.addFileToProject(".foldignore", "*.md\n/README.md\n")
        settings.rules.add(patternsFileRule())

        assertEquals(listOf("GUIDE.md"), modify(parent = docsNode).names())

        settings.foldNestedDirectories = true
        assertEquals(listOf("GUIDE.md"), modify(parent = docsNode).groups().single().children.names())
    }

    fun `test patterns file rule honors case sensitivity`() {
        myFixture.addFileToProject(".foldignore", "readme.MD\n")
        settings.rules.add(patternsFileRule())
        settings.hideEmptyGroups = false

        assertEmpty(modify().groups().single().children)

        settings.caseSensitive = false
        assertEquals(listOf("README.md"), modify().groups().single().children.names())
    }

    fun `test patterns file rule and pattern rule are applied in order`() {
        myFixture.addFileToProject(".foldignore", "*.md\n")
        settings.rules.add(Rule("Readme", "README.md"))
        settings.rules.add(patternsFileRule())

        val groups = modify().groups()

        assertEquals(listOf("Readme", "Docs"), groups.map { it.name })
        assertEquals(listOf("README.md"), groups[0].children.names())
        assertEquals(listOf("CHANGELOG.md"), groups[1].children.names())
    }

    fun `test root files are folded in the scope view`() {
        settings.rules.add(Rule("Docs", "*.md"))

        val result = modify(parent = scopeViewRootNode)
        val group = result.groups().single()

        assertEquals(listOf("Docs", "build.gradle.kts"), result.names())
        assertEquals(listOf("CHANGELOG.md", "README.md"), group.children.names())
    }

    fun `test root files are hidden in the scope view`() {
        settings.rules.add(Rule("Docs", "*.md", action = RuleAction.HIDE))

        val result = modify(parent = scopeViewRootNode)

        assertEmpty(result.groups())
        assertEquals(listOf("build.gradle.kts"), result.names())
    }

    fun `test nested files are folded in the scope view when enabled`() {
        settings.rules.add(Rule("Docs", "*.md"))

        assertEquals(listOf("GUIDE.md"), modify(parent = scopeViewDocsNode).names())

        settings.foldNestedDirectories = true
        assertEquals(listOf("GUIDE.md"), modify(parent = scopeViewDocsNode).groups().single().children.names())
    }

    fun `test group contains matched files`() {
        settings.rules.add(Rule("Docs", "*.md"))

        val group = modify().groups().single()
        val readme = myFixture.tempDirFixture.getFile("README.md")!!
        val gradle = myFixture.tempDirFixture.getFile("build.gradle.kts")!!

        assertTrue(group.contains(readme))
        assertFalse(group.contains(gradle))
    }

    fun `test rules of the project rule set file are applied before the settings rules`() {
        myFixture.addFileToProject(
            RULE_SET_FILE,
            """
            rules:
              - id: docs
                name: Docs
                patterns: ["*.md"]
            """.trimIndent()
        )
        settings.rules.add(Rule("Gradle", "*.kts"))

        val groups = modify().groups()

        assertEquals(listOf("Docs", "Gradle"), groups.map { it.name })
        assertEquals(listOf("CHANGELOG.md", "README.md"), groups[0].children.names())
        assertTrue(groups[0].value.isFromFile)
        assertFalse(groups[1].value.isFromFile)
    }

    fun `test project rule set file changes are applied`() {
        val file = myFixture.addFileToProject(RULE_SET_FILE, "rules:\n  - {id: docs, name: Docs, patterns: ['*.md']}\n").virtualFile

        assertEquals(listOf("CHANGELOG.md", "README.md"), modify().groups().single().children.names())

        myFixture.saveText(file, "rules:\n  - {id: docs, name: Docs, patterns: ['*.kts']}\n")
        assertEquals(listOf("build.gradle.kts"), modify().groups().single().children.names())
    }

    fun `test file rule disabled by an override is not applied`() {
        myFixture.addFileToProject(RULE_SET_FILE, "rules:\n  - {id: docs, name: Docs, patterns: ['*.md']}\n")
        settings.hideEmptyGroups = false

        assertEquals(listOf("Docs"), modify().groups().map { it.name })

        settings.ruleOverrides.add(RuleOverride("docs", enabled = false))
        assertEmpty(modify().groups())
    }

    fun `test options of the project rule set file override the settings`() {
        myFixture.addFileToProject(RULE_SET_FILE, "options:\n  matchDirectories: true\nrules: []\n")
        settings.matchDirectories = false
        settings.rules.add(Rule("Sources", "src docs"))

        assertEquals(listOf("docs", "src"), modify().groups().single().children.names())
    }

    fun `test user rules are applied to the project and overridden by the project file by id`() {
        userDirectory.resolve(USER_RULES_FILE).writeText(
            """
            rules:
              - id: docs
                name: Docs
                patterns: ["*.md"]
              - id: gradle
                name: Gradle
                patterns: ["*.kts"]
            """.trimIndent()
        )
        myFixture.addFileToProject(RULE_SET_FILE, "rules:\n  - {id: docs, name: Documentation}\n")

        val groups = modify().groups()

        assertEquals(listOf("Documentation", "Gradle"), groups.map { it.name })
        assertEquals(listOf("CHANGELOG.md", "README.md"), groups[0].children.names())
        assertEquals(listOf("build.gradle.kts"), groups[1].children.names())
    }

    fun `test settings rule with the id of a file rule replaces it`() {
        myFixture.addFileToProject(RULE_SET_FILE, "rules:\n  - {id: docs, name: Docs, patterns: ['*.md']}\n")
        settings.rules.add(Rule("Docs", "*.kts"))

        val group = modify().groups().single()

        assertFalse(group.value.isFromFile)
        assertEquals(listOf("build.gradle.kts"), group.children.names())
    }

    fun `test invalid project rule set file is skipped`() {
        myFixture.addFileToProject(RULE_SET_FILE, "rules: [\n")
        settings.rules.add(Rule("Docs", "*.md"))

        assertEquals(listOf("Docs"), modify().groups().map { it.name })
    }
}
