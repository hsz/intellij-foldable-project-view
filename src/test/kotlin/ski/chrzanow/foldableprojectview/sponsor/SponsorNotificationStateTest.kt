package ski.chrzanow.foldableprojectview.sponsor

import com.intellij.configurationStore.deserialize
import com.intellij.configurationStore.serialize
import com.intellij.testFramework.LightPlatformTestCase
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.SPONSOR_MIN_SESSIONS
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.SPONSOR_MIN_USAGE
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.SPONSOR_SNOOZE
import kotlin.time.Duration.Companion.days

class SponsorNotificationStateTest : LightPlatformTestCase() {

    private val start = 1_700_000_000_000L
    private val afterUsage = start + SPONSOR_MIN_USAGE.inWholeMilliseconds

    /**
     * Records [SPONSOR_MIN_SESSIONS] sessions starting at [start].
     */
    private fun usedState() = SponsorNotificationState().apply {
        repeat(SPONSOR_MIN_SESSIONS) { recordSession(start) }
    }

    fun `test not shown before any session`() {
        assertFalse(SponsorNotificationState().shouldShow(afterUsage))
    }

    fun `test not shown before enough sessions`() {
        val state = SponsorNotificationState().apply {
            repeat(SPONSOR_MIN_SESSIONS - 1) { recordSession(start) }
        }

        assertFalse(state.shouldShow(afterUsage))
    }

    fun `test not shown before enough usage time`() {
        assertFalse(usedState().shouldShow(afterUsage - 1))
    }

    fun `test shown once sessions and usage time thresholds are met`() {
        assertTrue(usedState().shouldShow(afterUsage))
    }

    fun `test snoozed until the snooze period passes`() {
        val state = usedState().apply { snooze(afterUsage) }
        val snoozedUntil = afterUsage + SPONSOR_SNOOZE.inWholeMilliseconds

        assertFalse(state.shouldShow(afterUsage))
        assertFalse(state.shouldShow(snoozedUntil - 1))
        assertTrue(state.shouldShow(snoozedUntil))
    }

    fun `test never shown once dismissed`() {
        val state = usedState().apply { dismiss() }

        assertFalse(state.shouldShow(afterUsage))
        assertFalse(state.shouldShow(afterUsage + 365.days.inWholeMilliseconds))
    }

    fun `test first session time is kept by the following sessions`() {
        val state = SponsorNotificationState().apply {
            recordSession(start)
            recordSession(afterUsage)
        }

        assertEquals(start, state.state.firstUsedAt)
        assertEquals(2, state.state.sessions)
    }

    fun `test state survives serialization`() {
        val state = usedState().apply {
            snooze(afterUsage)
            dismiss()
        }

        val element = serialize(state.state)!!
        val restored = SponsorNotificationState().apply { loadState(element.deserialize(SponsorNotificationState.State::class.java)) }

        assertEquals(start, restored.state.firstUsedAt)
        assertEquals(SPONSOR_MIN_SESSIONS, restored.state.sessions)
        assertEquals(afterUsage + SPONSOR_SNOOZE.inWholeMilliseconds, restored.state.snoozedUntil)
        assertTrue(restored.state.dismissed)
    }
}
