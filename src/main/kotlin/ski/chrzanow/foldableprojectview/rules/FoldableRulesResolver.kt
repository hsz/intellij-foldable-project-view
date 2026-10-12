package ski.chrzanow.foldableprojectview.rules

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.RULE_SET_FILE
import ski.chrzanow.foldableprojectview.settings.FoldableProjectSettings
import ski.chrzanow.foldableprojectview.settings.FoldableProjectState
import ski.chrzanow.foldableprojectview.settings.Rule
import ski.chrzanow.foldableprojectview.settings.RuleOverride
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * The settings in effect for a project: the IDE settings with the rule set files merged in, see [FoldableRulesResolver].
 *
 * The [rules] start with the rules of the files (the user ones, then the project ones), followed by the rules stored
 * in the IDE settings – the very same instances, so the in-place edits made in the settings are picked up.
 */
class ResolvedState(
    override val foldingEnabled: Boolean,
    override val matchDirectories: Boolean,
    override val foldNestedDirectories: Boolean,
    override val hideEmptyGroups: Boolean,
    override val caseSensitive: Boolean,
    override val rules: MutableList<Rule>,

    /**
     * The `enabled` flag of every file-sourced rule as defined in its file, by rule id; used to tell the overrides
     * from the defaults when the table is edited, see [FoldableProjectSettings.ruleOverrides].
     */
    val fileRuleDefaults: Map<String, Boolean>,

    /**
     * The rule set files in effect, in the order they are applied.
     */
    val ruleSets: List<RuleSet>,

    /**
     * The options set by the rule set files; the corresponding IDE settings are not applied.
     */
    val fileOptions: RuleSetOptions,

    /**
     * The files that could not be read or resolved.
     */
    val problems: List<RuleSetProblem>,
) : FoldableProjectState {

    val fileRules
        get() = rules.filter(Rule::isFromFile)
}

/**
 * Merges the layers of the folding rules into the [ResolvedState] seen by the Project View, lowest to highest priority:
 * 1. the user rules (`~/.config/foldableprojectview/rules.yaml`), see [RuleSetRepository.userRules],
 * 2. the project files (`.foldableprojectview.yaml` in the project root and the module content roots),
 * 3. the IDE settings (`.idea/FoldableProjectView.xml`), see [FoldableProjectSettings].
 *
 * A later layer adds rules or overrides the earlier ones by id; the options set by the files override the IDE settings.
 * The rules of the files are read-only in the settings, except for the `enabled` flag kept per project in
 * [FoldableProjectSettings.ruleOverrides]. The built-in presets are never applied on their own.
 *
 * The files are re-read only when they change, and the resolved file rules are reused while their inputs are the same,
 * so the rule instances stay stable between the calls – the settings page relies on that to connect the table rows with
 * the preview groups.
 */
@Service(Service.Level.PROJECT)
class FoldableRulesResolver(private val project: Project) {

    private val repository
        get() = service<RuleSetRepository>()

    /**
     * Parsed project file along with the VFS stamps it was parsed at.
     */
    private class CachedProjectFile(val stamp: Any, val loaded: LoadedRuleSets)

    private val projectFiles = ConcurrentHashMap<String, CachedProjectFile>()

    /**
     * Inputs the file rules depend on; the rules are rebuilt only when the inputs change.
     */
    private data class FileRulesKey(
        val patches: List<Pair<RulePatch, RuleOrigin>>,
        val overrides: List<RuleOverride>,
        val settingsIds: Set<String>,
    )

    @Volatile
    private var cachedFileRules: Pair<FileRulesKey, List<Rule>>? = null

