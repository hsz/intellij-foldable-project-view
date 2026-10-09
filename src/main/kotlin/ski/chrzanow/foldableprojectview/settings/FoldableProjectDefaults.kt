package ski.chrzanow.foldableprojectview.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Application-level copy of the last saved settings. Being roamable, it is synchronized by the IDE Settings Sync
 * and used to initialize the settings of projects that have no settings stored yet.
 */
@Service(Service.Level.APP)
@State(
    name = "FoldableProjectDefaults",
    storages = [Storage("FoldableProjectViewDefaults.xml", roamingType = RoamingType.DEFAULT)],
    category = SettingsCategory.UI,
)
class FoldableProjectDefaults : PersistentStateComponent<FoldableProjectSettings> {

    private var state: FoldableProjectSettings? = null

    val isStored get() = state != null

    override fun getState() = state

    override fun loadState(state: FoldableProjectSettings) {
        this.state = state
    }

    fun store(settings: FoldableProjectSettings) {
        state = FoldableProjectSettings().also { it.copyFrom(settings) }
    }
}
