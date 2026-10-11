package ski.chrzanow.foldableprojectview.sponsor

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.SPONSOR_MIN_SESSIONS
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.SPONSOR_MIN_USAGE
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.SPONSOR_SNOOZE
import kotlin.time.Duration.Companion.milliseconds

/**
 * Application-level, non-roaming record of how long and how often the plugin has been used,
 * and of how the user reacted to the sponsorship notification, see [SponsorNotificationActivity].
 *
 * The notification is shown only once the user has demonstrably gotten value from the plugin: after at least
 * [SPONSOR_MIN_SESSIONS] project sessions with folding enabled spread over at least [SPONSOR_MIN_USAGE].
 * Every time it is shown, it is snoozed for [SPONSOR_SNOOZE], so closing the balloon is the same as `Remind me later`.
 */
@Service(Service.Level.APP)
@State(
    name = "FoldableProjectViewSponsor",
    storages = [Storage("FoldableProjectViewSponsor.xml", roamingType = RoamingType.DISABLED)],
    category = SettingsCategory.PLUGINS,
)
class SponsorNotificationState : PersistentStateComponent<SponsorNotificationState.State> {

    class State : BaseState() {
        /** Epoch milliseconds of the first project session with folding enabled; `0` if none yet. */
        var firstUsedAt by property(0L)

        /** Number of project sessions started with folding enabled. */
        var sessions by property(0)

        /** Epoch milliseconds until which the notification is snoozed. */
        var snoozedUntil by property(0L)

        /** Whether the user asked to never show the notification again. */
        var dismissed by property(false)
    }

    private var state = State()

    override fun getState() = state

    override fun loadState(state: State) {
        this.state = state
    }

    /**
     * Records a project session with folding enabled started at [now].
     */
    fun recordSession(now: Long = System.currentTimeMillis()) {
        if (state.firstUsedAt == 0L) {
            state.firstUsedAt = now
        }
        state.sessions++
    }

    /**
     * Whether the notification should be shown at [now]: not dismissed, not snoozed, and the usage thresholds are met.
     */
    fun shouldShow(now: Long = System.currentTimeMillis()) = with(state) {
        !dismissed &&
            now >= snoozedUntil &&
            firstUsedAt != 0L &&
            sessions >= SPONSOR_MIN_SESSIONS &&
            (now - firstUsedAt).milliseconds >= SPONSOR_MIN_USAGE
    }

    /**
     * Snoozes the notification for [SPONSOR_SNOOZE] from [now].
     */
    fun snooze(now: Long = System.currentTimeMillis()) {
        state.snoozedUntil = now + SPONSOR_SNOOZE.inWholeMilliseconds
    }

    /**
     * Makes sure the notification is never shown again.
     */
    fun dismiss() {
        state.dismissed = true
    }

    companion object {

        fun getInstance() = service<SponsorNotificationState>()
    }
}
