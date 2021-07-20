package ski.chrzanow.foldableprojectview.options

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.observable.properties.AtomicLazyProperty

@State(name = "FoldableProjectSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class FoldableProjectConfiguration : PersistentStateComponent<FoldableProjectConfiguration.State> {

    internal val patternsProperty = AtomicLazyProperty { listOf<String>() }

    internal var patterns by patternsProperty

    override fun getState() = State(patterns)

    override fun loadState(state: State) {
        patterns = state.patterns
    }

    data class State(var patterns: List<String> = emptyList())
}
