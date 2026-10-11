package ski.chrzanow.foldableprojectview

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import kotlin.time.Duration.Companion.milliseconds

class DebouncerTest : BasePlatformTestCase() {

    private fun waitFor(condition: () -> Boolean) =
        PlatformTestUtil.waitWithEventsDispatching("Condition not met", condition, 5)

    /**
     * Dispatches the events for a while to make sure no delayed action is pending.
     */
    private fun settle() {
        val until = System.currentTimeMillis() + 300
        while (System.currentTimeMillis() < until) {
            UIUtil.dispatchAllInvocationEvents()
            Thread.sleep(10)
        }
    }

    fun `test burst of requests runs the action once after the delay`() {
        var runs = 0
        val debouncer = Debouncer.create(project, testRootDisposable, 50.milliseconds) { runs++ }

        repeat(5) { debouncer.request() }
        assertEquals(0, runs)

        waitFor { runs == 1 }
        settle()
        assertEquals(1, runs)
    }

    fun `test requests after the action ran schedule it again`() {
        var runs = 0
        val debouncer = Debouncer.create(project, testRootDisposable, 50.milliseconds) { runs++ }

        debouncer.request()
        waitFor { runs == 1 }

        debouncer.request()
        debouncer.request()
        waitFor { runs == 2 }
        settle()
        assertEquals(2, runs)
    }

    fun `test nothing runs after the parent is disposed`() {
        var runs = 0
        val parent = Disposer.newDisposable(testRootDisposable, "debouncer")
        val debouncer = Debouncer.create(project, parent, 50.milliseconds) { runs++ }

        debouncer.request()
        Disposer.dispose(parent)
        settle()

        debouncer.request()
        settle()
        assertEquals(0, runs)
    }
}
