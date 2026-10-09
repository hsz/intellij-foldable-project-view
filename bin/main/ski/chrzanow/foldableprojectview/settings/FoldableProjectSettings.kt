package ski.chrzanow.foldableprojectview.settings

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.annotations.OptionTag
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants

@Service(Service.Level.PROJECT)
@State(name = "FoldableProjectSettings", storages = [Storage(FoldableProjectViewConstants.STORAGE_FILE)])
class FoldableProjectSettings : FoldableProjectState, BaseState(), PersistentStateComponent<FoldableProjectSettings> {

    @get:OptionTag("FOLDING_ENABLED")
    override var foldingEnabled by property(true)

    @get:OptionTag("MATCH_DIRECTORIES")
    override var matchDirectories by property(true)

    @get:OptionTag("HIDE_EMPTY_GROUPS")
    override var hideEmptyGroups by property(true)

    @get:OptionTag("HIDE_ALL_GROUPS")
    override var hideAllGroups by property(false)

    @get:OptionTag("CASE_SENSITIVE")
    override var caseSensitive by property(true)

    @get:OptionTag("HIDE_IGNORED_FILES")
    override var foldIgnoredFiles by property(true)

    @get:OptionTag("LIMIT_TO_GO_WORK_MODULES")
    override var limitToGoWorkModules by property(false)

    @get:OptionTag("RULES")
    override var rules by list<Rule>()

    override fun getState() = this

    override fun loadState(state: FoldableProjectSettings) = copyFrom(state)
}
