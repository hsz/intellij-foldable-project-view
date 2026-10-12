package ski.chrzanow.foldableprojectview.rules

import com.intellij.ui.JBColor
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.RULE_SET_FILE
import ski.chrzanow.foldableprojectview.psi.search.GlobPatterns
import ski.chrzanow.foldableprojectview.settings.FoldableProjectState
import ski.chrzanow.foldableprojectview.settings.Rule
import ski.chrzanow.foldableprojectview.settings.RuleAction
import ski.chrzanow.foldableprojectview.settings.RuleColor
import ski.chrzanow.foldableprojectview.settings.RuleColors
import ski.chrzanow.foldableprojectview.settings.RuleType
import java.awt.Color
import java.nio.file.Path

/**
 * Thrown when a rule set file cannot be parsed, with a message describing the problem to the user.
 */
class RuleSetFormatException(message: String) : RuntimeException(message)

/**
 * The YAML format of the rule set files (`.foldableprojectview.yaml`, the presets, the user `rules.yaml`):
 *
 * ```yaml
 * name: Kotlin Gradle project     # optional for project files, required for presets
 * description: Folds the build noise
 * tags: [kotlin, gradle]          # presets only
 * extends: [builtin:docs]         # other rule sets to include, by id
 * options:                        # optional; only the keys present override the IDE settings
 *   matchDirectories: true
 * rules:
 *   - id: build                   # stable identity, derived from the name if missing
 *     name: Build
 *     patterns: [gradle/, gradlew*, "*.gradle.kts"]
 *     background: Blue            # palette color name or #RRGGBB
 *     action: group               # group | hide
 *   - id: local
 *     name: Local
 *     patternsFiles: [.foldignore]
 *   - type: ignoredFiles          # the built-in rule
 *     enabled: false
 * ```
 *
 * Every rule key is optional, so a rule set extending another one can override a single property of a rule by id.
 * The file is parsed with the safe YAML loader, so no types other than maps, lists, and scalars are instantiated.
 */
object RuleSetYaml {

    private const val NAME = "name"
    private const val DESCRIPTION = "description"
    private const val TAGS = "tags"
    private const val EXTENDS = "extends"
    private const val OPTIONS = "options"
    private const val RULES = "rules"

    private const val ID = "id"
    private const val PATTERNS = "patterns"
    private const val PATTERNS_FILES = "patternsFiles"
    private const val TYPE = "type"
    private const val BACKGROUND = "background"
    private const val FOREGROUND = "foreground"
    private const val ACTION = "action"
    private const val ENABLED = "enabled"

    private const val FOLDING_ENABLED = "foldingEnabled"
    private const val MATCH_DIRECTORIES = "matchDirectories"
    private const val FOLD_NESTED_DIRECTORIES = "foldNestedDirectories"
    private const val HIDE_EMPTY_GROUPS = "hideEmptyGroups"
    private const val CASE_SENSITIVE = "caseSensitive"

    private const val TYPE_PATTERNS = "patterns"
    private const val TYPE_PATTERNS_FILES = "patternsFiles"
    private const val TYPE_IGNORED_FILES = "ignoredFiles"

    private const val ACTION_GROUP = "group"
    private const val ACTION_HIDE = "hide"

    private val yaml
        get() = Yaml(SafeConstructor(LoaderOptions()))

    private val scalarYaml
        get() = Yaml(DumperOptions().apply { defaultScalarStyle = DumperOptions.ScalarStyle.PLAIN })

    /**
     * Parses the [text] of a rule set file. The [id] and [path] identify the parsed set, see [RuleSet.id];
     * the [defaultName] is used when the file has no `name`.
     */
    @Throws(RuleSetFormatException::class)
    fun parse(text: String, id: String, layer: RuleSetLayer, path: Path? = null, defaultName: String = id): RuleSet {
        val root = runCatching { yaml.load<Any?>(text) }
            .getOrElse { throw RuleSetFormatException(it.message ?: it.toString()) }
            ?: return RuleSet(id, layer, defaultName, path = path)
        val map = root.asMap("the document")

        val rules = (map[RULES] ?: emptyList<Any?>()).asList(RULES).mapIndexed { index, item ->
            parseRule(item.asMap("$RULES[$index]"), index)
        }
        rules.groupBy(RulePatch::id).filterValues { it.size > 1 }.keys.firstOrNull()?.let {
            throw RuleSetFormatException("Duplicate rule id: $it")
        }

        return RuleSet(
            id = id,
            layer = layer,
            name = map[NAME]?.asString(NAME)?.takeIf(String::isNotBlank) ?: defaultName,
            description = map[DESCRIPTION]?.asString(DESCRIPTION).orEmpty(),
            tags = map[TAGS]?.asStringList(TAGS).orEmpty(),
            extends = map[EXTENDS]?.asStringList(EXTENDS).orEmpty(),
            options = map[OPTIONS]?.asMap(OPTIONS)?.let(::parseOptions) ?: RuleSetOptions(),
            rules = rules,
            path = path,
        )
    }