    /**
     * Resolves the state for the given [settings] – by default, the stored settings of the project. The settings page
     * passes the edited copy, so the preview reflects the unsaved changes.
     */
    fun resolve(settings: FoldableProjectSettings = project.service<FoldableProjectSettings>()): ResolvedState {
        if (project.isDefault) {
            return settingsOnly(settings)
        }

        val user = repository.userRules()
        val projectFiles = projectRuleSets()
        val ruleSets = user.ruleSets + projectFiles.ruleSets
        val problems = (user.problems + projectFiles.problems).toMutableList()

        val patches = linkedMapOf<String, Pair<RulePatch, RuleOrigin>>()
        var options = RuleSetOptions()
        ruleSets.forEach { ruleSet ->
            val resolved = repository.resolve(ruleSet)
            problems += resolved.problems
            options = options.overriddenBy(resolved.options)
            resolved.rules.forEach { patch ->
                patches[patch.id] = (patches[patch.id]?.first?.mergedWith(patch) ?: patch) to ruleSet.origin
            }
        }

        val settingsIds = settings.rules.map(Rule::effectiveId).toSet()
        val key = FileRulesKey(patches.values.toList(), settings.ruleOverrides.toList(), settingsIds)
        val fileRules = cachedFileRules
            ?.takeIf { it.first == key }
            ?.second
            ?: buildFileRules(key).also { cachedFileRules = key to it }

        return ResolvedState(
            foldingEnabled = options.foldingEnabled ?: settings.foldingEnabled,
            matchDirectories = options.matchDirectories ?: settings.matchDirectories,
            foldNestedDirectories = options.foldNestedDirectories ?: settings.foldNestedDirectories,
            hideEmptyGroups = options.hideEmptyGroups ?: settings.hideEmptyGroups,
            caseSensitive = options.caseSensitive ?: settings.caseSensitive,
            rules = (fileRules + settings.rules).toMutableList(),
            fileRuleDefaults = patches.values
                .filter { (patch, _) -> patch.id !in settingsIds }
                .associate { (patch, _) -> patch.id to (patch.enabled ?: true) },
            ruleSets = ruleSets,
            fileOptions = options,
            problems = problems,
        )
    }

    /**
     * The rules of the files that are not overridden by the rules of the IDE settings with the same id,
     * with the per-project `enabled` overrides applied.
     */
    private fun buildFileRules(key: FileRulesKey): List<Rule> {
        val overrides = key.overrides.associate { it.id to it.enabled }
        return key.patches
            .filter { (patch, _) -> patch.id !in key.settingsIds }
            .map { (patch, origin) ->
                patch.toRule().apply {
                    this.origin = origin
                    overrides[patch.id]?.let { enabled = it }
                }
            }
    }

    private fun settingsOnly(settings: FoldableProjectSettings) = ResolvedState(
        foldingEnabled = settings.foldingEnabled,
        matchDirectories = settings.matchDirectories,
        foldNestedDirectories = settings.foldNestedDirectories,
        hideEmptyGroups = settings.hideEmptyGroups,
        caseSensitive = settings.caseSensitive,
        rules = settings.rules.toMutableList(),
        fileRuleDefaults = emptyMap(),
        ruleSets = emptyList(),
        fileOptions = RuleSetOptions(),
        problems = emptyList(),
    )

    /**
     * The `.foldableprojectview.yaml` files of the project root and the module content roots, in a stable order.
     */
    fun projectRuleSets(): LoadedRuleSets = runReadAction {
        val roots = buildList {
            project.guessProjectDir()?.let(::add)
            addAll(ProjectRootManager.getInstance(project).contentRoots)
        }.distinct()

        val files = roots.mapNotNull { root ->
            root.findFileByRelativePath(RULE_SET_FILE)?.takeUnless(VirtualFile::isDirectory)
        }.distinct()

        projectFiles.keys.retainAll(files.map(VirtualFile::getPath).toSet())
        files.map(::loadProjectFile).fold(LoadedRuleSets(), LoadedRuleSets::plus)
    }

    private fun loadProjectFile(file: VirtualFile): LoadedRuleSets {
        val stamp = Triple(file.modificationStamp, file.timeStamp, file.length)
        projectFiles[file.path]?.takeIf { it.stamp == stamp }?.let { return it.loaded }

        val path = runCatching { Path.of(file.path) }.getOrNull()
        val text = runCatching { VfsUtilCore.loadText(file) }.getOrDefault("")
        val loaded = runCatching { RuleSetYaml.parse(text, file.path, RuleSetLayer.PROJECT, path, defaultName = RULE_SET_FILE) }
            .fold(
                onSuccess = { LoadedRuleSets(ruleSets = listOf(it)) },
                onFailure = { LoadedRuleSets(problems = listOf(RuleSetProblem(path, it.message ?: it.toString()))) },
            )
        projectFiles[file.path] = CachedProjectFile(stamp, loaded)
        return loaded
    }

    /**
     * Whether the file at [path] is one of the rule set files the resolved state depends on: a project file or a file
     * of the user directory.
     */
    fun affects(path: String) = path.endsWith("/$RULE_SET_FILE") || repository.isUserFile(path)
}
