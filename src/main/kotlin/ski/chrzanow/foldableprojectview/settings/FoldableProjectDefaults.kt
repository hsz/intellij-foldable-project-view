package ski.chrzanow.foldableprojectview.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Application-level settings used to initialize the settings of projects that have no settings stored yet.
 * Being roamable, they are synchronized by the IDE Settings Sync.
 *
 * The defaults are changed explicitly only – with the `Use as Defaults for New Projects` button of the project settings,
 * or by editing them in the settings for new projects.
 */
@Service(Service.Level.APP)
@State(
    name = "FoldableProjectDefaults",
    storages = [Storage("FoldableProjectViewDefaults.xml", roamingType = RoamingType.DEFAULT)],
    category = SettingsCategory.UI,
)
class FoldableProjectDefaults : PersistentStateComponent<FoldableProjectSettings> {

    private var state: FoldableProjectSettings? = null

    override fun getState() = state

    override fun loadState(state: FoldableProjectSettings) {
        this.state = state
    }

    /**
     * Stores a copy of [settings] as the defaults for new projects.
     */
    fun store(settings: FoldableProjectSettings) {
        state = FoldableProjectSettings().also { it.copyFrom(settings) }
    }

    /**
     * Creates the settings a project without stored settings starts with.
     */
    fun createSettings() = FoldableProjectSettings().apply { loadDefaults() }
}
