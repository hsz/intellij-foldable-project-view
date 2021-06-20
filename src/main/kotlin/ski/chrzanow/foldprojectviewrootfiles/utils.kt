package ski.chrzanow.foldprojectviewrootfiles

import com.intellij.ide.util.PropertiesComponent
import ski.chrzanow.foldprojectviewrootfiles.FoldProjectViewRootFilesBundle.FOLD_PROJECT_VIEW_ROOT_FILES

fun isFoldingEnabled() = PropertiesComponent.getInstance().getBoolean(FOLD_PROJECT_VIEW_ROOT_FILES)

fun setFoldingEnabled(state: Boolean) {
    PropertiesComponent.getInstance().setValue(FOLD_PROJECT_VIEW_ROOT_FILES, state)
}
