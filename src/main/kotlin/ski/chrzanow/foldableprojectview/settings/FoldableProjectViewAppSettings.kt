package ski.chrzanow.foldableprojectview.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.xmlb.annotations.OptionTag
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.USER_DIRECTORY
import java.nio.file.Path

/**
 * Application-level options of the plugin that are not defaults for the project settings, see [FoldableProjectDefaults]
 * for those.
 */
@Service(Service.Level.APP)
@State(
    name = "FoldableProjectViewAppSettings",
    storages = [Storage("FoldableProjectViewApp.xml", roamingType = RoamingType.DEFAULT)],
    category = SettingsCategory.UI,
)
class FoldableProjectViewAppSettings : SimplePersistentStateComponent<FoldableProjectViewAppSettings.State>(State()) {

    class State : BaseState() {

        /**
         * Directory of the user-level rule sets (`rules.yaml`, `presets/`), see `RuleSetRepository`.
         * Empty for the default `~/.config/foldableprojectview`; a leading `~` stands for the user home.
         */
        @get:OptionTag("USER_DIRECTORY")
        var userDirectory by string()
    }

    var userDirectory: String
        get() = state.userDirectory.orEmpty()
        set(value) {
            state.userDirectory = value.trim().takeIf(String::isNotEmpty)
        }

    /**
     * The absolute path of the [userDirectory].
     */
    val userDirectoryPath: Path
        get() = resolve(userDirectory)

    companion object {

        private val home
            get() = Path.of(System.getProperty("user.home"))

        /**
         * The default location of the user directory: `~/.config/foldableprojectview`.
         */
        val defaultUserDirectoryPath: Path
            get() = home.resolve(USER_DIRECTORY)

        /**
         * Resolves the [text] entered in the settings to a path: empty means the default, `~` means the user home.
         */
        fun resolve(text: String): Path {
            val trimmed = text.trim()
            return when {
                trimmed.isEmpty() -> defaultUserDirectoryPath
                trimmed == "~" -> home
                trimmed.startsWith("~/") || (SystemInfo.isWindows && trimmed.startsWith("~\\")) -> home.resolve(trimmed.substring(2))
                else -> Path.of(trimmed).toAbsolutePath()
            }
        }
    }
}

/**
 * Presents the path with the user home shortened to `~`.
 */
fun Path.toPresentableUserPath(): String {
    val home = Path.of(System.getProperty("user.home"))
    return when {
        startsWith(home) -> "~/" + home.relativize(this).toString().replace('\\', '/')
        else -> toString()
    }
}
