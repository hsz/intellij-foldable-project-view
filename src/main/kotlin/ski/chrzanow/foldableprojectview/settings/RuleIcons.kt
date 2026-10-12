package ski.chrzanow.foldableprojectview.settings

import com.intellij.icons.AllIcons
import com.intellij.util.ui.EmptyIcon
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon

/**
 * A named built-in IDE icon from [RuleIcons]. It is stored by its [id].
 */
class RuleIcon internal constructor(
    val id: String,
    val icon: Icon,
) {

    /**
     * Readable name derived from the icon identifier, shown in the settings.
     */
    val displayName: String
        get() {
            val name = id.substringAfterLast('.').removePrefix("ToolWindow").removePrefix("Pp")
            return wordNames[name] ?: name.split(wordBoundary).joinToString(" ") { word ->
                wordNames[word] ?: word
            }
        }

    override fun equals(other: Any?) = other is RuleIcon && other.id == id

    override fun hashCode() = id.hashCode()

    override fun toString() = "RuleIcon($id)"

    private companion object {
        val wordBoundary = Regex("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])")

        val wordNames = mapOf(
            "Aws" to "AWS",
            "Css" to "CSS",
            "Csv" to "CSV",
            "Editorconfig" to "EditorConfig",
            "Github" to "GitHub",
            "GO" to "Go",
            "Html" to "HTML",
            "Http" to "HTTP",
            "I18n" to "i18n",
            "Jar" to "JAR",
            "JavaScript" to "JavaScript",
            "JB" to "JetBrains",
            "Json" to "JSON",
            "Lib" to "Library",
            "Mariadb" to "MariaDB",
            "MongoDB" to "MongoDB",
            "Mysql" to "MySQL",
            "Php" to "PHP",
            "Postgresql" to "PostgreSQL",
            "RabbitMQ" to "RabbitMQ",
            "Sqlite" to "SQLite",
            "Ssh" to "SSH",
            "Todo" to "TODO",
            "Ui" to "UI",
            "Xml" to "XML",
            "Yaml" to "YAML",
        )
    }
}

/**
 * Built-in IDE icons offered for the folding groups in the rule editor, see [Rule.icon].
 */
@Suppress("DEPRECATION")
object RuleIcons {

    private const val DEFAULT_ICON_ID = "AllIcons.General.CollapseComponent"

    /**
     * Icon of the folding groups without a custom icon. Resolved reflectively like the rest of the icons, so a platform
     * that renamed it does not break the class initialization; an empty icon is the last resort.
     */
    val DEFAULT_ICON: Icon = resolveFromAllIcons(DEFAULT_ICON_ID) ?: EmptyIcon.ICON_16

