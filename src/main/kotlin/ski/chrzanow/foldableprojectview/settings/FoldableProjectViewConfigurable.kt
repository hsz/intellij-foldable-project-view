package ski.chrzanow.foldableprojectview.settings

import com.intellij.ide.projectView.impl.AbstractProjectTreeStructure
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.Configurable.NoScroll
import com.intellij.openapi.project.Project
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.layout.not
import com.intellij.util.ui.tree.TreeUtil
import ski.chrzanow.foldableprojectview.FoldableProjectViewBundle.message
import ski.chrzanow.foldableprojectview.bindSelected
import ski.chrzanow.foldableprojectview.createPredicate
import ski.chrzanow.foldableprojectview.projectView.FoldableTreeStructureProvider
import ski.chrzanow.foldableprojectview.settings.FoldableProjectState.Rule
import java.awt.Dimension
import javax.swing.BorderFactory.createEmptyBorder

class FoldableProjectViewConfigurable(project: Project) : BoundSearchableConfigurable(
    helpTopic = "FoldableProjectView",
    _id = "FoldableProjectView",
    displayName = "FOOO", // TODO: ???
), NoScroll {

    companion object {
        const val ID = "ski.chrzanow.foldableprojectview.options.FoldableProjectViewConfigurable"
    }

    private val settings = project.service<FoldableProjectSettings>()

    private val propertyGraph = PropertyGraph()
    private val settingsProperty = propertyGraph.lazyProperty { FoldableProjectSettings().apply { copyFrom(settings) } }
    private val foldingEnabledPredicate = settingsProperty.createPredicate(FoldableProjectSettings::foldingEnabled)
    private val hideAllGroupsPredicate = settingsProperty.createPredicate(FoldableProjectSettings::hideAllGroups)

    private val ruleProperty = propertyGraph
        .lazyProperty<Rule?> { null }
        .apply {
            afterChange {
                ApplicationManager.getApplication().invokeLater {
                    rulesTable.tableView.updateUI()
                }
            }
        }

    private val rulesTable = FoldableRulesTable(settingsProperty)
    private val rulesEditor = FoldableRulesEditor(ruleProperty)

    private val settingsPanel = panel {
        row {
            checkBox(message("foldableProjectView.settings.foldingEnabled"))
                .bindSelected(settingsProperty, FoldableProjectSettings::foldingEnabled)
                .comment(message("foldableProjectView.settings.foldingEnabled.comment"), MAX_LINE_LENGTH_WORD_WRAP)
                .applyToComponent { setMnemonic('e') }
        }

        rowsRange {
            row {
                checkBox(message("foldableProjectView.settings.foldDirectories"))
                    .bindSelected(settingsProperty, FoldableProjectSettings::foldDirectories)
                    .comment(message("foldableProjectView.settings.foldDirectories.comment"), MAX_LINE_LENGTH_WORD_WRAP)
                    .applyToComponent { setMnemonic('d') }
            }

            row {
                checkBox(message("foldableProjectView.settings.foldIgnoredFiles"))
                    .bindSelected(settingsProperty, FoldableProjectSettings::foldIgnoredFiles)
                    .comment(message("foldableProjectView.settings.foldIgnoredFiles.comment"), MAX_LINE_LENGTH_WORD_WRAP)
                    .applyToComponent { setMnemonic('h') }
            }

            row {
                checkBox(message("foldableProjectView.settings.hideAllGroups"))
                    .bindSelected(settingsProperty, FoldableProjectSettings::hideAllGroups)
                    .comment(message("foldableProjectView.settings.hideAllGroups.comment"), MAX_LINE_LENGTH_WORD_WRAP)
                    .gap(RightGap.SMALL)
                    .applyToComponent { setMnemonic('i') }

                ContextHelpLabel
                    .create(
                        message("foldableProjectView.settings.hideAllGroups.help"),
                        message("foldableProjectView.settings.hideAllGroups.help.description"),
                    )
                    .let(::cell)
            }

            row {
                checkBox(message("foldableProjectView.settings.hideEmptyGroups"))
                    .bindSelected(settingsProperty, FoldableProjectSettings::hideEmptyGroups)
                    .comment(message("foldableProjectView.settings.hideEmptyGroups.comment"), MAX_LINE_LENGTH_WORD_WRAP)
                    .applyToComponent { setMnemonic('h') }
                    .enabledIf(hideAllGroupsPredicate.not())
            }

            row {
                checkBox(message("foldableProjectView.settings.caseInsensitive"))
                    .bindSelected(settingsProperty, FoldableProjectSettings::caseInsensitive)
                    .comment(message("foldableProjectView.settings.caseInsensitive.comment"), MAX_LINE_LENGTH_WORD_WRAP)
                    .applyToComponent { setMnemonic('c') }
            }
        }.enabledIf(foldingEnabledPredicate)
    }

    private val projectView by lazy {
        object : ProjectViewPane(project) {

            private val treeStructureProvider = FoldableTreeStructureProvider(project).withProjectViewPane(this).withState(settingsProperty)

            override fun enableDnD() = Unit

            override fun createStructure() = object : AbstractProjectTreeStructure(project) {

                override fun getProviders() = listOf(treeStructureProvider)
            }
        }
    }

    private val splitter = OnePixelSplitter(false, .6f, .4f, .6f).apply {
        firstComponent = settingsPanel.apply {
            border = createEmptyBorder(10, 10, 10, 30)
        }
        secondComponent = projectView.createComponent().apply {
            border = createEmptyBorder()
            preferredSize = Dimension()
        }
        setHonorComponentsMinimumSize(false)
        TreeUtil.promiseExpand(projectView.tree, 2)
    }

    override fun createPanel() =
        panel {
            row {
                cell(splitter)
                    .horizontalAlign(HorizontalAlign.FILL)
                    .verticalAlign(VerticalAlign.FILL)
            }
            group(message("foldableProjectView.settings.foldingRules")) {
                row {
                    cell(rulesTable.component)
                        .horizontalAlign(HorizontalAlign.FILL)
                        .verticalAlign(VerticalAlign.FILL)
                        .resizableColumn()

                    cell(rulesEditor.createPanel())
                        .applyIfEnabled()

                    with(rulesTable.tableView) {
                        selectionModel.addListSelectionListener {
                            ruleProperty.set(selectedObject)
                        }
                    }
                }
            }
        }

    override fun getId() = ID

    override fun getDisplayName() = message("foldableProjectView.name")

    override fun isModified() = settingsProperty.get() != settings

    override fun apply() {
        rulesTable
        settings.copyFrom(settingsProperty.get())
    }

    override fun reset() = with(settingsProperty) {
        set(get().apply { copyFrom(settings) })
    }
}
