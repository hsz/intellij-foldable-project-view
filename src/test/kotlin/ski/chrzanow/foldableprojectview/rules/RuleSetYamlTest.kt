package ski.chrzanow.foldableprojectview.rules

import com.intellij.testFramework.LightPlatformTestCase
import ski.chrzanow.foldableprojectview.psi.search.GlobPatterns
import ski.chrzanow.foldableprojectview.settings.FoldableProjectSettings
import ski.chrzanow.foldableprojectview.settings.Rule
import ski.chrzanow.foldableprojectview.settings.RuleAction
import ski.chrzanow.foldableprojectview.settings.RuleColors
import ski.chrzanow.foldableprojectview.settings.RuleType
import java.awt.Color

class RuleSetYamlTest : LightPlatformTestCase() {

    private fun parse(text: String) = RuleSetYaml.parse(text, "test", RuleSetLayer.PROJECT)

    private fun assertInvalid(text: String, message: String) {
        val exception = runCatching { parse(text) }.exceptionOrNull()
        assertNotNull("Expected a format exception for:\n$text", exception)
        assertTrue(exception.toString(), exception is RuleSetFormatException)
        assertTrue(exception!!.message, exception.message!!.contains(message))
    }

    fun `test full rule set is parsed`() {
        val ruleSet = parse(
            """
            name: Kotlin Gradle project
            description: Folds the build noise
            tags: [kotlin, gradle]
            extends: [builtin:docs, user:ci]
            options:
              matchDirectories: true
              hideEmptyGroups: false
            rules:
              - id: build
                name: Build
                patterns: ["gradle/", "gradlew*", "*.gradle.kts", "My Notes.md"]
                background: Blue
                foreground: "#FF0000"
                action: hide
              - id: local
                name: Local
                patternsFiles: [.foldignore, .dockerignore]
                enabled: false
              - type: ignoredFiles
                enabled: false
              - name: CI/CD
                patterns: .github/ .gitlab-ci.yml
            """.trimIndent()
        )

        assertEquals("Kotlin Gradle project", ruleSet.name)
        assertEquals("Folds the build noise", ruleSet.description)
        assertEquals(listOf("kotlin", "gradle"), ruleSet.tags)
        assertEquals(listOf("builtin:docs", "user:ci"), ruleSet.extends)
        assertEquals(RuleSetOptions(matchDirectories = true, hideEmptyGroups = false), ruleSet.options)

        val (build, local, ignored, ci) = ruleSet.rules
        assertEquals("build", build.id)
        assertEquals("Build", build.name)
        assertEquals("gradle/ gradlew* *.gradle.kts \"My Notes.md\"", build.pattern)
        assertEquals(RuleType.PATTERN, build.type)
        assertSame(RuleColors.background("Blue"), build.background)
        assertEquals(Color(0xFF, 0x00, 0x00), build.foreground)
        assertEquals(RuleAction.HIDE, build.action)
        assertNull(build.enabled)

        assertEquals("local", local.id)
        assertEquals(RuleType.PATTERNS_FILE, local.type)
        assertEquals(".foldignore .dockerignore", local.pattern)
        assertEquals(false, local.enabled)

        assertEquals(Rule.IGNORED_FILES_ID, ignored.id)
        assertEquals(RuleType.IGNORED_FILES, ignored.type)
        assertEquals(false, ignored.enabled)

        assertEquals("ci-cd", ci.id)
        assertEquals(".github/ .gitlab-ci.yml", ci.pattern)
    }

    fun `test rule patch materializes a rule with defaults`() {
        val rule = parse("rules:\n  - {id: docs, name: Docs, patterns: ['*.md']}\n").rules.single().toRule()

        assertEquals(Rule("Docs", "*.md", id = "docs"), rule)
        assertTrue(rule.enabled)
        assertEquals(RuleAction.GROUP, rule.action)
        assertNull(rule.origin)
    }

    fun `test empty and minimal documents are accepted`() {
        assertEquals(RuleSet("test", RuleSetLayer.PROJECT, "test"), parse(""))
        assertEquals(RuleSet("test", RuleSetLayer.PROJECT, "test"), parse("# nothing here\n"))
        assertEmpty(parse("rules: []\n").rules)
    }

