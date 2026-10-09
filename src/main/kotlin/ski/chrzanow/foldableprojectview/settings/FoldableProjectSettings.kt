package ski.chrzanow.foldableprojectview.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
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

    @get:OptionTag("RULES")
    override var rules by list<Rule>()

    override fun getState() = this

    override fun loadState(state: FoldableProjectSettings) = copyFrom(state)

    override fun noStateLoaded() {
        service<FoldableProjectDefaults>().state?.let(::copyFrom)
    }
}

/**
 * Applies [block] to the settings and notifies [FoldableProjectSettingsListener]s of the [project] about the change.
 */
fun FoldableProjectSettings.update(project: Project, block: FoldableProjectSettings.() -> Unit) {
    block()
    service<FoldableProjectDefaults>().store(this)
    project.messageBus
        .syncPublisher(FoldableProjectSettingsListener.TOPIC)
        .settingsChanged(this)
}
