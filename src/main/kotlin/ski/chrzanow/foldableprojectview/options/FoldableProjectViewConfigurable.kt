package ski.chrzanow.foldableprojectview.options

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.TitledSeparator
import com.intellij.util.ui.FormBuilder
import ski.chrzanow.foldableprojectview.FoldableProjectViewBundle
import javax.swing.JComponent

class FoldableProjectViewConfigurable : SearchableConfigurable {

    private val builder = FormBuilder.createFormBuilder()

    companion object {

        const val ID = "ski.chrzanow.foldableprojectview.options.FoldableProjectViewConfigurable"
    }

    override fun createComponent(): JComponent? {
        builder.addComponent(
            TitledSeparator(FoldableProjectViewBundle.message("foldableProjectView.name")),
            0
        )

        builder.addLabeledComponent(
            FoldableProjectViewBundle.message("foldableProjectView.pattern"),
            RawCommandLineEditor(),
        )
        return builder.panel
    }

    override fun isModified(): Boolean {
        return false
    }

    override fun apply() {
    }

    override fun getDisplayName() = FoldableProjectViewBundle.message("foldableProjectView.name")

    override fun getId() = ID
}
