package ski.chrzanow.foldableprojectview.settings

import com.intellij.icons.AllIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleIconsTest {

    @Test
    fun `palettes offer unique named built-in icons`() {
        val ids = RuleIcons.icons.map { it.id }

        assertTrue(ids.isNotEmpty())
        assertEquals(ids, ids.distinct())
        assertTrue(RuleIcons.icons.all { it.displayName.isNotBlank() })
    }

    @Test
    fun `icon display name is derived from its identifier`() {
        val names = mapOf(
            "Nodes.Folder" to "Folder",
            "Nodes.FolderGithub" to "Folder GitHub",
            "Nodes.PpLibFolder" to "Library Folder",
            "Nodes.PpWeb" to "Web",
            "Nodes.PluginJB" to "Plugin JetBrains",
            "Nodes.JavaDocFolder" to "Java Doc Folder",
            "Nodes.Editorconfig" to "EditorConfig",
            "Nodes.JarDirectory" to "JAR Directory",
            "Nodes.Ssh" to "SSH",
            "General.ExternalTools" to "External Tools",
            "General.TodoImportant" to "TODO Important",
            "Toolwindows.ToolWindowBuild" to "Build",
            "FileTypes.JsonSchema" to "JSON Schema",
            "FileTypes.Xml" to "XML",
            "FileTypes.Yaml" to "YAML",
            "FileTypes.Csv" to "CSV",
            "FileTypes.Http" to "HTTP",
            "FileTypes.UiForm" to "UI Form",
            "FileTypes.I18n" to "i18n",
            "FileTypes.Html" to "HTML",
            "FileTypes.Css" to "CSS",
            "FileTypes.JavaScript" to "JavaScript",
            "Language.GO" to "Go",
            "Language.Php" to "PHP",
            "Providers.Postgresql" to "PostgreSQL",
            "Providers.Mysql" to "MySQL",
            "Providers.Mariadb" to "MariaDB",
            "Providers.Sqlite" to "SQLite",
            "Providers.MongoDB" to "MongoDB",
            "Providers.RabbitMQ" to "RabbitMQ",
            "Providers.Aws" to "AWS",
            "Actions.XMLParser" to "XML Parser",
            "Actions.LoadHTTPConfig" to "Load HTTP Config",
        )

        for ((name, expected) in names) {
            val icon = RuleIcon("AllIcons.$name", AllIcons.Nodes.Folder)
            assertEquals(name, expected, icon.displayName)
        }
    }

    @Test
    fun `all curated icons are resolvable`() {
        for (icon in RuleIcons.icons) {
            assertSame(icon.icon, RuleIcons.find(icon.id))
            assertNotNull(RuleIcons.findRuleIcon(icon.id))
        }
    }

    @Test
    fun `icons are looked up by id`() {
        assertSame(AllIcons.Nodes.Folder, RuleIcons.find("AllIcons.Nodes.Folder"))
        assertSame(AllIcons.Nodes.Folder, RuleIcons.find("Folder"))
        assertSame(AllIcons.Nodes.Package, RuleIcons.find("AllIcons.Nodes.Package"))
        assertSame(AllIcons.Nodes.Package, RuleIcons.find("Package"))
        assertNull(RuleIcons.find("NonExistentIcon"))
        assertNull(RuleIcons.find("AllIcons.Providers.NonExistentField"))
        assertNull(RuleIcons.find("AllIcons.NonExistentGroup.NonExistentField"))
        assertNull(RuleIcons.find(""))
        assertNull(RuleIcons.find(null))
    }

    @Test
    fun `icons can be resolved via AllIcons reflection`() {
        val icon = RuleIcons.find("AllIcons.Actions.CheckOut")
        assertNotNull(icon)
        assertSame(AllIcons.Actions.CheckOut, icon)
    }

    @Test
    fun `findRuleIcon returns RuleIcon for curated and resolved icons`() {
        val curated = RuleIcons.findRuleIcon("AllIcons.Nodes.Folder")
        assertNotNull(curated)
        assertEquals("AllIcons.Nodes.Folder", curated!!.id)
        assertSame(AllIcons.Nodes.Folder, curated.icon)

        val resolved = RuleIcons.findRuleIcon("AllIcons.Actions.CheckOut")
        assertNotNull(resolved)
        assertEquals("AllIcons.Actions.CheckOut", resolved!!.id)
        assertSame(AllIcons.Actions.CheckOut, resolved.icon)

        assertNull(RuleIcons.findRuleIcon("NonExistentIcon"))
        assertNull(RuleIcons.findRuleIcon(null))
    }

    @Test
    fun `rule icon resolution falls back to default icon`() {
        val ruleWithIcon = Rule("Docs", "*.md", icon = "AllIcons.Nodes.Folder")
        assertSame(AllIcons.Nodes.Folder, RuleIcons.icon(ruleWithIcon))

        val ruleWithoutIcon = Rule("Docs", "*.md")
        assertSame(RuleIcons.icon(ruleWithoutIcon), RuleIcons.DEFAULT_ICON)

        assertSame(RuleIcons.icon(null), RuleIcons.DEFAULT_ICON)

        val ruleWithInvalidIcon = Rule("Docs", "*.md", icon = "InvalidIcon")
        assertSame(RuleIcons.icon(ruleWithInvalidIcon), RuleIcons.DEFAULT_ICON)
    }
}
