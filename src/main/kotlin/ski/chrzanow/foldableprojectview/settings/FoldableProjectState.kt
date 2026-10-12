package ski.chrzanow.foldableprojectview.settings

import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.Converter
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.Transient
import ski.chrzanow.foldableprojectview.FoldableProjectViewBundle.message
import ski.chrzanow.foldableprojectview.rules.RuleOrigin
import ski.chrzanow.foldableprojectview.psi.search.FoldableProjectSearchScope
import ski.chrzanow.foldableprojectview.psi.search.GlobPatterns
import com.intellij.ui.JBColor
import java.awt.Color

interface FoldableProjectState {

    val foldingEnabled: Boolean
    val matchDirectories: Boolean
    val foldNestedDirectories: Boolean
    val hideEmptyGroups: Boolean
    val caseSensitive: Boolean
    val rules: MutableList<Rule>
}

/**
 * The built-in rule folding the VCS-ignored files, see [RuleType.IGNORED_FILES].
 */
val FoldableProjectState.ignoredFilesRule: Rule?
    get() = rules.firstOrNull { it.type == RuleType.IGNORED_FILES }

/**
 * Paths of the patterns files used by the active [RuleType.PATTERNS_FILE] rules, relative to the module content roots,
 * see [Rule.patternsFilePaths].
 */
val FoldableProjectState.patternsFilePaths: List<String>
    get() = rules
        .filter { it.isActive && it.type == RuleType.PATTERNS_FILE }
        .flatMap(Rule::patternsFilePaths)
        .distinct()

/**
 * What the [Rule] matches.
 */
enum class RuleType {

    /** Elements matching the glob [Rule.pattern]. */
    PATTERN,

    /**
     * Elements matching the glob patterns read from the files listed in [Rule.pattern], like `.foldignore` or `.dockerignore`.
     * The files are located in the module content roots, and contain one gitignore-like pattern per line, see [GlobPatterns.parseLines].
     */
    PATTERNS_FILE,

    /**
     * Elements ignored by the VCS. There is a single built-in rule of this type, which cannot be removed
     * but can be disabled, renamed, colored, reordered, and switched to the [RuleAction.HIDE] action.
     */
    IGNORED_FILES,
}

/**
 * What happens to the elements matched by a [Rule] in the Project View.
 */
enum class RuleAction {

    /** Move the matched elements into a folding group. */
    GROUP,

    /** Remove the matched elements from the Project View. */
    HIDE,
}

/**
 * Folding rule. New rules start with an empty [name] and [pattern]; such rules are kept, but not applied until filled in,
 * see [isActive].
 *
 * The [background] and [foreground] of the folding group are either theme-aware [RuleColor]s from the [RuleColors] palette
 * or custom colors picked by the user, see [ColorConverter].
 *
 * The [id] is a stable identity of the rule across the rule set files and the IDE settings: a rule set extending another
 * one overrides its rules by id, and the IDE settings keep the per-project overrides of the file-sourced rules by id,
 * see `RuleSetRepository` and `FoldableRulesResolver`. It is derived from the [name] when not set, see [effectiveId].
 */
data class Rule(
    var name: String = "",

    var pattern: String = "",

    @get:OptionTag(converter = BackgroundColorConverter::class)
    var background: Color? = null,

    @get:OptionTag(converter = ForegroundColorConverter::class)
    var foreground: Color? = null,

    var action: RuleAction = RuleAction.GROUP,

    var type: RuleType = RuleType.PATTERN,

    var enabled: Boolean = true,

    var id: String = "",

    var icon: String? = null,
) {

    /**
     * Where the rule comes from: `null` for the rules stored in the IDE settings, otherwise the rule set file.
     * Not persisted; set by `FoldableRulesResolver` when the layers are merged.
     */
    @get:Transient
    var origin: RuleOrigin? = null

    /**
     * Whether the rule comes from a rule set file rather than the IDE settings, see [origin].
     */
    val isFromFile
        get() = origin != null

    /**
     * The [id], or a slug derived from the [name] (`CI/CD` becomes `ci-cd`) when the id is not set.
     * The built-in ignored files rule is always identified as [IGNORED_FILES_ID].
     */
    val effectiveId
        get() = when {
            type == RuleType.IGNORED_FILES -> IGNORED_FILES_ID
            id.isNotBlank() -> id
            else -> slug(name)
        }

    /**
     * Whether [other] stands for this very rule: the same instance for the rules of the IDE settings, which the settings
     * page edits in place, or the same file-sourced rule, whose instances are rebuilt when the files are re-read.
     */
    fun isSameAs(other: Rule?) = other != null && (this === other || (isFromFile && other.isFromFile && effectiveId == other.effectiveId))

    /**
     * Whether the rule is enabled and has anything to match.
     */
    val isActive
        get() = enabled && (type == RuleType.IGNORED_FILES || pattern.isNotBlank())

    /**
     * The [name] shown in the Project View and in the search scopes, with a placeholder for the rules that have no name yet.
     */
    val displayName
        get() = name.ifBlank { message("foldableProjectView.unnamedRule") }

    /**
     * Whether the rule matches by glob patterns, either entered directly or read from the patterns files.
     */
    val usesPatterns
        get() = type != RuleType.IGNORED_FILES

    /**
     * Paths of the patterns files listed in [pattern], relative to the module content root, see [RuleType.PATTERNS_FILE].
     */
    val patternsFilePaths
        get() = when (type) {
            RuleType.PATTERNS_FILE -> patternsFilePaths(pattern)
            else -> emptyList()
        }

    fun getScope(project: Project, state: FoldableProjectState) = FoldableProjectSearchScope(project, state, this)

    companion object {

        /**
         * The [effectiveId] of the built-in ignored files rule, see [RuleType.IGNORED_FILES].
         */
        const val IGNORED_FILES_ID = "ignored-files"

        /**
         * Turns the [text] into an id: lowercase, with runs of non-alphanumeric characters replaced by a single dash.
         */
        fun slug(text: String) = text
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), "-")
            .trim('-')

        /**
         * Parses the space-separated paths of the patterns files, see [RuleType.PATTERNS_FILE].
         */
        fun patternsFilePaths(text: String) = GlobPatterns.split(text).map { it.trim('/') }.filter(String::isNotEmpty)

        /**
         * Creates the built-in rule folding the VCS-ignored files.
         */
        fun ignoredFiles(enabled: Boolean = true) = Rule(
            name = message("foldableProjectView.ignoredFiles"),
            pattern = "",
            type = RuleType.IGNORED_FILES,
            enabled = enabled,
        )
    }
}

/**
 * Stores a [RuleColor] by its id (`Blue`), and a custom color by its ARGB value (`-16777216`), as the earlier versions did.
 */
private abstract class ColorConverter(private val palette: List<RuleColor>) : Converter<Color>() {

    override fun toString(value: Color) = (value as? RuleColor)?.id ?: value.rgb.toString()

    // java.awt.Color is required here to preserve the alpha channel; the result is wrapped in JBColor anyway
    @Suppress("UseJBColor")
    override fun fromString(value: String) = palette.firstOrNull { it.id == value }
        ?: value.toIntOrNull()?.let { Color(it, true) }?.let { JBColor(it, it) }
}

private class BackgroundColorConverter : ColorConverter(RuleColors.backgrounds)

private class ForegroundColorConverter : ColorConverter(RuleColors.foregrounds)
