package ski.chrzanow.foldableprojectview.rules

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.LocalFileSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.BUILTIN_PRESETS_INDEX
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.BUILTIN_PRESETS_PATH
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.BUILTIN_PRESET_PREFIX
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.USER_PRESETS_DIRECTORY
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.USER_PRESET_PREFIX
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.USER_RULES_FILE
import ski.chrzanow.foldableprojectview.settings.FoldableProjectViewAppSettings
import ski.chrzanow.foldableprojectview.settings.Rule
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText

/**
 * A rule set file that could not be read, reported in the settings and the log instead of breaking the Project View.
 */
data class RuleSetProblem(val path: Path?, val message: String) {

    val presentableMessage
        get() = listOfNotNull(path?.toString(), message).joinToString(": ")
}

/**
 * The rule sets read from a file or a directory, along with the problems met on the way.
 */
data class LoadedRuleSets(val ruleSets: List<RuleSet> = emptyList(), val problems: List<RuleSetProblem> = emptyList()) {

    operator fun plus(other: LoadedRuleSets) = LoadedRuleSets(ruleSets + other.ruleSets, problems + other.problems)
}

/**
 * A rule set with its `extends` resolved: the options and the rules of the included rule sets with its own on top,
 * see [RuleSetRepository.resolve].
 */
data class ResolvedRuleSet(
    val ruleSet: RuleSet,
    val options: RuleSetOptions,
    val rules: List<RulePatch>,
    val problems: List<RuleSetProblem> = emptyList(),
) {

    /**
     * The rules as [Rule]s with the defaults filled in and the [RuleSet.origin] set.
     */
    fun toRules() = rules.map { it.toRule().apply { origin = ruleSet.origin } }
}

/**
 * Finds the rule sets available to all projects:
 * - the built-in presets shipped in the plugin resources (`/presets`), listed in the index file – never applied on their own,
 *   only offered in the settings and reachable via `extends` as `builtin:<file name>`,
 * - the presets of the user directory (the YAML files of `~/.config/foldableprojectview/presets`), reachable as `user:<file name>`,
 * - the user rules (`~/.config/foldableprojectview/rules.yaml`), applied to every project.
 *
 * The project files are found by `FoldableRulesResolver`, which merges all the layers with the IDE settings.
 * The files are parsed once and re-read when their modification time changes, so the lookups are cheap enough to be
 * repeated on every Project View update. The user directory is added to the VFS watch roots, so its changes refresh
 * the Project View like the project files do.
 */
@Service(Service.Level.APP)
class RuleSetRepository(private val coroutineScope: CoroutineScope) {

    private val appSettings
        get() = service<FoldableProjectViewAppSettings>()

    /**
     * Parsed file along with the modification time and the size it was parsed at.
     */
    private class CachedFile(val stamp: Any, val loaded: LoadedRuleSets)

    private val cache = ConcurrentHashMap<Path, CachedFile>()

    @Volatile
    private var watchedDirectory: Path? = null

    init {
        watchUserDirectory()
    }

    /**
     * The presets shipped with the plugin, see [BUILTIN_PRESETS_INDEX]. A preset that fails to parse is logged and skipped,
     * although the tests make sure none does.
     */
    val builtinPresets: List<RuleSet> by lazy {
        val index = javaClass.getResourceAsStream(BUILTIN_PRESETS_INDEX)?.use { it.reader().readText() }
            ?: return@lazy emptyList<RuleSet>().also { LOG.warn("Built-in presets index not found: $BUILTIN_PRESETS_INDEX") }

        index.lines()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { file ->
                val text = javaClass.getResourceAsStream("$BUILTIN_PRESETS_PATH/$file")?.use { it.reader().readText() }
                    ?: return@mapNotNull null.also { LOG.warn("Built-in preset not found: $file") }
                val id = BUILTIN_PRESET_PREFIX + file.substringBeforeLast('.')
                runCatching { RuleSetYaml.parse(text, id, RuleSetLayer.BUILTIN) }
                    .onFailure { LOG.warn("Built-in preset $file is invalid: ${it.message}") }
                    .getOrNull()
            }
    }

    val userDirectory: Path
        get() = appSettings.userDirectoryPath

    val userPresetsDirectory: Path
        get() = userDirectory.resolve(USER_PRESETS_DIRECTORY)

    val userRulesFile: Path
        get() = userDirectory.resolve(USER_RULES_FILE)

    /**
     * The presets of the user directory, one per YAML file, identified as `user:<file name without extension>`.
     */
    fun userPresets(): LoadedRuleSets {
        val directory = userPresetsDirectory
        if (!directory.exists()) {
            return LoadedRuleSets()
        }
        return runCatching { directory.listDirectoryEntries() }
            .getOrElse { return LoadedRuleSets(problems = listOf(RuleSetProblem(directory, it.message ?: it.toString()))) }
            .filter { it.isRegularFile() && it.extension in YAML_EXTENSIONS }
            .sortedBy { it.fileName.toString() }
            .map { load(it, RuleSetLayer.USER, USER_PRESET_PREFIX + it.nameWithoutExtension, defaultName = it.nameWithoutExtension) }
            .fold(LoadedRuleSets(), LoadedRuleSets::plus)
    }

