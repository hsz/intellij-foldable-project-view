package ski.chrzanow.foldableprojectview.settings

import com.intellij.configurationStore.deserialize
import com.intellij.configurationStore.serialize
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.xmlb.XmlSerializer
import ski.chrzanow.foldableprojectview.rules.RuleOrigin
import ski.chrzanow.foldableprojectview.rules.RuleSetLayer
import java.awt.Color

class FoldableProjectSettingsTest : LightPlatformTestCase() {

    /**
     * Restores the settings from the serialized [settings] the way the component store does, including the migration.
     */
    private fun roundtrip(settings: FoldableProjectSettings) = serialize(settings)!!.let { element ->
        FoldableProjectSettings().apply { loadState(element.deserialize(FoldableProjectSettings::class.java)) }
    }

    fun `test rule colors survive serialization`() {
        val settings = FoldableProjectSettings().apply {
            rules.add(Rule("Docs", "*.md", background = Color(0x10, 0x20, 0x30), foreground = Color(0xFF, 0x00, 0x7F, 0x80)))
            rules.add(Rule("Gradle", "gradle*"))
            rules.add(Rule.ignoredFiles())
        }

        val element = serialize(settings)!!
        val xml = JDOMUtil.write(element)
        assertTrue(xml, xml.contains("background"))
        assertTrue(xml, xml.contains("foreground"))

        val restored = FoldableProjectSettings().apply { loadState(element.deserialize(FoldableProjectSettings::class.java)) }

        assertEquals(settings.rules, restored.rules)
        assertEquals(Color(0x10, 0x20, 0x30), restored.rules[0].background)
        assertEquals(Color(0xFF, 0x00, 0x7F, 0x80), restored.rules[0].foreground)
        assertNull(restored.rules[1].background)
        assertNull(restored.rules[1].foreground)
    }

    fun `test rule action survives serialization`() {
        val settings = FoldableProjectSettings().apply {
            rules.add(Rule("Docs", "*.md", action = RuleAction.HIDE))
            rules.add(Rule("Gradle", "gradle*"))
            rules.add(Rule.ignoredFiles())
        }

        val element = serialize(settings)!!
        val xml = JDOMUtil.write(element)
        assertTrue(xml, xml.contains("HIDE"))

        val restored = FoldableProjectSettings().apply { loadState(element.deserialize(FoldableProjectSettings::class.java)) }

        assertEquals(settings.rules, restored.rules)
        assertEquals(RuleAction.HIDE, restored.rules[0].action)
        assertEquals(RuleAction.GROUP, restored.rules[1].action)
    }

    fun `test rule type and enabled flag survive serialization`() {
        val settings = FoldableProjectSettings().apply {
            rules.add(Rule("Docs", "*.md", enabled = false))
            rules.add(Rule.ignoredFiles().copy(name = "Noise", action = RuleAction.HIDE, enabled = false))
            rules.add(Rule("Gradle", "gradle*"))
        }

        val element = serialize(settings)!!
        val xml = JDOMUtil.write(element)
        assertTrue(xml, xml.contains("IGNORED_FILES"))
        assertFalse("the legacy option must not be stored anymore", xml.contains("HIDE_IGNORED_FILES"))

        val restored = roundtrip(settings)

        assertEquals(settings.rules, restored.rules)
        assertEquals(listOf(false, false, true), restored.rules.map { it.enabled })
        assertEquals(RuleType.IGNORED_FILES, restored.rules[1].type)
        assertEquals("Noise", restored.ignoredFilesRule!!.name)
    }

    fun `test patterns file rule survives serialization`() {
        val settings = FoldableProjectSettings().apply {
            rules.add(Rule("Docs", ".foldignore .dockerignore", type = RuleType.PATTERNS_FILE, action = RuleAction.HIDE))
            rules.add(Rule.ignoredFiles())
        }

        val element = serialize(settings)!!
        val xml = JDOMUtil.write(element)
        assertTrue(xml, xml.contains("PATTERNS_FILE"))

        val restored = roundtrip(settings)

        assertEquals(settings.rules, restored.rules)
        assertEquals(RuleType.PATTERNS_FILE, restored.rules[0].type)
        assertEquals(listOf(".foldignore", ".dockerignore"), restored.rules[0].patternsFilePaths)
    }

    fun `test ignored files rule is stored once it is present`() {
        val restored = roundtrip(FoldableProjectSettings().apply { rules.add(Rule.ignoredFiles()) })

        assertEquals(listOf(Rule.ignoredFiles()), restored.rules)
    }

    fun `test legacy settings without rule action are loaded with the group action`() {
        val element = JDOMUtil.load(
            """
            <FoldableProjectSettings>
              <option name="RULES">
                <list>
                  <Rule>
                    <option name="name" value="Docs" />
                    <option name="pattern" value="*.md" />
                  </Rule>
                </list>
              </option>
            </FoldableProjectSettings>
            """.trimIndent()
        )

        val restored = FoldableProjectSettings().apply { loadState(element.deserialize(FoldableProjectSettings::class.java)) }

        assertEquals(listOf(Rule("Docs", "*.md"), Rule.ignoredFiles()), restored.rules)
        assertEquals(RuleAction.GROUP, restored.rules.first().action)
        assertEquals(RuleType.PATTERN, restored.rules.first().type)
        assertTrue(restored.rules.first().enabled)
    }

