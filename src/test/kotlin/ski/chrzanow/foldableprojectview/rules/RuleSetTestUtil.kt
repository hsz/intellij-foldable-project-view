package ski.chrzanow.foldableprojectview.rules

import com.intellij.openapi.components.service
import com.intellij.openapi.util.io.FileUtil
import ski.chrzanow.foldableprojectview.settings.FoldableProjectViewAppSettings
import java.nio.file.Path

/**
 * Points the user directory of the rule sets to a fresh temporary directory, so the tests neither read the real
 * `~/.config/foldableprojectview` of the developer nor write to it. Returns the directory and a function restoring the setting.
 */
fun isolateUserDirectory(): Pair<Path, () -> Unit> {
    val appSettings = service<FoldableProjectViewAppSettings>()
    val original = appSettings.userDirectory
    val directory = FileUtil.createTempDirectory("foldableprojectview", null).toPath()
    appSettings.userDirectory = directory.toString()
    return directory to {
        appSettings.userDirectory = original
        FileUtil.delete(directory.toFile())
    }
}

/**
 * Runs [block] with the user directory of the rule sets isolated, see [isolateUserDirectory].
 */
fun <T> withIsolatedUserDirectory(block: (Path) -> T): T {
    val (directory, restore) = isolateUserDirectory()
    try {
        return block(directory)
    } finally {
        restore()
    }
}
