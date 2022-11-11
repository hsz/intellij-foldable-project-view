package ski.chrzanow.foldableprojectview.settings

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.annotations.OptionTag
import ski.chrzanow.foldableprojectview.settings.FoldableProjectState.Rule

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

    @get:OptionTag("HIDE_IGNORED_FILES")
    override var foldIgnoredFiles by property(true)

    @get:OptionTag("RULES")
    override var rules by list<Rule>()

    override fun getState() = this

    override fun loadState(state: FoldableProjectSettings) = copyFrom(state)
}
