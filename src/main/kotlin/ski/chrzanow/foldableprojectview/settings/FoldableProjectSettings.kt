package ski.chrzanow.foldableprojectview.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.util.xmlb.annotations.OptionTag

@State(name = "FoldableProjectSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class FoldableProjectSettings : BaseState(), PersistentStateComponent<FoldableProjectSettings> {

    @get:OptionTag("FOLDING_ENABLED")
    var foldingEnabled by property(true)

    @get:OptionTag("FOLDING_ENABLED")
    var patterns by string("")

    override fun getState() = this

    override fun loadState(state: FoldableProjectSettings) {
        copyFrom(state)
    }
}
