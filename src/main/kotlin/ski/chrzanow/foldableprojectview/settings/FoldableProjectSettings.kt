package ski.chrzanow.foldableprojectview.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.util.xmlb.annotations.OptionTag

@State(name = "FoldableProjectSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class FoldableProjectSettings : FoldableProjectState, BaseState(), PersistentStateComponent<FoldableProjectSettings> {

    @get:OptionTag("FOLDING_ENABLED")
    override var foldingEnabled by property(true)

    @get:OptionTag("FOLD_DIRECTORIES")
    override var foldDirectories by property(true)

    @get:OptionTag("HIDE_EMPTY_GROUPS")
    override var hideEmptyGroups by property(true)

    @get:OptionTag("HIDE_ALL_GROUPS")
    override var hideAllGroups by property(false)

    @get:OptionTag("CASE_INSENSITIVE")
    override var caseInsensitive by property(true)

    @get:OptionTag("PATTERNS")
    override var patterns by string("")

    override fun getState() = this

    override fun loadState(state: FoldableProjectSettings) = copyFrom(state)
}
