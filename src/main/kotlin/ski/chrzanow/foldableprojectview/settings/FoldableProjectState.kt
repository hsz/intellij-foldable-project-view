package ski.chrzanow.foldableprojectview.settings

import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.Converter
import com.intellij.util.xmlb.annotations.OptionTag
import ski.chrzanow.foldableprojectview.FoldableProjectViewBundle.message
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
 */
data class Rule(
    var name: String = "",

    var pattern: String = "",

    @get:OptionTag(converter = ColorConverter::class)
    var background: Color? = null,

    @get:OptionTag(converter = ColorConverter::class)
    var foreground: Color? = null,

    var action: RuleAction = RuleAction.GROUP,

    var type: RuleType = RuleType.PATTERN,

    var enabled: Boolean = true,
) {

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

private class ColorConverter : Converter<Color>() {

    override fun toString(value: Color) = value.rgb.toString()

    // java.awt.Color is required here to preserve the alpha channel; the result is wrapped in JBColor anyway
    @Suppress("UseJBColor")
    override fun fromString(value: String) = runCatching { Color(value.toInt(), true).let { JBColor(it, it) } }.getOrNull()
}