    private fun parseOptions(map: Map<String, Any?>) = RuleSetOptions(
        foldingEnabled = map[FOLDING_ENABLED]?.asBoolean(FOLDING_ENABLED),
        matchDirectories = map[MATCH_DIRECTORIES]?.asBoolean(MATCH_DIRECTORIES),
        foldNestedDirectories = map[FOLD_NESTED_DIRECTORIES]?.asBoolean(FOLD_NESTED_DIRECTORIES),
        hideEmptyGroups = map[HIDE_EMPTY_GROUPS]?.asBoolean(HIDE_EMPTY_GROUPS),
        caseSensitive = map[CASE_SENSITIVE]?.asBoolean(CASE_SENSITIVE),
    ).also {
        (map.keys - setOf(FOLDING_ENABLED, MATCH_DIRECTORIES, FOLD_NESTED_DIRECTORIES, HIDE_EMPTY_GROUPS, CASE_SENSITIVE))
            .firstOrNull()
            ?.let { throw RuleSetFormatException("Unknown option: $it") }
    }

    private fun parseRule(map: Map<String, Any?>, index: Int): RulePatch {
        val name = map[NAME]?.asString(NAME)
        val patterns = map[PATTERNS]?.asStringList(PATTERNS)
        val patternsFiles = map[PATTERNS_FILES]?.asStringList(PATTERNS_FILES)
        val type = map[TYPE]?.asString(TYPE)?.let {
            when (it) {
                TYPE_PATTERNS -> RuleType.PATTERN
                TYPE_PATTERNS_FILES -> RuleType.PATTERNS_FILE
                TYPE_IGNORED_FILES -> RuleType.IGNORED_FILES
                else -> throw RuleSetFormatException("Unknown rule type: $it")
            }
        } ?: when {
            patternsFiles != null && patterns == null -> RuleType.PATTERNS_FILE
            patterns != null -> RuleType.PATTERN
            else -> null
        }
        if (patterns != null && patternsFiles != null) {
            throw RuleSetFormatException("A rule lists either patterns or patternsFiles, not both")
        }

        val id = map[ID]?.asString(ID)?.takeIf(String::isNotBlank)
            ?: when (type) {
                RuleType.IGNORED_FILES -> Rule.IGNORED_FILES_ID
                else -> name?.let(Rule::slug)?.takeIf(String::isNotBlank)
            }
            ?: throw RuleSetFormatException("Rule #${index + 1} has neither an id nor a name")

        return RulePatch(
            id = id,
            name = name,
            pattern = (patternsFiles ?: patterns)?.joinToString(" ", transform = ::quotePattern),
            type = type,
            background = map[BACKGROUND]?.asString(BACKGROUND)?.let { parseColor(it, RuleColors.backgrounds) },
            foreground = map[FOREGROUND]?.asString(FOREGROUND)?.let { parseColor(it, RuleColors.foregrounds) },
            action = map[ACTION]?.asString(ACTION)?.let {
                when (it) {
                    ACTION_GROUP -> RuleAction.GROUP
                    ACTION_HIDE -> RuleAction.HIDE
                    else -> throw RuleSetFormatException("Unknown rule action: $it")
                }
            },
            enabled = map[ENABLED]?.asBoolean(ENABLED),
        )
    }

    /**
     * Resolves a color written as a palette name (`Blue`, case-insensitive) or a hex value (`#RRGGBB`, `#AARRGGBB`).
     */
    @Suppress("UseJBColor")
    fun parseColor(text: String, palette: List<RuleColor>): Color {
        palette.firstOrNull { it.id.equals(text.trim(), ignoreCase = true) }?.let { return it }

        val hex = text.trim().removePrefix("#")
        val argb = when (hex.length) {
            6 -> hex.toLongOrNull(16)?.let { it or 0xFF000000 }
            8 -> hex.toLongOrNull(16)
            else -> null
        } ?: throw RuleSetFormatException("Unknown color: $text")
        return Color(argb.toInt(), true).let { JBColor(it, it) }
    }

    /**
     * Writes a color the way [parseColor] reads it.
     */
    fun formatColor(color: Color) = when (color) {
        is RuleColor -> color.id
        else -> when (color.alpha) {
            0xFF -> "#%06X".format(color.rgb and 0xFFFFFF)
            else -> "#%08X".format(color.rgb)
        }
    }

