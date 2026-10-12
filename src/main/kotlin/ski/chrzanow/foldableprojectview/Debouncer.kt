package ski.chrzanow.foldableprojectview

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration

/**
 * Coalesces bursts of [request]s into a single run of the [action] on the EDT, [delay] after the last request.
 * The action runs in the modality state of the last request, so it reaches the components of a modal dialog
 * (e.g., the Settings dialog) the request was made from. Nothing runs once the [scope] is canceled or the debouncer
 * is [cancel]led.
 */
@OptIn(FlowPreview::class)
class Debouncer(scope: CoroutineScope, delay: Duration, private val action: () -> Unit) {

    private val requests = Channel<ModalityState>(Channel.CONFLATED)

    private val job: Job = scope.launch {
        requests.receiveAsFlow()
            .debounce(delay)
            .collect { modality ->
                withContext(Dispatchers.EDT + modality.asContextElement()) {
                    action()
                }
            }
    }

    fun request() {
        requests.trySend(ModalityState.defaultModalityState())
    }

    /**
     * Stops processing the requests; the pending and any further requests are dropped.
     */
    fun cancel() {
        job.cancel()
        requests.close()
    }

    companion object {

        /**
         * Creates a debouncer living until the [parentDisposable] is disposed, e.g., for the UI components of the [project].
         */
        fun create(project: Project, parentDisposable: Disposable, delay: Duration, action: () -> Unit): Debouncer {
            val debouncer = Debouncer(project.service<DebouncerScope>().coroutineScope, delay, action)
            Disposer.register(parentDisposable) { debouncer.cancel() }
            return debouncer
        }
    }
}

/**
 * Provides the coroutine scope for the debouncers that are not owned by a service, see [Debouncer.create].
 */
@Service(Service.Level.PROJECT)
internal class DebouncerScope(val coroutineScope: CoroutineScope)