    private val curatedIconIds = listOf(
        "AllIcons.Nodes.Folder",
        "AllIcons.Nodes.FolderGithub",
        "AllIcons.Nodes.ConfigFolder",
        "AllIcons.Nodes.LogFolder",
        "AllIcons.Nodes.JavaDocFolder",
        "AllIcons.Nodes.PpLibFolder",
        "AllIcons.Nodes.ExtractedFolder",
        "AllIcons.Nodes.CompiledClassesFolder",
        "AllIcons.Nodes.NativeLibrariesFolder",
        "AllIcons.Nodes.TemplateRoot",
        "AllIcons.Nodes.TestSourceFolder",
        "AllIcons.Nodes.HomeFolder",
        "AllIcons.Nodes.CopyOfFolder",
        "AllIcons.Nodes.Symlink",
        "AllIcons.Nodes.Module",
        "AllIcons.Nodes.ModuleGroup",
        "AllIcons.Nodes.Package",
        "AllIcons.Nodes.Plugin",
        "AllIcons.Nodes.PluginJB",
        "AllIcons.Nodes.Library",
        "AllIcons.Nodes.JarDirectory",
        "AllIcons.Nodes.Artifact",
        "AllIcons.Nodes.ResourceBundle",
        "AllIcons.Modules.SourceRoot",
        "AllIcons.Modules.ResourcesRoot",
        "AllIcons.Modules.TestRoot",
        "AllIcons.Modules.TestResourcesRoot",
        "AllIcons.Modules.GeneratedSourceRoot",
        "AllIcons.Modules.GeneratedTestRoot",
        "AllIcons.Modules.GeneratedFolder",
        "AllIcons.Modules.ExcludeRoot",
        "AllIcons.Toolwindows.ToolWindowBuild",
        "AllIcons.Toolwindows.ToolWindowRun",
        "AllIcons.Actions.Compile",
        "AllIcons.Actions.Execute",
        "AllIcons.Actions.Rebuild",
        "AllIcons.Actions.RunAll",
        "AllIcons.Actions.BuildLoadChanges",
        "AllIcons.Actions.Lightning",
        "AllIcons.Nodes.Deploy",
        "AllIcons.Nodes.Target",
        "AllIcons.Nodes.Toolbox",
        "AllIcons.General.ExternalTools",
        "AllIcons.Nodes.Console",
        "AllIcons.Nodes.Test",
        "AllIcons.Nodes.TestGroup",
        "AllIcons.Toolwindows.ToolWindowCoverage",
        "AllIcons.Toolwindows.Problems",
        "AllIcons.Toolwindows.ToolWindowInspection",
        "AllIcons.Vcs.Branch",
        "AllIcons.Vcs.Merge",
        "AllIcons.Vcs.History",
        "AllIcons.Vcs.Patch",
        "AllIcons.Vcs.Vendors.Github",
        "AllIcons.Actions.Commit",
        "AllIcons.Actions.Diff",
        "AllIcons.Actions.Rollback",
        "AllIcons.Actions.ListChanges",
        "AllIcons.Nodes.Tag",
        "AllIcons.General.GearPlain",
        "AllIcons.General.Settings",
        "AllIcons.General.ProjectConfigurable",
        "AllIcons.General.ProjectStructure",
        "AllIcons.Nodes.Editorconfig",
        "AllIcons.Nodes.Padlock",
        "AllIcons.Nodes.Locked",
        "AllIcons.Nodes.SecurityRole",
        "AllIcons.Nodes.Ssh",
        "AllIcons.Nodes.KeymapEditor",
        "AllIcons.Toolwindows.Documentation",
        "AllIcons.Nodes.Bookmark",
        "AllIcons.Nodes.BookmarkGroup",
        "AllIcons.Nodes.Favorite",
        "AllIcons.General.Note",
        "AllIcons.General.ContextHelp",
        "AllIcons.General.TodoDefault",
        "AllIcons.General.TodoImportant",
        "AllIcons.FileTypes.Archive",
        "AllIcons.FileTypes.Config",
        "AllIcons.FileTypes.Custom",
        "AllIcons.FileTypes.Text",
        "AllIcons.FileTypes.Xml",
        "AllIcons.FileTypes.Json",
        "AllIcons.FileTypes.JsonSchema",
        "AllIcons.FileTypes.Yaml",
        "AllIcons.FileTypes.Properties",
        "AllIcons.FileTypes.Csv",
        "AllIcons.FileTypes.Image",
        "AllIcons.FileTypes.Diagram",
        "AllIcons.FileTypes.Font",
        "AllIcons.FileTypes.Gitignore",
        "AllIcons.FileTypes.Http",
        "AllIcons.FileTypes.I18n",
        "AllIcons.FileTypes.Manifest",
        "AllIcons.FileTypes.UiForm",
        "AllIcons.FileTypes.SourceMap",
        "AllIcons.General.Web",
        "AllIcons.Nodes.PpWeb",
        "AllIcons.Nodes.Services",
        "AllIcons.Nodes.DataTables",
        "AllIcons.Nodes.DataSchema",
        "AllIcons.Nodes.DataColumn",
        "AllIcons.Providers.Postgresql",
        "AllIcons.Providers.Mysql",
        "AllIcons.Providers.Mariadb",
        "AllIcons.Providers.Sqlite",
        "AllIcons.Providers.MongoDB",
        "AllIcons.Providers.Redis",
        "AllIcons.Providers.Kafka",
        "AllIcons.Providers.RabbitMQ",
        "AllIcons.Providers.Elasticsearch",
        "AllIcons.Providers.Aws",
        "AllIcons.Providers.GoogleCloud",
        "AllIcons.Providers.Microsoft",
        "AllIcons.FileTypes.Java",
        "AllIcons.FileTypes.JavaClass",
        "AllIcons.Language.Kotlin",
        "AllIcons.Language.Python",
        "AllIcons.Language.GO",
        "AllIcons.Language.Rust",
        "AllIcons.FileTypes.JavaScript",
        "AllIcons.FileTypes.Html",
        "AllIcons.FileTypes.Css",
        "AllIcons.Language.Php",
        "AllIcons.Language.Ruby",
        "AllIcons.Language.Scala",
        "AllIcons.Nodes.Class",
        "AllIcons.Nodes.Interface",
        "AllIcons.Nodes.AbstractClass",
        "AllIcons.Nodes.Enum",
        "AllIcons.Nodes.Record",
        "AllIcons.Nodes.Annotationtype",
        "AllIcons.Nodes.Function",
        "AllIcons.Nodes.Method",
        "AllIcons.Nodes.Lambda",
        "AllIcons.Nodes.Variable",
        "AllIcons.Nodes.Constant",
        "AllIcons.Nodes.Property",
        "AllIcons.Nodes.Template",
        "AllIcons.Nodes.Models",
        "AllIcons.Nodes.Controller",
        "AllIcons.General.Layout",
        "AllIcons.General.Filter",
        "AllIcons.General.User",
        "AllIcons.General.Groups",
        "AllIcons.General.Hashtag",
        "AllIcons.General.Information",
        "AllIcons.General.Warning",
        "AllIcons.General.Error",
        "AllIcons.General.GreenCheckmark",
        "AllIcons.General.OpenDisk",
        "AllIcons.Actions.Download",
        "AllIcons.Actions.Upload",
        "AllIcons.Actions.Dump",
        "AllIcons.Actions.Search",
        "AllIcons.Actions.Find",
        "AllIcons.Actions.Edit",
        "AllIcons.Actions.Refresh",
        "AllIcons.Actions.Restart",
        "AllIcons.Actions.Colors",
        "AllIcons.Actions.Install",
        "AllIcons.Actions.DependencyAnalyzer",
        "AllIcons.Actions.IntentionBulb",
        "AllIcons.Actions.Scratch",
    )

