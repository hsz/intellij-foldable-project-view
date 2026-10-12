package ski.chrzanow.foldableprojectview.rules

import ski.chrzanow.foldableprojectview.FoldableProjectViewBundle.message
import ski.chrzanow.foldableprojectview.settings.FoldableProjectState
import ski.chrzanow.foldableprojectview.settings.Rule
import ski.chrzanow.foldableprojectview.settings.RuleAction
import ski.chrzanow.foldableprojectview.settings.RuleType
import java.awt.Color
import java.nio.file.Path

/**
 * Where a [RuleSet] is stored. The layers are applied in this order, the later ones overriding the earlier ones by rule id,
 * and the IDE settings (`.idea/FoldableProjectView.xml`) come last, see `FoldableRulesResolver`.
 */
enum class RuleSetLayer {

    /** Presets shipped with the plugin in its resources. Never applied on their own – only offered in the settings and via `extends`. */
    BUILTIN,

    /** Rule sets of the user directory: the presets and the `rules.yaml` applied to every project. */
    USER,

    /** The `.foldableprojectview.yaml` files of the project root and the module content roots, versioned with the project. */
    PROJECT,
}

/**
 * The rule set file a [Rule] comes from, see [Rule.origin].
 */
data class RuleOrigin(val layer: RuleSetLayer, val ruleSet: String, val path: Path?) {

    /**
     * Short description of the origin shown in the settings, like `Project file` or `User file`.
     */
    val displayName: String
        get() = when (layer) {
            RuleSetLayer.BUILTIN -> message("foldableProjectView.settings.origin.builtin", ruleSet)
            RuleSetLayer.USER -> message("foldableProjectView.settings.origin.user")
            RuleSetLayer.PROJECT -> message("foldableProjectView.settings.origin.project")
        }
}

/**
 * The options a rule set may set for the projects it is applied to; `null` leaves the IDE setting untouched.
 */
data class RuleSetOptions(
    val foldingEnabled: Boolean? = null,
    val matchDirectories: Boolean? = null,
    val foldNestedDirectories: Boolean? = null,
    val hideEmptyGroups: Boolean? = null,
    val caseSensitive: Boolean? = null,
) {

    val isEmpty
        get() = this == RuleSetOptions()

    /**
     * Returns these options with the non-null ones of [other] on top.
     */
    fun overriddenBy(other: RuleSetOptions) = RuleSetOptions(
        foldingEnabled = other.foldingEnabled ?: foldingEnabled,
        matchDirectories = other.matchDirectories ?: matchDirectories,
        foldNestedDirectories = other.foldNestedDirectories ?: foldNestedDirectories,
        hideEmptyGroups = other.hideEmptyGroups ?: hideEmptyGroups,
        caseSensitive = other.caseSensitive ?: caseSensitive,
    )

    companion object {

        /**
         * Options with every value taken from the [state], used to export the settings.
         */
        fun of(state: FoldableProjectState) = RuleSetOptions(
            foldingEnabled = state.foldingEnabled,
            matchDirectories = state.matchDirectories,
            foldNestedDirectories = state.foldNestedDirectories,
            hideEmptyGroups = state.hideEmptyGroups,
            caseSensitive = state.caseSensitive,
        )
    }
}

/**
 * A rule as written in a rule set file: only the keys present in the file are set, so a rule set extending another one
 * can override a single property of a rule (`enabled: false`) without repeating the rest, see [applyTo].
 */
data class RulePatch(
    val id: String,
    val name: String? = null,
    val pattern: String? = null,
    val type: RuleType? = null,
    val background: Color? = null,
    val foreground: Color? = null,
    val action: RuleAction? = null,
    val enabled: Boolean? = null,
    val icon: String? = null,
) {

    /**
     * This patch with the properties set in [other] on top.
     */
    fun mergedWith(other: RulePatch) = RulePatch(
        id = id,
        name = other.name ?: name,
        pattern = other.pattern ?: pattern,
        type = other.type ?: type,
        background = other.background ?: background,
        foreground = other.foreground ?: foreground,
        action = other.action ?: action,
        enabled = other.enabled ?: enabled,
        icon = other.icon ?: icon,
    )

    /**
     * A new rule with the properties of this patch and the defaults for the rest.
     */
    fun toRule() = applyTo(Rule(id = id))

    /**
     * A copy of the [rule] with the properties of this patch on top.
     */
    fun applyTo(rule: Rule) = rule.copy(
        id = id,
        name = name ?: rule.name,
        pattern = pattern ?: rule.pattern,
        type = type ?: rule.type,
        background = background ?: rule.background,
        foreground = foreground ?: rule.foreground,
        action = action ?: rule.action,
        enabled = enabled ?: rule.enabled,
        icon = icon ?: rule.icon,
    )

    companion object {

        /**
         * A patch setting every property of the [rule], used to export the settings.
         */
        fun of(rule: Rule) = RulePatch(
            id = rule.effectiveId,
            name = rule.name,
            pattern = rule.pattern.takeIf { rule.usesPatterns },
            type = rule.type,
            background = rule.background,
            foreground = rule.foreground,
            action = rule.action,
            enabled = rule.enabled,
            icon = rule.icon,
        )
    }
}

/**
 * A set of folding rules stored in a YAML file, see [RuleSetYaml] for the format. It can [extends] other rule sets by id,
 * see [RuleSetRepository.resolve].
 *
 * The [id] identifies the rule set in `extends`: `builtin:docs` for a built-in preset, `user:gradle` for a preset of the
 * user directory, and the file path for the other files.
 */
data class RuleSet(
    val id: String,
    val layer: RuleSetLayer,
    val name: String,
    val description: String = "",
    val tags: List<String> = emptyList(),
    val extends: List<String> = emptyList(),
    val options: RuleSetOptions = RuleSetOptions(),
    val rules: List<RulePatch> = emptyList(),
    val path: Path? = null,
) {

    val origin
        get() = RuleOrigin(layer, name, path)
}
