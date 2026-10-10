package ski.chrzanow.foldableprojectview.settings

import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.Converter
import com.intellij.util.xmlb.annotations.OptionTag
import ski.chrzanow.foldableprojectview.FoldableProjectViewBundle.message
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.DEFAULT_RULE_NAME
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.DEFAULT_RULE_PATTERN
import ski.chrzanow.foldableprojectview.psi.search.FoldableProjectSearchScope
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
 * What the [Rule] matches.
 */
enum class RuleType {

    /** Elements matching the glob [Rule.pattern]. */
    PATTERN,

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

data class Rule(
    var name: String = DEFAULT_RULE_NAME,

    var pattern: String = DEFAULT_RULE_PATTERN,

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

    fun getScope(project: Project, state: FoldableProjectState) = FoldableProjectSearchScope(project, state, this)

    companion object {

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