    val icons: List<RuleIcon> = curatedIconIds.mapNotNull { id ->
        resolveFromAllIcons(id)?.let { icon ->
            RuleIcon(id, icon)
        }
    }

    private val iconCache = ConcurrentHashMap<String, Icon?>()

    fun find(id: String?): Icon? {
        if (id.isNullOrBlank()) return null
        return iconCache.computeIfAbsent(id) { resolveIcon(it) }
    }

    fun findRuleIcon(id: String?): RuleIcon? {
        if (id.isNullOrBlank()) return null
        icons.firstOrNull { it.id.equals(id, ignoreCase = true) || it.id.substringAfterLast('.').equals(id, ignoreCase = true) }?.let { return it }
        val resolved = find(id) ?: return null
        return RuleIcon(id, resolved)
    }

    fun icon(rule: Rule?): Icon {
        return rule?.icon?.let(::find) ?: DEFAULT_ICON
    }

    private fun resolveIcon(id: String): Icon? {
        val trimmed = id.trim()
        // 1. Direct match in curated icons
        icons.firstOrNull { it.id.equals(trimmed, ignoreCase = true) }?.let { return it.icon }

        // 2. Short id matching in curated icons
        icons.firstOrNull { it.id.substringAfterLast('.').equals(trimmed, ignoreCase = true) }?.let { return it.icon }

        // 3. Resolve via AllIcons reflection
        return resolveFromAllIcons(trimmed)
    }

    private fun resolveFromAllIcons(path: String): Icon? {
        val normalized = path.removePrefix("AllIcons.").trim()
        val parts = normalized.split('.')
        if (parts.isEmpty()) return null

        try {
            var currentClass: Class<*> = AllIcons::class.java
            for (i in 0 until parts.size - 1) {
                val nestedName = parts[i]
                currentClass = currentClass.declaredClasses.firstOrNull {
                    it.simpleName.equals(nestedName, ignoreCase = true)
                } ?: return null
            }
            val fieldName = parts.last()
            val field = currentClass.declaredFields.firstOrNull {
                it.name.equals(fieldName, ignoreCase = true)
            } ?: return null
            return field.get(null) as? Icon
        } catch (_: ReflectiveOperationException) {
            return null
        } catch (_: LinkageError) {
            return null
        } catch (_: SecurityException) {
            return null
        }
    }
}
