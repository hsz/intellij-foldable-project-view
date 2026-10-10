package ski.chrzanow.foldableprojectview.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.OptionTag
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants

@Service(Service.Level.PROJECT)
@State(name = "FoldableProjectSettings", storages = [Storage(FoldableProjectViewConstants.STORAGE_FILE)])
class FoldableProjectSettings : FoldableProjectState, BaseState(), PersistentStateComponent<FoldableProjectSettings> {

    @get:OptionTag("FOLDING_ENABLED")
    override var foldingEnabled by property(true)

    @get:OptionTag("MATCH_DIRECTORIES")
    override var matchDirectories by property(true)

    @get:OptionTag("FOLD_NESTED_DIRECTORIES")
    override var foldNestedDirectories by property(false)

    @get:OptionTag("HIDE_EMPTY_GROUPS")
    override var hideEmptyGroups by property(true)

    /**
     * Replaced by the per-rule [Rule.action]; kept only to migrate the settings stored by the previous versions.
     */
    @get:OptionTag("HIDE_ALL_GROUPS")
    var hideAllGroups by property(false)

    @get:OptionTag("CASE_SENSITIVE")
    override var caseSensitive by property(true)

    /**
     * Replaced by the built-in [RuleType.IGNORED_FILES] rule; kept only to migrate the settings stored by the previous versions.
     */
    @get:OptionTag("HIDE_IGNORED_FILES")
    var legacyFoldIgnoredFiles by property(true)

    @get:OptionTag("RULES")
    override var rules by list<Rule>()

    override fun getState() = this

    override fun loadState(state: FoldableProjectSettings) {
        copyFrom(state)
        migrate()
    }

    override fun noStateLoaded() = loadDefaults()

    /**
     * Replaces the current settings with the defaults for new projects, see [FoldableProjectDefaults].
     */
    fun loadDefaults() {
        copyFrom(service<FoldableProjectDefaults>().state ?: FoldableProjectSettings())
        migrate()
    }

    /**
     * Brings the loaded state up to date with the current model:
     * - adds the built-in ignored files rule if missing, converting the legacy `Fold ignored files` option into its [Rule.enabled] flag,
     * - converts the legacy `Hide all groups` option into the [RuleAction.HIDE] action of every rule.
     */
    private fun migrate() {
        if (ignoredFilesRule == null) {
            rules.add(Rule.ignoredFiles(enabled = legacyFoldIgnoredFiles))
        }
        legacyFoldIgnoredFiles = true

        if (hideAllGroups) {
            rules.replaceAll { it.copy(action = RuleAction.HIDE) }
            hideAllGroups = false
        }
    }
}

/**
 * Whether the built-in [RuleType.IGNORED_FILES] rule is enabled. Changing it adds the rule if it is missing.
 */
var FoldableProjectSettings.foldIgnoredFiles: Boolean
    get() = ignoredFilesRule?.enabled == true
    set(value) {
        val index = rules.indexOfFirst { it.type == RuleType.IGNORED_FILES }
        if (index < 0) {
            rules.add(Rule.ignoredFiles(enabled = value))
        } else {
            // Replace the rule instead of mutating it in place, so the list-based modification tracking notices the change.
            val rule = rules.removeAt(index)
            rules.add(index, rule.copy(enabled = value))
        }
    }

/**
 * Applies [block] to the settings and notifies [FoldableProjectSettingsListener]s of the [project] about the change.
 * The defaults for new projects are not affected, see [FoldableProjectDefaults.store].
 */
fun FoldableProjectSettings.update(project: Project, block: FoldableProjectSettings.() -> Unit) {
    block()
    project.messageBus
        .syncPublisher(FoldableProjectSettingsListener.TOPIC)
        .settingsChanged(this)
}
