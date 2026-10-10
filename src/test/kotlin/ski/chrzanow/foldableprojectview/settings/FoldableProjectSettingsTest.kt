package ski.chrzanow.foldableprojectview.settings

import com.intellij.configurationStore.deserialize
import com.intellij.configurationStore.serialize
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.output.XMLOutputter
import java.awt.Color

class FoldableProjectSettingsTest : LightPlatformTestCase() {

    fun `test rule colors survive serialization`() {
        val settings = FoldableProjectSettings().apply {
            rules.add(Rule("Docs", "*.md", background = Color(0x10, 0x20, 0x30), foreground = Color(0xFF, 0x00, 0x7F, 0x80)))
            rules.add(Rule("Gradle", "gradle*"))
        }

        val element = serialize(settings)!!
        val xml = XMLOutputter().outputString(element)
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
        }

        val element = serialize(settings)!!
        val xml = XMLOutputter().outputString(element)
        assertTrue(xml, xml.contains("HIDE"))

        val restored = FoldableProjectSettings().apply { loadState(element.deserialize(FoldableProjectSettings::class.java)) }

        assertEquals(settings.rules, restored.rules)
        assertEquals(RuleAction.HIDE, restored.rules[0].action)
        assertEquals(RuleAction.GROUP, restored.rules[1].action)
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

        assertEquals(listOf(Rule("Docs", "*.md")), restored.rules)
        assertEquals(RuleAction.GROUP, restored.rules.single().action)
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
        assertEquals(listOf(Rule("Docs", "*.md", action = RuleAction.HIDE)), restored.rules)
    }

    fun `test rule colors survive xml serializer roundtrip`() {
        val rule = Rule("Docs", "*.md", background = Color(0x10, 0x20, 0x30), foreground = Color(0xFF, 0x00, 0x7F))

        val element = XmlSerializer.serialize(rule)
        val restored = XmlSerializer.deserialize(element, Rule::class.java)

        assertEquals(rule, restored)
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
