package ski.chrzanow.foldableprojectview

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

object FoldableProjectViewConstants {

    const val COLOR_COLUMN_TEXT = "Aa"
    const val STORAGE_FILE = "FoldableProjectView.xml"

    /**
     * Maximum number of the project content files visited to compute the number of elements matched by a rule
     * in the settings, see `FoldableRulesEditor`.
     */
    const val MATCH_COUNT_LIMIT = 50_000

    /**
     * Delay used to coalesce bursts of events (keystrokes in the settings, VCS status or file changes) into a single
     * Project View refresh, see `Debouncer`.
     */
    val REFRESH_DELAY: Duration = 250.milliseconds
}
