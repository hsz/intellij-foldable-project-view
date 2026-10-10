package ski.chrzanow.foldableprojectview.settings

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.util.isNotNull
import com.intellij.openapi.observable.util.transform
import com.intellij.openapi.options.UiDslUnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ColorPanel
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.util.ui.UIUtil
import ski.chrzanow.foldableprojectview.FoldableProjectViewBundle.message
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.MATCH_COUNT_LIMIT
import ski.chrzanow.foldableprojectview.bindColor
import ski.chrzanow.foldableprojectview.bindColorControl
import ski.chrzanow.foldableprojectview.bindSelectedItem
import ski.chrzanow.foldableprojectview.bindText
import ski.chrzanow.foldableprojectview.psi.search.GlobPatterns

/**
 * Editor of the [Rule] selected in the rules table.
 *
 * The [settingsProperty] provides the options the matching depends on (case sensitivity, directories, nested folding);
 * the [project] is used to validate the patterns files paths (see [RuleType.PATTERNS_FILE]) and to count the elements
 * matched by the edited rule. It is `null` when the editor is used for the defaults of new projects.
 */
class FoldableRulesEditor(
    private val ruleProperty: ObservableMutableProperty<Rule?>,
    private val settingsProperty: ObservableProperty<out FoldableProjectState>,
    private val project: Project? = null,
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
     * Patterns are editable for the [RuleType.PATTERN] rules only; the built-in ignored files rule has none.
     */
    private val patternRulePredicate = rulePredicate { it.type == RuleType.PATTERN }

    /**
     * The [RuleType.PATTERNS_FILE] rules list the files with the patterns instead of the patterns themselves.
     */
    private val patternsFileRulePredicate = rulePredicate { it.type == RuleType.PATTERNS_FILE }
    private val notPatternsFileRulePredicate = rulePredicate { it.type != RuleType.PATTERNS_FILE }

    /**
     * The source of the patterns can be switched for all the rules but the built-in ignored files rule.
     */
    private val usesPatternsPredicate = rulePredicate(Rule::usesPatterns)

    /**
     * The [RuleType] edited with the source switch: the built-in ignored files rule is shown as [RuleType.PATTERN].
     */
    private val typeProperty = ruleProperty.transform(
        { it?.type?.takeIf { type -> type != RuleType.IGNORED_FILES } ?: RuleType.PATTERN },
        { value -> ruleProperty.get()?.apply { type = value } },
    )

    private fun rulePredicate(predicate: (Rule) -> Boolean) = object : ComponentPredicate() {

        override fun invoke() = ruleProperty.get()?.let(predicate) == true

        override fun addListener(listener: (Boolean) -> Unit) =
            ruleProperty.afterChange {
                listener(it?.let(predicate) == true)
            }
    }

    /**
     * Label showing how many elements the edited rule matches in the [project].
     */
    private val matchesLabel = JBLabel().apply {
        name = MATCHES_LABEL_NAME
        foreground = UIUtil.getContextHelpForeground()
    }

    override fun Panel.createContent() {
        rowsRange {
            row(message("foldableProjectView.settings.name")) {
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
            }.rowComment(message("foldableProjectView.settings.type.comment"), 40)
                .enabledIf(usesPatternsPredicate)
            row(message("foldableProjectView.settings.patterns")) {
                expandableTextField()
                    .align(Align.FILL)
                    .comment(message("foldableProjectView.settings.patterns.comment"), 40)
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
            }.enabledIf(patternRulePredicate)
                .visibleIf(notPatternsFileRulePredicate)
            row(message("foldableProjectView.settings.patternsFile")) {
                expandableTextField()
                    .align(Align.FILL)
                    .comment(message("foldableProjectView.settings.patternsFile.comment"), 40)
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
            }.visibleIf(patternsFileRulePredicate)
            if (project != null) {
                row("") {
                    cell(matchesLabel)
                }.visibleIf(usesPatternsPredicate)
                settingsProperty.afterChange { updateMatchesLabel() }
                updateMatchesLabel()
            }
            row(message("foldableProjectView.settings.action")) {
                segmentedButton(RuleAction.entries) { text = it.text }
                    .bindSelectedItem(ruleProperty, Rule::action, RuleAction.GROUP)
            }.rowComment(message("foldableProjectView.settings.action.comment"), 40)
            row {
                val foregroundCheckBox = checkBox(message("foldableProjectView.settings.foreground"))
                    .bindColorControl(ruleProperty, Rule::foreground, JBColor.foreground().brighter())

                cell(ColorPanel())
                    .align(Align.FILL)
                    .enabledIf(foregroundCheckBox.selected)
                    .bindColor(ruleProperty, Rule::foreground)
            }.enabledIf(groupActionPredicate)
            row {
                val backgroundCheckBox = checkBox(message("foldableProjectView.settings.background"))
                    .bindColorControl(ruleProperty, Rule::background, JBColor.background().darker())

                cell(ColorPanel())
                    .align(Align.FILL)
                    .enabledIf(backgroundCheckBox.selected)
                    .bindColor(ruleProperty, Rule::background)
            }.enabledIf(groupActionPredicate)
        }.enabledIf(selectedRowPredicate)
    }

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

    private fun updateMatchesLabel() {
        matchesLabel.text = matchesText().orEmpty()
    }

    /**
     * Describes how many elements of the [project] the edited rule matches with the current settings,
     * or `null` when there is nothing to count (no project, no rule selected, or the rule has no patterns).
     */
    fun matchesText(): String? {
        val project = project ?: return null
        val rule = ruleProperty.get()?.takeIf { it.usesPatterns && it.pattern.isNotBlank() } ?: return null
        val state = settingsProperty.get()
        val (count, limitReached) = runReadAction { countMatches(project, rule, state) }

        return when {
            limitReached -> message("foldableProjectView.settings.matches.limit", count)
            state.foldNestedDirectories -> message("foldableProjectView.settings.matches.nested", count)
            else -> message("foldableProjectView.settings.matches.root", count)
        }
    }

    /**
     * Counts the elements matched by the [rule]: the children of the content roots, or – when the rules are applied
     * in nested directories – the whole project content, visiting at most [MATCH_COUNT_LIMIT] files.
     * Returns the count and whether the limit was reached.
     */
    private fun countMatches(project: Project, rule: Rule, state: FoldableProjectState): Pair<Int, Boolean> {
        val scope = rule.getScope(project, state)
        val roots = ProjectRootManager.getInstance(project).contentRoots

        fun VirtualFile.isMatched() = (!isDirectory || state.matchDirectories) && scope.contains(this)

        if (!state.foldNestedDirectories) {
            return roots.sumOf { root -> root.children.count { it.isMatched() } } to false
        }

        var count = 0
        var visited = 0
        val completed = ProjectFileIndex.getInstance(project).iterateContent { file ->
            if (file !in roots && file.isMatched()) {
                count++
            }
            ++visited < MATCH_COUNT_LIMIT
        }
        return count to !completed
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
        /** Component name of the label showing the number of matched elements, see [matchesText]. */
        const val MATCHES_LABEL_NAME = "FoldableRulesEditor.matches"
    }
}
