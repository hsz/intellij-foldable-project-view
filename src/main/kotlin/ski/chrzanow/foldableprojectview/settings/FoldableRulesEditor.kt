package ski.chrzanow.foldableprojectview.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.util.isNotNull
import com.intellij.openapi.observable.util.lockOrSkip
import com.intellij.openapi.observable.util.transform
import com.intellij.openapi.options.UiDslUnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ColorPanel
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.UIUtil
import ski.chrzanow.foldableprojectview.Debouncer
import ski.chrzanow.foldableprojectview.FoldableProjectViewBundle.message
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.MATCH_COUNT_LIMIT
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.MATCH_PREVIEW_LIMIT
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.REFRESH_DELAY
import ski.chrzanow.foldableprojectview.bindColor
import ski.chrzanow.foldableprojectview.bindColorControl
import ski.chrzanow.foldableprojectview.bindSelectedItem
import ski.chrzanow.foldableprojectview.bindText
import ski.chrzanow.foldableprojectview.psi.search.GlobPatterns
import java.awt.Color
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.KMutableProperty1

/**
 * Editor of the [Rule] selected in the rules table.
 *
 * The [settingsProperty] provides the options the matching depends on (case sensitivity, directories, nested folding);
 * the [project] is used to validate the patterns files paths (see [RuleType.PATTERNS_FILE]) and to count the elements
 * matched by the edited rule. It is `null` when the editor is used for the defaults of new projects.
 * Counting the matches may visit the whole project content, so with a [parentDisposable] the count is recomputed
 * [REFRESH_DELAY] after the last change instead of on every keystroke.
 */