    fun `test legacy disabled fold ignored files option is migrated on deserialization`() {
        val element = JDOMUtil.load(
            """
            <FoldableProjectSettings>
              <option name="HIDE_IGNORED_FILES" value="false" />
              <option name="RULES">
                <list>
                  <Rule>
                    <option name="name" value="Docs" />
                    <option name="pattern" value="*.md" />
                  </Rule>
                </list>
              </option>
            </FoldableProjectSettings>
            """.trimIndent()
        )

        val restored = FoldableProjectSettings().apply { loadState(element.deserialize(FoldableProjectSettings::class.java)) }

        assertEquals(listOf(Rule("Docs", "*.md"), Rule.ignoredFiles(enabled = false)), restored.rules)
        assertFalse(restored.foldIgnoredFiles)
        assertTrue(restored.legacyFoldIgnoredFiles)

        val xml = JDOMUtil.write(serialize(restored)!!)
        assertFalse(xml, xml.contains("HIDE_IGNORED_FILES"))
        assertTrue(xml, xml.contains("IGNORED_FILES"))
    }

    fun `test legacy hide all groups option is migrated on deserialization`() {
        val element = JDOMUtil.load(
            """
            <FoldableProjectSettings>
              <option name="HIDE_ALL_GROUPS" value="true" />
              <option name="RULES">
                <list>
                  <Rule>
                    <option name="name" value="Docs" />
                    <option name="pattern" value="*.md" />
                  </Rule>
                </list>
              </option>
            </FoldableProjectSettings>
            """.trimIndent()
        )

        val restored = FoldableProjectSettings().apply { loadState(element.deserialize(FoldableProjectSettings::class.java)) }

        assertFalse(restored.hideAllGroups)
        assertEquals(listOf(Rule("Docs", "*.md", action = RuleAction.HIDE), Rule.ignoredFiles().copy(action = RuleAction.HIDE)), restored.rules)
    }

    fun `test rule colors survive xml serializer roundtrip`() {
        val rule = Rule("Docs", "*.md", background = Color(0x10, 0x20, 0x30), foreground = Color(0xFF, 0x00, 0x7F))

        val element = XmlSerializer.serialize(rule)
        val restored = XmlSerializer.deserialize(element, Rule::class.java)

        assertEquals(rule, restored)
    }

    fun `test palette colors are stored by name`() {
        val settings = FoldableProjectSettings().apply {
            rules.add(Rule("Docs", "*.md", background = RuleColors.background("Blue"), foreground = RuleColors.foreground("Green")))
            rules.add(Rule.ignoredFiles())
        }

        val element = serialize(settings)!!
        val xml = JDOMUtil.write(element)
        assertTrue(xml, xml.contains("""<option name="background" value="Blue" />"""))
        assertTrue(xml, xml.contains("""<option name="foreground" value="Green" />"""))

        val restored = roundtrip(settings)

        assertEquals(settings.rules, restored.rules)
        assertSame(RuleColors.background("Blue"), restored.rules[0].background)
        assertSame(RuleColors.foreground("Green"), restored.rules[0].foreground)
    }

    fun `test unknown color names and malformed colors are dropped on deserialization`() {
        val element = JDOMUtil.load(
            """
            <FoldableProjectSettings>
              <option name="RULES">
                <list>
                  <Rule>
                    <option name="name" value="Docs" />
                    <option name="pattern" value="*.md" />
                    <option name="background" value="Purple" />
                    <option name="foreground" value="not-a-color" />
                  </Rule>
                </list>
              </option>
            </FoldableProjectSettings>
            """.trimIndent()
        )

        val restored = FoldableProjectSettings().apply { loadState(element.deserialize(FoldableProjectSettings::class.java)) }

        assertNull(restored.rules.first().background)
        assertNull(restored.rules.first().foreground)
    }

    fun `test rule ids and overrides survive serialization and the origin is not stored`() {
        val settings = FoldableProjectSettings().apply {
            rules.add(Rule("Docs", "*.md", id = "docs").apply { origin = RuleOrigin(RuleSetLayer.PROJECT, "x", null) })
            rules.add(Rule.ignoredFiles())
            ruleOverrides.add(RuleOverride("gradle", enabled = false))
        }

        val element = serialize(settings)!!
        val xml = JDOMUtil.write(element)
        assertTrue(xml, xml.contains("""<option name="id" value="docs" />"""))
        assertTrue(xml, xml.contains("RULE_OVERRIDES"))
        assertFalse(xml, xml.contains("origin"))

        val restored = roundtrip(settings)

        assertEquals(settings.rules, restored.rules)
        assertEquals("docs", restored.rules[0].id)
        assertNull(restored.rules[0].origin)
        assertEquals(listOf(RuleOverride("gradle", enabled = false)), restored.ruleOverrides)
    }

    fun `test effective id is derived from the name`() {
        assertEquals("docs", Rule("Docs", "*.md").effectiveId)
        assertEquals("ci-cd", Rule("CI/CD", "*.yml").effectiveId)
        assertEquals("custom", Rule("Docs", "*.md", id = "custom").effectiveId)
        assertEquals(Rule.IGNORED_FILES_ID, Rule.ignoredFiles().copy(name = "Noise").effectiveId)
        assertEquals("", Rule().effectiveId)
    }

    fun `test copyFrom keeps rule colors`() {
        val source = FoldableProjectSettings().apply {
            rules.add(Rule("Docs", "*.md", background = Color.RED, foreground = Color.BLUE))
        }
        val target = FoldableProjectSettings().apply { copyFrom(source) }

        assertEquals(source.rules, target.rules)
        assertEquals(Color.RED, target.rules[0].background)
        assertEquals(Color.BLUE, target.rules[0].foreground)
    }
}