    /**
     * The user rules applied to every project, if the file exists.
     */
    fun userRules() = load(userRulesFile, RuleSetLayer.USER, userRulesFile.toString(), defaultName = USER_RULES_FILE)

    /**
     * All the presets offered in the settings: the built-in ones followed by the user ones.
     */
    fun presets() = builtinPresets + userPresets().ruleSets

    /**
     * Finds a rule set by the id used in `extends`: `builtin:docs`, `user:gradle`, or a bare `docs` looked up among
     * the user presets first and the built-in ones next.
     */
    fun find(id: String): RuleSet? = when {
        id.startsWith(BUILTIN_PRESET_PREFIX) -> builtinPresets.firstOrNull { it.id == id }
        id.startsWith(USER_PRESET_PREFIX) -> userPresets().ruleSets.firstOrNull { it.id == id }
        else -> find(USER_PRESET_PREFIX + id) ?: find(BUILTIN_PRESET_PREFIX + id)
    }

    /**
     * Resolves the `extends` of the [ruleSet]: the rules of the included sets come first, in the order listed, followed by
     * the rules of the set itself. A rule with the id of an earlier rule overrides it in place instead of being added,
     * see [RulePatch]. Unknown and cyclic references are reported as problems and skipped.
     */
    fun resolve(ruleSet: RuleSet): ResolvedRuleSet {
        val problems = mutableListOf<RuleSetProblem>()
        val rules = linkedMapOf<String, RulePatch>()
        var options = RuleSetOptions()

        fun include(set: RuleSet, visited: Set<String>) {
            if (set.id in visited) {
                problems += RuleSetProblem(set.path, "Cyclic extends: ${set.id}")
                return
            }
            set.extends.forEach { id ->
                val extended = find(id)
                if (extended == null) {
                    problems += RuleSetProblem(set.path, "Unknown rule set: $id")
                } else {
                    include(extended, visited + set.id)
                }
            }
            options = options.overriddenBy(set.options)
            set.rules.forEach { rule ->
                rules[rule.id] = rules[rule.id]?.mergedWith(rule) ?: rule
            }
        }
        include(ruleSet, emptySet())

        return ResolvedRuleSet(ruleSet, options, rules.values.toList(), problems)
    }

    /**
     * Reads and parses the rule set file at [path], or returns nothing if the file does not exist.
     */
    fun load(path: Path, layer: RuleSetLayer, id: String, defaultName: String = id): LoadedRuleSets {
        if (!path.isRegularFile()) {
            cache.remove(path)
            return LoadedRuleSets()
        }
        val stamp = runCatching { Files.getLastModifiedTime(path) to Files.size(path) }.getOrNull()
        cache[path]?.takeIf { stamp != null && it.stamp == stamp }?.let { return it.loaded }

        val loaded = runCatching { RuleSetYaml.parse(path.readText(), id, layer, path, defaultName) }
            .fold(
                onSuccess = { LoadedRuleSets(ruleSets = listOf(it)) },
                onFailure = { LoadedRuleSets(problems = listOf(RuleSetProblem(path, it.message ?: it.toString()))) },
            )
        if (stamp != null) {
            cache[path] = CachedFile(stamp, loaded)
        }
        return loaded
    }

    /**
     * Writes the [ruleSet] to the [path], creating the parent directories if needed, and makes sure the VFS notices.
     */
    fun write(path: Path, ruleSet: RuleSet) {
        path.parent?.let(Files::createDirectories)
        Files.writeString(path, RuleSetYaml.write(ruleSet))
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)
    }

    /**
     * Stores the [ruleSet] as the user rules applied to every project, see [userRulesFile].
     */
    fun writeUserRules(ruleSet: RuleSet) = write(userRulesFile, ruleSet.copy(id = userRulesFile.toString(), layer = RuleSetLayer.USER, path = userRulesFile))

    /**
     * Adds the current user directory to the VFS watch roots, so the changes made to its files refresh the Project View.
     * Called again once the directory is changed in the settings.
     */
    fun watchUserDirectory() {
        val directory = userDirectory
        if (watchedDirectory == directory) {
            return
        }
        watchedDirectory = directory
        coroutineScope.launch(Dispatchers.IO) {
            runCatching {
                with(LocalFileSystem.getInstance()) {
                    addRootToWatch(directory.toString(), true)
                    refreshAndFindFileByNioFile(directory)?.refresh(true, true)
                }
            }.onFailure { LOG.warn("Cannot watch the user directory $directory", it) }
        }
    }

    /**
     * Whether the [path] of a changed file lies in the user directory.
     */
    fun isUserFile(path: String) = runCatching { Path.of(path).startsWith(userDirectory) }.getOrDefault(false)

    /**
     * The resolved rules of the [ruleSet] as fresh [Rule]s without an origin, to be added to the IDE settings by the `+` menu
     * of the rules table like the rules entered by hand.
     */
    fun copyRules(ruleSet: RuleSet) = resolve(ruleSet).rules.map(RulePatch::toRule)

    companion object {

        private val LOG = logger<RuleSetRepository>()

        private val YAML_EXTENSIONS = setOf("yaml", "yml")
    }
}