class FoldableRulesEditor(
    private val ruleProperty: ObservableMutableProperty<Rule?>,
    private val settingsProperty: ObservableProperty<out FoldableProjectState>,
    private val project: Project? = null,
    parentDisposable: Disposable? = null,
) : UiDslUnnamedConfigurable.Simple() {

    private val selectedRowPredicate = object : ComponentPredicate() {

        override fun invoke() = ruleProperty.isNotNull().get()

        override fun addListener(listener: (Boolean) -> Unit) =
            ruleProperty.afterChange {
                listener(it != null)
            }
    }

    /**
     * Colors apply to the folding group only, so they are relevant for the [RuleAction.GROUP] action only.
     */
    private val groupActionPredicate = rulePredicate { it.action == RuleAction.GROUP }

    /**
     * The patterns field is shown for the [RuleType.PATTERN] rules (and while no rule is selected, as the editor's default shape).
     */
    private val patternRulePredicate = rulePredicate(whenNoRule = true) { it.type == RuleType.PATTERN }

    /**
     * The [RuleType.PATTERNS_FILE] rules list the files with the patterns instead of the patterns themselves.
     */
    private val patternsFileRulePredicate = rulePredicate { it.type == RuleType.PATTERNS_FILE }

    /**
     * The built-in ignored files rule has no patterns to edit; a description of what it matches is shown instead.
     */
    private val ignoredFilesRulePredicate = rulePredicate { it.type == RuleType.IGNORED_FILES }

    /**
     * The source of the patterns can be switched for all the rules but the built-in ignored files rule.
     */
    private val usesPatternsPredicate = rulePredicate { it.usesPatterns }
    private val sourceRowPredicate = rulePredicate(whenNoRule = true) { it.usesPatterns }

    /**
     * The [RuleType] edited with the source switch: the built-in ignored files rule is shown as [RuleType.PATTERN].
     */
    private val typeProperty = ruleProperty.transform(
        { it?.type?.takeIf { type -> type != RuleType.IGNORED_FILES } ?: RuleType.PATTERN },
        { value -> ruleProperty.get()?.apply { type = value } },
    )

    /**
     * Predicate evaluated against the edited rule; [whenNoRule] is its value while no rule is selected.
     */
    private fun rulePredicate(whenNoRule: Boolean = false, predicate: (Rule) -> Boolean) = object : ComponentPredicate() {

        override fun invoke() = ruleProperty.get()?.let(predicate) ?: whenNoRule

        override fun addListener(listener: (Boolean) -> Unit) =
            ruleProperty.afterChange {
                listener(it?.let(predicate) ?: whenNoRule)
            }
    }

    /**
     * Label showing how many elements the edited rule matches in the [project]; its tooltip lists the first of them.
     */
    private val matchesLabel = JBLabel().apply {
        name = MATCHES_LABEL_NAME
        foreground = UIUtil.getContextHelpForeground()
    }

    private val matchesUpdater = if (project != null && parentDisposable != null) {
        Debouncer.create(project, parentDisposable, REFRESH_DELAY, ::updateMatchesLabel)
    } else {
        null
    }

    override fun Panel.createContent() {
        rowsRange {
            row(message("foldableProjectView.settings.name.label")) {
                textField()
                    .align(Align.FILL)
                    .applyToComponent { emptyText.text = message("foldableProjectView.settings.name.placeholder") }
                    .bindText(ruleProperty, Rule::name)
                    .validationOnInput { field ->
                        when {
                            ruleProperty.get() == null || field.text.isNotBlank() -> null
                            else -> warning(message("foldableProjectView.settings.name.empty"))
                        }
                    }
            }
            row(message("foldableProjectView.settings.type")) {
                segmentedButton(listOf(RuleType.PATTERN, RuleType.PATTERNS_FILE)) { text = it.text }
                    .bind(typeProperty)
            }.rowComment(message("foldableProjectView.settings.type.comment"), MAX_LINE_LENGTH_WORD_WRAP)
                .visibleIf(sourceRowPredicate)
            row(message("foldableProjectView.settings.patterns.label")) {
                expandableTextField()
                    .align(Align.FILL)
                    .comment(message("foldableProjectView.settings.patterns.comment"), MAX_LINE_LENGTH_WORD_WRAP)
                    .applyToComponent { emptyText.text = message("foldableProjectView.settings.patterns.placeholder") }
                    .bindText(ruleProperty, Rule::pattern)
                    .validationOnInput { field ->
                        when {
                            ruleProperty.get()?.type != RuleType.PATTERN -> null
                            field.text.isBlank() -> warning(message("foldableProjectView.settings.patterns.empty.tooltip"))
                            else -> GlobPatterns.invalidPatterns(field.text)
                                .takeIf { it.isNotEmpty() }
                                ?.let { warning(message("foldableProjectView.settings.patterns.invalid", it.joinToString(" "))) }
                        }
                    }
                contextHelp(message("foldableProjectView.settings.patterns.help"), message("foldableProjectView.settings.patterns.help.title"))
            }.visibleIf(patternRulePredicate)
            row(message("foldableProjectView.settings.patternsFile.label")) {
                expandableTextField()
                    .align(Align.FILL)
                    .comment(message("foldableProjectView.settings.patternsFile.comment"), MAX_LINE_LENGTH_WORD_WRAP)
                    .applyToComponent { emptyText.text = message("foldableProjectView.settings.patternsFile.placeholder") }
                    .bindText(ruleProperty, Rule::pattern)
                    .validationOnInput { field ->
                        when {
                            ruleProperty.get()?.type != RuleType.PATTERNS_FILE -> null
                            field.text.isBlank() -> warning(message("foldableProjectView.settings.patterns.empty.tooltip"))
                            else -> missingPatternsFiles(field.text)
                                .takeIf { it.isNotEmpty() }
                                ?.let { warning(message("foldableProjectView.settings.patternsFile.notFound", it.joinToString(" "))) }
                        }
                    }
                contextHelp(message("foldableProjectView.settings.patternsFile.help"), message("foldableProjectView.settings.patternsFile.help.title"))
            }.visibleIf(patternsFileRulePredicate)
            row(message("foldableProjectView.settings.patterns")) {
                text(message("foldableProjectView.ignoredFiles.description"))
                    .applyToComponent { name = IGNORED_FILES_DESCRIPTION_NAME }
            }.visibleIf(ignoredFilesRulePredicate)
            if (project != null) {
                row("") {
                    cell(matchesLabel)
                }.visibleIf(usesPatternsPredicate)
                settingsProperty.afterChange { scheduleMatchesUpdate() }
                updateMatchesLabel()
            }
            row(message("foldableProjectView.settings.action")) {
                segmentedButton(RuleAction.entries) { text = it.text }
                    .bindSelectedItem(ruleProperty, Rule::action, RuleAction.GROUP)
            }.rowComment(message("foldableProjectView.settings.action.comment"), MAX_LINE_LENGTH_WORD_WRAP)
            row {
                val foregroundCheckBox = checkBox(message("foldableProjectView.settings.foreground"))
                    .applyToComponent { setMnemonic('g') }
                    .bindColorControl(ruleProperty, Rule::foreground, RuleColors.foregrounds.first())

                colorChooser(RuleColors.foregrounds, Rule::foreground, foregroundCheckBox.selected)
            }.enabledIf(groupActionPredicate)
            row {
                val backgroundCheckBox = checkBox(message("foldableProjectView.settings.background"))
                    .applyToComponent { setMnemonic('b') }
                    .bindColorControl(ruleProperty, Rule::background, RuleColors.backgrounds.first())

                colorChooser(RuleColors.backgrounds, Rule::background, backgroundCheckBox.selected)
            }.rowComment(message("foldableProjectView.settings.color.comment"), MAX_LINE_LENGTH_WORD_WRAP)
                .enabledIf(groupActionPredicate)
        }.enabledIf(selectedRowPredicate)
    }

    /**
     * Combo box with the theme-aware colors of the [palette] and a `Custom…` item, followed by a color picker
     * shown for the custom colors only. Both edit the [property] of the selected rule.
     */
    private fun Row.colorChooser(palette: List<RuleColor>, property: KMutableProperty1<Rule, Color?>, enabled: ComponentPredicate) {
        val customColorPredicate = rulePredicate { property.get(it).isCustom }

        comboBox(palette + CUSTOM_COLOR, colorRenderer(property))
            .applyToComponent { name = property.name }
            .enabledIf(enabled)
            .bindColorChoice(palette, property)

        cell(ColorPanel())
            .enabledIf(enabled)
            .visibleIf(customColorPredicate)
            .bindColor(ruleProperty, property)
    }

    private fun colorRenderer(property: KMutableProperty1<Rule, Color?>) = SimpleListCellRenderer.create<Any?> { label, value, _ ->
        when (value) {
            is RuleColor -> {
                label.text = value.displayName
                label.icon = ColorIcon(COLOR_ICON_SIZE, value, true)
            }

            else -> {
                label.text = message("foldableProjectView.settings.color.custom")
                label.icon = ruleProperty.get()?.let(property::get)
                    ?.takeIf { it.isCustom }
                    ?.let { ColorIcon(COLOR_ICON_SIZE, it, true) }
                    ?: EmptyIcon.create(COLOR_ICON_SIZE)
            }
        }
    }

    /**
     * Keeps the selected item in sync with the color of the edited rule: a [RuleColor] selects its item, any other color
     * selects [CUSTOM_COLOR]. Choosing `Custom…` turns the current color into a custom one to start the picking from.
     */
    private fun Cell<ComboBox<Any>>.bindColorChoice(palette: List<RuleColor>, property: KMutableProperty1<Rule, Color?>) = applyToComponent {
        val mutex = AtomicBoolean()

        fun update(rule: Rule?) {
            selectedItem = when (val color = rule?.let(property::get)) {
                is RuleColor -> color
                null -> palette.first()
                else -> CUSTOM_COLOR
            }
        }

        update(ruleProperty.get())
        ruleProperty.afterChange { rule ->
            mutex.lockOrSkip { update(rule) }
        }
        addActionListener {
            mutex.lockOrSkip {
                ruleProperty.get()?.let { rule ->
                    val current = property.get(rule)
                    val color = when (val selected = selectedItem) {
                        is RuleColor -> selected
                        else -> current?.takeIf { it.isCustom } ?: (current ?: palette.first()).toCustomColor()
                    }
                    property.set(rule, color)
                    ruleProperty.set(rule)
                }
            }
        }
    }

    /**
     * A copy of the color as it looks in the current theme, used as is in both themes.
     */
    @Suppress("UseJBColor")
    private fun Color.toCustomColor() = Color(rgb, true).let { JBColor(it, it) }

    /**
     * Returns the patterns files listed in the [text] that exist in none of the project content roots.
     * Nothing is reported without a [project].
     */
    private fun missingPatternsFiles(text: String): List<String> {
        val roots = project?.let { ProjectRootManager.getInstance(it).contentRoots } ?: return emptyList()
        return Rule.patternsFilePaths(text).filter { path ->
            roots.none { root -> root.findFileByRelativePath(path) != null }
        }
    }

    private fun scheduleMatchesUpdate() = matchesUpdater?.request() ?: updateMatchesLabel()

    private fun updateMatchesLabel() {
        val matches = findMatches()
        matchesLabel.text = matches?.text.orEmpty()
        matchesLabel.toolTipText = matches?.tooltip
    }

    /**
     * Finds the elements of the [project] the edited rule matches with the current settings,
     * or `null` when there is nothing to count (no project, no rule selected, or the rule has no patterns).
     */
    private fun findMatches(): Matches? {
        val project = project ?: return null
        val rule = ruleProperty.get()?.takeIf { it.usesPatterns && it.pattern.isNotBlank() } ?: return null
        val state = settingsProperty.get()

        return runReadAction { findMatches(project, rule, state) }
    }

    /**
     * The elements matched by a rule: their number, whether the [MATCH_COUNT_LIMIT] was reached before the whole project
     * content was visited, and the first [MATCH_PREVIEW_LIMIT] of them as paths relative to their content roots.
     */
    @Suppress("CanBeParameter")
    private class Matches(val count: Int, val limitReached: Boolean, val examples: List<String>, nested: Boolean) {

        val text = when {
            limitReached -> message("foldableProjectView.settings.matches.limit", count)
            nested -> message("foldableProjectView.settings.matches.nested", count)
            else -> message("foldableProjectView.settings.matches.root", count)
        }

        val tooltip = examples
            .takeIf { it.isNotEmpty() }
            ?.map(StringUtil::escapeXmlEntities)
            ?.let { paths ->
                val more = count - paths.size
                val lines = if (more > 0 || limitReached) {
                    paths + message("foldableProjectView.settings.matches.more", more.coerceAtLeast(1))
                } else {
                    paths
                }
                lines.joinToString("<br>", prefix = "<html>", postfix = "</html>")
            }
    }

    /**
     * Finds the elements matched by the [rule]: the children of the content roots, or – when the rules are applied
     * in nested directories – the whole project content, visiting at most [MATCH_COUNT_LIMIT] files.
     */
    private fun findMatches(project: Project, rule: Rule, state: FoldableProjectState): Matches {
        val scope = rule.getScope(project, state)
        val roots = ProjectRootManager.getInstance(project).contentRoots
        val examples = mutableListOf<String>()
        var count = 0

        fun VirtualFile.isMatched() = (!isDirectory || state.matchDirectories) && scope.contains(this)

        fun VirtualFile.collect(root: VirtualFile?) {
            count++
            if (examples.size < MATCH_PREVIEW_LIMIT) {
                examples += root?.let { VfsUtilCore.getRelativePath(this, it) } ?: name
            }
        }

        if (!state.foldNestedDirectories) {
            roots.forEach { root ->
                root.children.filter { it.isMatched() }.forEach { it.collect(root) }
            }
            return Matches(count, false, examples, nested = false)
        }

        val fileIndex = ProjectFileIndex.getInstance(project)
        var visited = 0
        val completed = fileIndex.iterateContent { file ->
            if (file !in roots && file.isMatched()) {
                file.collect(fileIndex.getContentRootForFile(file))
            }
            ++visited < MATCH_COUNT_LIMIT
        }
        return Matches(count, !completed, examples, nested = true)
    }

    private val RuleAction.text
        get() = when (this) {
            RuleAction.GROUP -> message("foldableProjectView.settings.action.group")
            RuleAction.HIDE -> message("foldableProjectView.settings.action.hide")
        }

    private val RuleType.text
        get() = when (this) {
            RuleType.PATTERNS_FILE -> message("foldableProjectView.settings.type.patternsFile")
            else -> message("foldableProjectView.settings.type.pattern")
        }

    companion object {
        /** Component name of the label showing the number of matched elements, see [Matches]. */
        const val MATCHES_LABEL_NAME = "FoldableRulesEditor.matches"

        /** Component name of the description shown instead of the patterns for the built-in ignored files rule. */
        const val IGNORED_FILES_DESCRIPTION_NAME = "FoldableRulesEditor.ignoredFilesDescription"

        /** The `Custom…` item of the color combo boxes, see [colorChooser]. */
        val CUSTOM_COLOR = Any()

        private const val COLOR_ICON_SIZE = 12
    }
}