    fun `test invalid documents are reported`() {
        assertInvalid("- a\n- b\n", "Expected a mapping")
        assertInvalid("rules: 42\n", "Expected a list")
        assertInvalid("rules:\n  - patterns: ['*.md']\n", "neither an id nor a name")
        assertInvalid("rules:\n  - {id: a, name: A}\n  - {id: a, name: B}\n", "Duplicate rule id")
        assertInvalid("rules:\n  - {id: a, patterns: ['*.md'], patternsFiles: [.foldignore]}\n", "either patterns or patternsFiles")
        assertInvalid("rules:\n  - {id: a, type: files}\n", "Unknown rule type")
        assertInvalid("rules:\n  - {id: a, action: remove}\n", "Unknown rule action")
        assertInvalid("rules:\n  - {id: a, background: Purple}\n", "Unknown color")
        assertInvalid("rules:\n  - {id: a, enabled: yes please}\n", "Expected true or false")
        assertInvalid("options:\n  hideAllGroups: true\n", "Unknown option")
        assertInvalid("rules: [\n", "")
    }

    fun `test colors are parsed and written in both forms`() {
        assertSame(RuleColors.background("Green"), RuleSetYaml.parseColor("green", RuleColors.backgrounds))
        assertEquals(Color(0x10, 0x20, 0x30), RuleSetYaml.parseColor("#102030", RuleColors.foregrounds))
        assertEquals(Color(0x10, 0x20, 0x30, 0x80), RuleSetYaml.parseColor("#80102030", RuleColors.foregrounds))

        assertEquals("Green", RuleSetYaml.formatColor(RuleColors.background("Green")!!))
        assertEquals("#102030", RuleSetYaml.formatColor(Color(0x10, 0x20, 0x30)))
        assertEquals("#80102030", RuleSetYaml.formatColor(Color(0x10, 0x20, 0x30, 0x80)))
    }

    fun `test exported settings survive a roundtrip`() {
        val settings = FoldableProjectSettings().apply {
            matchDirectories = true
            caseSensitive = false
            rules.add(Rule("Docs", "*.md \"My Notes.md\" !CHANGELOG.md", background = RuleColors.background("Blue"), foreground = Color(0x10, 0x20, 0x30)))
            rules.add(Rule("Local", ".foldignore", type = RuleType.PATTERNS_FILE, action = RuleAction.HIDE, enabled = false))
            rules.add(Rule.ignoredFiles(enabled = false))
            rules.add(Rule("From file", "*.kt", id = "file").apply { origin = RuleOrigin(RuleSetLayer.USER, "rules.yaml", null) })
        }

        val exported = RuleSetYaml.export(settings, "My project", "Exported")
        val text = RuleSetYaml.write(exported)
        val restored = parse(text)

        assertEquals("My project", restored.name)
        assertEquals("Exported", restored.description)
        assertEquals(RuleSetOptions.of(settings), restored.options)
        assertEquals("rules from files are provided by their own files", listOf("docs", "local", Rule.IGNORED_FILES_ID), restored.rules.map { it.id })

        val rules = restored.rules.map { it.toRule() }
        assertEquals(settings.rules.take(3).map { it.copy(id = it.effectiveId) }, rules)
        assertSame(RuleColors.background("Blue"), rules[0].background)
        assertEquals(Color(0x10, 0x20, 0x30), rules[0].foreground)
        assertEquals(listOf("*.md", "My Notes.md", "!CHANGELOG.md"), GlobPatterns.split(rules[0].pattern))
    }

    fun `test written yaml is readable and quotes the special scalars`() {
        val text = RuleSetYaml.write(
            RuleSet(
                id = "x",
                layer = RuleSetLayer.PROJECT,
                name = "Docs: everything",
                rules = listOf(RulePatch(id = "docs", name = "Docs", pattern = "*.md #notes", type = RuleType.PATTERN, background = Color(0x10, 0x20, 0x30))),
            )
        )

        assertTrue(text, text.contains("name: 'Docs: everything'"))
        assertTrue(text, text.contains("patterns: ['*.md', '#notes']"))
        assertTrue(text, text.contains("background: '#102030'"))
        assertEquals("Docs: everything", parse(text).name)
        assertEquals("*.md #notes", parse(text).rules.single().pattern)
    }
}
