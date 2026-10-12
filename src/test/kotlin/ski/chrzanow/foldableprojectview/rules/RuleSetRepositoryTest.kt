package ski.chrzanow.foldableprojectview.rules

import com.intellij.openapi.components.service
import com.intellij.testFramework.LightPlatformTestCase
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.BUILTIN_PRESETS_INDEX
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.BUILTIN_PRESETS_PATH
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.BUILTIN_PRESET_PREFIX
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.RULE_SET_FILE
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.USER_PRESETS_DIRECTORY
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.USER_RULES_FILE
import ski.chrzanow.foldableprojectview.psi.search.GlobPatterns
import ski.chrzanow.foldableprojectview.settings.RuleAction
import ski.chrzanow.foldableprojectview.settings.RuleType
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlin.io.path.createDirectories
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.writeText

/**
 * Checks the built-in presets shipped in the resources – so a broken contribution fails the build instead of the `+` menu –
 * and the lookups of the user directory.
 */
class RuleSetRepositoryTest : LightPlatformTestCase() {

    private val repository
        get() = service<RuleSetRepository>()

    private lateinit var userDirectory: Path
    private lateinit var restoreUserDirectory: () -> Unit

    override fun setUp() {
        super.setUp()
        isolateUserDirectory().let { (directory, restore) ->
            userDirectory = directory
            restoreUserDirectory = restore
        }
    }

    override fun tearDown() {
        try {
            restoreUserDirectory()
        } finally {
            super.tearDown()
        }
    }

    private fun userPreset(name: String, text: String) = userDirectory.resolve(USER_PRESETS_DIRECTORY)
        .createDirectories()
        .resolve("$name.yaml")
        .writeText(text)

    fun `test every built-in preset file is listed in the index and parses`() {
        // The resources are packed into a jar for the tests, so the source directory is listed instead.
        val directory = Path.of("src/main/resources").resolve(BUILTIN_PRESETS_PATH.removePrefix("/"))
        assertTrue(directory.toString(), Files.isDirectory(directory))
        val files = directory.listDirectoryEntries("*.yaml").map { it.name }.sorted()
        val index = javaClass.getResource(BUILTIN_PRESETS_INDEX)!!.readText()
            .lines()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }

