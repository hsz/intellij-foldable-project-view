package ski.chrzanow.foldableprojectview

import com.intellij.ide.util.PropertiesComponent
import ski.chrzanow.foldableprojectview.FoldableProjectViewBundle.PROJECT_VIEW_FOLDING_ENABLED

fun isFoldingEnabled() = PropertiesComponent.getInstance().getBoolean(PROJECT_VIEW_FOLDING_ENABLED)

fun setFoldingEnabled(state: Boolean) {
    PropertiesComponent.getInstance().setValue(PROJECT_VIEW_FOLDING_ENABLED, state)
}
