package ski.chrzanow.foldableprojectview

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

object FoldableProjectViewConstants {

    const val COLOR_COLUMN_TEXT = "Aa"
    const val STORAGE_FILE = "FoldableProjectView.xml"
    const val PLUGIN_ID = "ski.chrzanow.foldableprojectview"

    /**
     * Name of the rule set file looked up in the project root and in the module content roots, see `RuleSetRepository`.
     */
    const val RULE_SET_FILE = ".foldableprojectview.yaml"

    /**
     * Default directory of the user-level rule sets: the `rules.yaml` applied to every project and the YAML files of the
     * `presets` subdirectory offered next to the built-in presets, relative to the user home.
     * See `FoldableProjectViewAppSettings.userDirectory`.
     */
    const val USER_DIRECTORY = ".config/foldableprojectview"
    const val USER_RULES_FILE = "rules.yaml"
    const val USER_PRESETS_DIRECTORY = "presets"

    /**
     * Classpath directory of the built-in presets, one YAML rule set per file, listed in the [BUILTIN_PRESETS_INDEX].
     */
    const val BUILTIN_PRESETS_PATH = "/presets"
    const val BUILTIN_PRESETS_INDEX = "$BUILTIN_PRESETS_PATH/index.txt"

    /**
     * JSON schema of the rule set files, attached to them by the IDE for completion and validation.
     */
    const val RULE_SET_SCHEMA_PATH = "/schemas/foldableprojectview.schema.json"

    /**
     * Prefixes of the rule set ids used by `extends`, see `RuleSetRepository`.
     */
    const val BUILTIN_PRESET_PREFIX = "builtin:"
    const val USER_PRESET_PREFIX = "user:"

    /**
     * Help topic of the settings page; the platform routes the topics prefixed with the plugin id to the plugin's
     * `WebHelpProvider`, see `FoldableProjectViewHelpProvider`.
     */
    const val HELP_TOPIC = "$PLUGIN_ID.settings"

    /**
     * Documentation opened by the `?` button of the settings page.
     */
    const val DOCUMENTATION_URL = "https://github.com/hsz/intellij-foldable-project-view#usage"

    /**
     * Maximum number of the project content files visited to compute the number of elements matched by a rule
     * in the settings, see `FoldableRulesEditor`.
     */
    const val MATCH_COUNT_LIMIT = 50_000

    /**
     * Maximum number of the matched elements listed in the tooltip of the match counter, see `FoldableRulesEditor`.
     */
    const val MATCH_PREVIEW_LIMIT = 10

    /**
     * Delay used to coalesce bursts of events (keystrokes in the settings, VCS status or file changes) into a single
     * Project View refresh, see `Debouncer`.
     */
    val REFRESH_DELAY: Duration = 250.milliseconds

    const val SPONSOR_GITHUB_URL = "https://github.com/sponsors/hsz"
    const val SPONSOR_KOFI_URL = "https://ko-fi.com/hszanowski"
    const val SPONSOR_PAYPAL_URL = "https://paypal.me/hsz"

    /**
     * Minimum number of project sessions with folding enabled, and the minimum time since the first of them,
     * before the sponsorship notification is shown; it is snoozed for [SPONSOR_SNOOZE] every time it is shown.
     * See `SponsorNotificationState`.
     */
    const val SPONSOR_MIN_SESSIONS = 10
    val SPONSOR_MIN_USAGE: Duration = 14.days
    val SPONSOR_SNOOZE: Duration = 90.days
}
