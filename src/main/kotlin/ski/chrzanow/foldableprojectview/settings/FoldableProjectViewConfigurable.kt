package ski.chrzanow.foldableprojectview.settings

import com.intellij.ide.projectView.impl.AbstractProjectTreeStructure
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.layout.CCFlags
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.ui.layout.applyToComponent
import com.intellij.ui.layout.panel
import com.intellij.ui.layout.selected
import com.intellij.ui.layout.toBinding
import com.intellij.ui.layout.toNullableBinding
import com.intellij.ui.layout.withSelectedBinding
import com.intellij.ui.layout.withTextBinding
import com.intellij.util.ui.tree.TreeUtil
import ski.chrzanow.foldableprojectview.FoldableProjectViewBundle.message
import ski.chrzanow.foldableprojectview.projectView.FoldableTreeStructureProvider
import javax.swing.BorderFactory.createEmptyBorder

class FoldableProjectViewConfigurable(private val project: Project) : SearchableConfigurable {

    private val settings = project.service<FoldableProjectSettings>()
    private val propertyGraph = PropertyGraph()
    private val foldingEnabledProperty = propertyGraph.graphProperty { settings.foldingEnabled }
    private val foldDirectoriesProperty = propertyGraph.graphProperty { settings.foldDirectories }
    private val foldIgnoredFilesProperty = propertyGraph.graphProperty { settings.foldIgnoredFiles }
    private val hideEmptyGroupsProperty = propertyGraph.graphProperty { settings.hideEmptyGroups }
    private val hideAllGroupsProperty = propertyGraph.graphProperty { settings.hideAllGroups }
    private val caseInsensitiveProperty = propertyGraph.graphProperty { settings.caseInsensitive }
    private val patternsProperty = propertyGraph.graphProperty { settings.patterns ?: "" }

    private lateinit var foldingEnabledPredicate: ComponentPredicate

    private val settingsPanel = panel {
        blockRow {
            row {
                checkBox(
                    message("foldableProjectView.settings.foldingEnabled"),
                    foldingEnabledProperty,
                )
                    .withSelectedBinding(settings::foldingEnabled.toBinding())
                    .comment(message("foldableProjectView.settings.foldingEnabled.comment"), -1)
                    .applyToComponent { setMnemonic('e') }
                    .apply { foldingEnabledPredicate = selected }
            }

            row {
                checkBox(
                    message("foldableProjectView.settings.foldDirectories"),
                    foldDirectoriesProperty,
                )
                    .withSelectedBinding(settings::foldDirectories.toBinding())
                    .comment(message("foldableProjectView.settings.foldDirectories.comment"), -1)
                    .applyToComponent { setMnemonic('d') }
                    .enableIf(foldingEnabledPredicate)
            }

            row {
                checkBox(
                        message("foldableProjectView.settings.foldIgnoredFiles"),
                        foldIgnoredFilesProperty,
                )
                        .withSelectedBinding(settings::foldIgnoredFiles.toBinding())
                        .comment(message("foldableProjectView.settings.foldIgnoredFiles.comment"), -1)
                        .applyToComponent { setMnemonic('h') }
                        .enableIf(foldingEnabledPredicate)
            }

            row {
                checkBox(
                    message("foldableProjectView.settings.hideEmptyGroups"),
                    hideEmptyGroupsProperty,
                )
                    .withSelectedBinding(settings::hideEmptyGroups.toBinding())
                    .comment(message("foldableProjectView.settings.hideEmptyGroups.comment"), -1)
                    .applyToComponent { setMnemonic('h') }
                    .enableIf(foldingEnabledPredicate)
                    .apply { hideAllGroupsProperty.afterPropagation { enabled = !hideAllGroupsProperty.get() } }
            }

            row {
                checkBox(
                    message("foldableProjectView.settings.hideAllGroups"),
                    hideAllGroupsProperty,
                )
                    .withSelectedBinding(settings::hideAllGroups.toBinding())
                    .comment(message("foldableProjectView.settings.hideAllGroups.comment"), -1)
                    .applyToComponent { setMnemonic('i') }
                    .enableIf(foldingEnabledPredicate)

                ContextHelpLabel.create(
                    message("foldableProjectView.settings.hideAllGroups.help"),
                    message("foldableProjectView.settings.hideAllGroups.help.description"),
                )()
            }

            row {
                checkBox(
                    message("foldableProjectView.settings.caseInsensitive"),
                    caseInsensitiveProperty,
                )
                    .withSelectedBinding(settings::caseInsensitive.toBinding())
                    .comment(message("foldableProjectView.settings.caseInsensitive.comment"), -1)
                    .applyToComponent { setMnemonic('c') }
                    .enableIf(foldingEnabledPredicate)
            }
        }

        titledRow(message("foldableProjectView.settings.foldingRules")) {
            row {
                expandableTextField(patternsProperty)
                    .withTextBinding(settings::patterns.toNullableBinding(""))
                    .comment(message("foldableProjectView.settings.patterns.comment"), -1)
                    .constraints(CCFlags.growX)
                    .applyToComponent {
                        emptyText.text = message("foldableProjectView.settings.patterns")
                    }
                    .enableIf(foldingEnabledPredicate)
            }
        }
    }
    private val projectView by lazy {
        object : ProjectViewPane(project) {
            override fun enableDnD() = Unit

            override fun createStructure() = object : AbstractProjectTreeStructure(project) {
                override fun getProviders() = listOf(FoldableTreeStructureProvider(project).apply {
                    propertyGraph.afterPropagation {
                        updateFromRoot(true)
                    }
                    withState(FoldableProjectState.fromGraphProperties(
                        foldingEnabledProperty,
                        foldDirectoriesProperty,
                        hideEmptyGroupsProperty,
                        hideAllGroupsProperty,
                        foldIgnoredFilesProperty,
                        caseInsensitiveProperty,
                        patternsProperty,
                    ))
                })
            }
        }
    }

    companion object {
        const val ID = "ski.chrzanow.foldableprojectview.options.FoldableProjectViewConfigurable"
    }

    override fun getId() = ID

    override fun getDisplayName() = message("foldableProjectView.name")

    override fun createComponent() = OnePixelSplitter(.3f).apply {
        firstComponent = settingsPanel.apply {
            border = createEmptyBorder(10, 10, 10, 30)
        }
        secondComponent = projectView.createComponent().apply {
            border = createEmptyBorder()
        }
        TreeUtil.promiseExpand(projectView.tree, 2)
    }

    override fun isModified() = settingsPanel.isModified()

    override fun reset() = settingsPanel.reset()

    override fun apply() {
        val updated = isModified

        settingsPanel.apply()
        if (updated) {
            ApplicationManager.getApplication()
                .messageBus
                .syncPublisher(FoldableProjectSettingsListener.TOPIC)
                .settingsChanged(settings)
        }
    }
}
