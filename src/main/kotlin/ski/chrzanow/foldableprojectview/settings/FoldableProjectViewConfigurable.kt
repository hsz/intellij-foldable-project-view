package ski.chrzanow.foldableprojectview.settings

import com.intellij.ide.projectView.impl.AbstractProjectTreeStructure
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.openapi.components.service
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.Configurable.NoScroll
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.not
import com.intellij.util.ui.tree.TreeUtil
import ski.chrzanow.foldableprojectview.FoldableProjectViewBundle.message
import ski.chrzanow.foldableprojectview.bindSelected
import ski.chrzanow.foldableprojectview.createPredicate
import ski.chrzanow.foldableprojectview.projectView.FoldableTreeStructureProvider
import java.awt.Dimension
import javax.swing.BorderFactory.createEmptyBorder
import javax.swing.JComponent

class FoldableProjectViewConfigurable(private val project: Project) : BoundSearchableConfigurable(
    displayName = message("foldableProjectView.name"),
    helpTopic = "FoldableProjectView",
    _id = ID,
), NoScroll {

    companion object {
        const val ID = "FoldableProjectViewConfigurable"
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
                rulesTable.tableView.repaint()

                // Re-emit the settings so the preview and the modification state pick up the rule change.
                settingsProperty.set(settingsProperty.get())
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
                checkBox(message("foldableProjectView.settings.caseSensitive"))
                    .bindSelected(settingsProperty, FoldableProjectSettings::caseSensitive)
                    .comment(message("foldableProjectView.settings.caseSensitive.comment"), MAX_LINE_LENGTH_WORD_WRAP)
                    .applyToComponent { setMnemonic('c') }
            }

            row {
                checkBox(message("foldableProjectView.settings.matchDirectories"))
                    .bindSelected(settingsProperty, FoldableProjectSettings::matchDirectories)
                    .comment(message("foldableProjectView.settings.matchDirectories.comment"), MAX_LINE_LENGTH_WORD_WRAP)
                    .applyToComponent { setMnemonic('d') }
            }

            row {
                checkBox(message("foldableProjectView.settings.foldIgnoredFiles"))
                    .bindSelected(settingsProperty, FoldableProjectSettings::foldIgnoredFiles)
                    .comment(message("foldableProjectView.settings.foldIgnoredFiles.comment"), MAX_LINE_LENGTH_WORD_WRAP)
                    .applyToComponent { setMnemonic('i') }
            }

            row {
                checkBox(message("foldableProjectView.settings.hideAllGroups"))
                    .bindSelected(settingsProperty, FoldableProjectSettings::hideAllGroups)
                    .comment(message("foldableProjectView.settings.hideAllGroups.comment"), MAX_LINE_LENGTH_WORD_WRAP)
                    .gap(RightGap.SMALL)
                    .applyToComponent { setMnemonic('a') }

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
        }.enabledIf(foldingEnabledPredicate)
    }

    private fun createProjectViewPane() = object : ProjectViewPane(project) {

        private val treeStructureProvider = FoldableTreeStructureProvider(project).withPreview(this, settingsProperty)

        override fun enableDnD() = Unit

        override fun createStructure() = object : AbstractProjectTreeStructure(project) {

            override fun getProviders() = listOf(treeStructureProvider)
        }
    }

    /**
     * Settings panel with the live Project View preview on the right side.
     * The preview is not available for the default project (e.g., in the "New Projects Setup" settings).
     */
    private fun createSettingsWithPreview(): JComponent {
        if (project.isDefault) {
            return settingsPanel
        }

        val projectView = createProjectViewPane().also {
            Disposer.register(disposable!!, it)
        }

        return OnePixelSplitter(false, .6f, .4f, .6f).apply {
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
    }

    override fun createPanel() = panel {
        row {
            comment(message("foldableProjectView.settings.support.comment"), MAX_LINE_LENGTH_WORD_WRAP)
        }
        row {
            cell(createSettingsWithPreview())
                .align(Align.FILL)
        }
        group(message("foldableProjectView.settings.foldingRules")) {
            row {
                cell(rulesTable.component)
                    .align(Align.FILL)
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

    override fun isModified() = settingsProperty.get() != settings

    override fun apply() {
        settings.update(project) {
            copyFrom(settingsProperty.get())
        }
        invalidateTable()
    }

    override fun reset() {
        settingsProperty.set(settingsProperty.get().apply {
            copyFrom(settings)
        })
        invalidateTable()
    }

    private fun invalidateTable() = rulesTable.apply {
        setValues(settingsProperty.get().rules)
        tableView.selection.clear()
    }
}
