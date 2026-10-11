package ski.chrzanow.foldableprojectview

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

object FoldableProjectViewConstants {

    const val COLOR_COLUMN_TEXT = "Aa"
    const val STORAGE_FILE = "FoldableProjectView.xml"
    const val PLUGIN_ID = "ski.chrzanow.foldableprojectview"

    /**
     * Help topic of the settings page; the platform routes the topics prefixed with the plugin id to the plugin's
     * `WebHelpProvider`, see `FoldableProjectViewHelpProvider`.
     */
    const val HELP_TOPIC = "$PLUGIN_ID.settings"

    /**
     * Documentation opened by the `?` button of the settings page.
     */
    const val DOCUMENTATION_URL = "https://github.com/hsz/intellij-foldable-projectview#usage"

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
