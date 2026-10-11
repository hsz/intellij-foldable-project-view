package ski.chrzanow.foldableprojectview.actionSystem

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleOptionAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import ski.chrzanow.foldableprojectview.settings.FoldableProjectSettings
import ski.chrzanow.foldableprojectview.settings.foldIgnoredFiles
import ski.chrzanow.foldableprojectview.settings.update
import java.util.function.Function
import kotlin.reflect.KMutableProperty1

/**
 * Toggles a boolean [FoldableProjectSettings] property of the current project and refreshes the Project View.
 */
abstract class FoldableToggleOptionAction(property: KMutableProperty1<FoldableProjectSettings, Boolean>) : DumbAware, ToggleOptionAction(
    Function { event: AnActionEvent -> event.project?.let { SettingsOption(it, property) } }
) {

    override fun update(event: AnActionEvent) {
        super.update(event)
        if (event.project == null) {
            event.presentation.isEnabledAndVisible = false
        }
    }

    private class SettingsOption(
        private val project: Project,
        private val property: KMutableProperty1<FoldableProjectSettings, Boolean>,
    ) : Option {

        private val settings = project.service<FoldableProjectSettings>()

        override fun isSelected() = property.get(settings)

        override fun setSelected(selected: Boolean) {
            if (selected != isSelected) {
                settings.update(project) {
                    property.set(this, selected)
                }
            }
        }
    }
}

class EnableFoldingAction : FoldableToggleOptionAction(FoldableProjectSettings::foldingEnabled)

/**
 * Toggles the built-in `Ignored files` rule, see [foldIgnoredFiles].
 */
class FoldIgnoredFilesAction : FoldableToggleOptionAction(FoldableProjectSettings::foldIgnoredFiles)
