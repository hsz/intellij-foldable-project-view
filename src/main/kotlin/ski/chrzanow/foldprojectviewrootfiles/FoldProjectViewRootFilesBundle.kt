package ski.chrzanow.foldprojectviewrootfiles

import com.intellij.AbstractBundle
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

@NonNls
private const val BUNDLE = "messages.FoldProjectViewRootFiles"

object FoldProjectViewRootFilesBundle : AbstractBundle(BUNDLE) {

    const val FOLD_PROJECT_VIEW_ROOT_FILES = "foldProjectViewRootFiles"

    @Suppress("SpreadOperator")
    @JvmStatic
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any) =
        getMessage(key, *params)

    @Suppress("SpreadOperator")
    @JvmStatic
    fun messagePointer(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any) =
        getLazyMessage(key, *params)
}