        assertTrue(files.isNotEmpty())
        assertEquals("every preset file is listed in index.txt, and vice versa", files, index.sorted())
        assertEquals(index.size, index.distinct().size)
        assertEquals("every listed preset parses", index.size, repository.builtinPresets.size)
        assertEquals(index.map { BUILTIN_PRESET_PREFIX + it.removeSuffix(".yaml") }, repository.builtinPresets.map { it.id })
    }

    fun `test built-in presets are valid`() {
        val presets = repository.builtinPresets

        assertEquals(presets.size, presets.map { it.name }.distinct().size)
        presets.forEach { preset ->
            assertTrue(preset.id, preset.name.isNotBlank())
            assertTrue(preset.id, preset.description.isNotBlank())
            assertEquals(RuleSetLayer.BUILTIN, preset.layer)
            assertEmpty("built-in presets are self-contained", preset.extends)
            assertTrue(preset.id, preset.rules.isNotEmpty())

            preset.rules.forEach { rule ->
                assertTrue("${preset.id}: ${rule.id}", rule.id.matches(Regex("[a-z0-9][a-z0-9-]*")))
                assertTrue("${preset.id}: ${rule.id} needs a name", !rule.name.isNullOrBlank())
                assertEquals("${preset.id}: ${rule.id} should match by patterns", RuleType.PATTERN, rule.type)
                assertTrue("${preset.id}: ${rule.id} needs patterns", !rule.pattern.isNullOrBlank())
                assertEmpty("${preset.id}: ${rule.id} has invalid patterns", GlobPatterns.invalidPatterns(rule.pattern!!))
                assertTrue("${preset.id}: ${rule.id} must not hide elements", rule.action != RuleAction.HIDE)
            }
        }
        assertEquals("rule ids are unique across the presets", presets.sumOf { it.rules.size }, presets.flatMap { it.rules }.map { it.id }.distinct().size)
    }

    fun `test built-in preset rules do not fold the rule set file itself`() {
        repository.builtinPresets.flatMap { it.rules }.forEach { rule ->
            assertFalse("${rule.id} folds $RULE_SET_FILE", GlobPatterns.parse(rule.pattern!!).matches(RULE_SET_FILE))
        }
    }

    fun `test presets are found by id`() {
        val docs = repository.find("builtin:docs")!!

        assertEquals("Documentation", docs.name)
        assertSame(docs, repository.find("docs"))
        assertNull(repository.find("builtin:missing"))
        assertNull(repository.find("user:missing"))
    }

    fun `test user presets and user rules are read from the user directory`() {
        assertEmpty(repository.userPresets().ruleSets)
        assertEmpty(repository.userRules().ruleSets)

        userPreset("gradle", "name: My Gradle\nrules:\n  - {id: gradle, name: Gradle, patterns: ['*.kts']}\n")
        userPreset("broken", "rules: [\n")
        userDirectory.resolve(USER_RULES_FILE).writeText("extends: [user:gradle]\nrules: []\n")

        val presets = repository.userPresets()
        assertEquals(listOf("user:gradle"), presets.ruleSets.map { it.id })
        assertEquals("My Gradle", presets.ruleSets.single().name)
        assertEquals(RuleSetLayer.USER, presets.ruleSets.single().layer)
        assertEquals(listOf(userDirectory.resolve(USER_PRESETS_DIRECTORY).resolve("broken.yaml")), presets.problems.map { it.path })

        assertEquals(presets.ruleSets.single(), repository.find("user:gradle"))
        assertEquals("user presets take precedence over the built-in ones for bare ids", presets.ruleSets.single(), repository.find("gradle"))

        val userRules = repository.userRules().ruleSets.single()
        assertEquals(listOf("user:gradle"), userRules.extends)
        assertEquals(listOf("gradle"), repository.resolve(userRules).rules.map { it.id })
    }

    fun `test extends includes the rules of the extended sets first and overrides them by id`() {
        userPreset("base", "options: {matchDirectories: true}\nrules:\n  - {id: a, name: A, patterns: ['*.a'], background: Blue}\n  - {id: b, name: B, patterns: ['*.b']}\n")
        val ruleSet = RuleSetYaml.parse(
            """
            extends: [builtin:docs, user:base]
            options: {hideEmptyGroups: false}
            rules:
              - {id: a, enabled: false}
              - {id: c, name: C, patterns: ['*.c']}
            """.trimIndent(),
            RULE_SET_FILE,
            RuleSetLayer.PROJECT,
        )

        val resolved = repository.resolve(ruleSet)

        assertEmpty(resolved.problems)
        assertEquals(RuleSetOptions(matchDirectories = true, hideEmptyGroups = false), resolved.options)
        assertEquals(listOf("docs", "a", "b", "c"), resolved.rules.map { it.id })

        val a = resolved.rules[1]
        assertEquals("A", a.name)
        assertEquals("*.a", a.pattern)
        assertEquals(false, a.enabled)
        assertNotNull(a.background)

        assertTrue(resolved.toRules().all { it.origin == ruleSet.origin })
    }

    fun `test unknown and cyclic extends are reported and skipped`() {
        userPreset("a", "extends: [user:b]\nrules:\n  - {id: a, name: A, patterns: ['*.a']}\n")
        userPreset("b", "extends: [user:a, user:missing]\nrules:\n  - {id: b, name: B, patterns: ['*.b']}\n")

        val resolved = repository.resolve(repository.find("user:a")!!)

        assertEquals(listOf("b", "a"), resolved.rules.map { it.id })
        assertEquals(setOf("Cyclic extends: user:a", "Unknown rule set: user:missing"), resolved.problems.map { it.message }.toSet())
    }

    fun `test files are re-read when they change`() {
        val file = userDirectory.resolve(USER_RULES_FILE)
        file.writeText("rules:\n  - {id: a, name: A, patterns: ['*.a']}\n")
        assertEquals(listOf("a"), repository.userRules().ruleSets.single().rules.map { it.id })

        file.writeText("rules:\n  - {id: b, name: B, patterns: ['*.b']}\n")
        // The modification time has a coarse resolution, so make sure the stamp differs.
        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + 5_000))
        assertEquals(listOf("b"), repository.userRules().ruleSets.single().rules.map { it.id })

        Files.delete(file)
        assertEmpty(repository.userRules().ruleSets)
    }

    fun `test written user rules are read back`() {
        repository.writeUserRules(
            RuleSet(
                id = "x",
                layer = RuleSetLayer.PROJECT,
                name = "Defaults",
                rules = listOf(RulePatch(id = "docs", name = "Docs", pattern = "*.md", type = RuleType.PATTERN)),
            )
        )

        val userRules = repository.userRules().ruleSets.single()
        assertEquals("Defaults", userRules.name)
        assertEquals(RuleSetLayer.USER, userRules.layer)
        assertEquals(repository.userRulesFile, userRules.path)
        assertEquals(listOf("docs"), userRules.rules.map { it.id })
    }

    fun `test user file paths are recognized`() {
        assertTrue(repository.isUserFile(userDirectory.resolve(USER_RULES_FILE).toString()))
        assertTrue(repository.isUserFile(userDirectory.resolve(USER_PRESETS_DIRECTORY).resolve("x.yaml").toString()))
        assertFalse(repository.isUserFile("/somewhere/else/rules.yaml"))
    }
}