    /**
     * Serializes the [ruleSet] to YAML, in the key order documented above. The rules are written as the patches are,
     * so the properties that are not set are left out.
     */
    fun write(ruleSet: RuleSet): String = buildString {
        appendLine("# Foldable Project View rule set, see https://github.com/hsz/intellij-foldable-project-view#rule-set-files")
        appendLine("$NAME: ${scalar(ruleSet.name)}")
        if (ruleSet.description.isNotBlank()) {
            appendLine("$DESCRIPTION: ${scalar(ruleSet.description)}")
        }
        if (ruleSet.tags.isNotEmpty()) {
            appendLine("$TAGS: ${flowList(ruleSet.tags)}")
        }
        if (ruleSet.extends.isNotEmpty()) {
            appendLine("$EXTENDS: ${flowList(ruleSet.extends)}")
        }
        with(ruleSet.options) {
            if (!isEmpty) {
                appendLine("$OPTIONS:")
                foldingEnabled?.let { appendLine("  $FOLDING_ENABLED: $it") }
                matchDirectories?.let { appendLine("  $MATCH_DIRECTORIES: $it") }
                foldNestedDirectories?.let { appendLine("  $FOLD_NESTED_DIRECTORIES: $it") }
                hideEmptyGroups?.let { appendLine("  $HIDE_EMPTY_GROUPS: $it") }
                caseSensitive?.let { appendLine("  $CASE_SENSITIVE: $it") }
            }
        }
        if (ruleSet.rules.isEmpty()) {
            appendLine("$RULES: []")
        } else {
            appendLine("$RULES:")
            ruleSet.rules.forEach { rule ->
                appendLine("  - $ID: ${scalar(rule.id)}")
                rule.name?.let { appendLine("    $NAME: ${scalar(it)}") }
                when (rule.type) {
                    RuleType.IGNORED_FILES -> appendLine("    $TYPE: $TYPE_IGNORED_FILES")
                    RuleType.PATTERNS_FILE -> appendLine("    $PATTERNS_FILES: ${flowList(Rule.patternsFilePaths(rule.pattern.orEmpty()))}")
                    RuleType.PATTERN -> appendLine("    $PATTERNS: ${flowList(GlobPatterns.split(rule.pattern.orEmpty()))}")
                    null -> rule.pattern?.let { appendLine("    $PATTERNS: ${flowList(GlobPatterns.split(it))}") }
                }
                rule.background?.let { appendLine("    $BACKGROUND: ${scalar(formatColor(it))}") }
                rule.foreground?.let { appendLine("    $FOREGROUND: ${scalar(formatColor(it))}") }
                rule.action?.let { appendLine("    $ACTION: ${if (it == RuleAction.HIDE) ACTION_HIDE else ACTION_GROUP}") }
                rule.enabled?.let { appendLine("    $ENABLED: $it") }
            }
        }
    }

    /**
     * Creates the rule set to export the [state] as: the options and the rules stored in the IDE settings, see [RuleSet].
     * The rules that already come from files are left out, as they are provided by their own files.
     */
    fun export(state: FoldableProjectState, name: String, description: String = "") = RuleSet(
        id = RULE_SET_FILE,
        layer = RuleSetLayer.PROJECT,
        name = name,
        description = description,
        options = RuleSetOptions.of(state),
        rules = state.rules.filterNot(Rule::isFromFile).map(RulePatch::of),
    )

    /**
     * Wraps a pattern containing separators in double quotes, so the patterns can be joined with spaces, see [GlobPatterns.split].
     */
    private fun quotePattern(pattern: String) = when {
        pattern.any { it.isWhitespace() || it == ',' } -> "\"$pattern\""
        else -> pattern
    }

    private fun scalar(value: String) = scalarYaml.dump(value).trimEnd().removeSuffix("...").trimEnd()

    private fun flowList(values: List<String>) = values.joinToString(", ", "[", "]", transform = ::scalar)

    private fun Any?.asMap(what: String): Map<String, Any?> = (this as? Map<*, *>)
        ?.entries
        ?.associate { (key, value) -> key.toString() to value }
        ?: throw RuleSetFormatException("Expected a mapping for $what")

    private fun Any?.asList(what: String): List<Any?> = this as? List<*>
        ?: throw RuleSetFormatException("Expected a list for $what")

    private fun Any?.asString(what: String): String = when (this) {
        is String -> this
        is Number, is Boolean -> toString()
        else -> throw RuleSetFormatException("Expected a text for $what")
    }

    private fun Any?.asBoolean(what: String): Boolean = this as? Boolean
        ?: throw RuleSetFormatException("Expected true or false for $what")

    /**
     * A list of texts, or a single text with space-separated values (`patterns: "*.md *.txt"`).
     */
    private fun Any?.asStringList(what: String): List<String> = when (this) {
        is List<*> -> map { it.asString(what) }
        is String -> GlobPatterns.split(this)
        else -> throw RuleSetFormatException("Expected a list for $what")
    }
}
