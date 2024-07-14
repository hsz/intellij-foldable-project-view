package com.pj.foldableprojectview.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.OptionTag
import com.pj.foldableprojectview.FoldableProjectViewConstants
import java.io.File

@Service(Service.Level.PROJECT)
@State(name = "FoldableProjectSettings", storages = [Storage(FoldableProjectViewConstants.STORAGE_FILE)])
class FoldableProjectSettings : FoldableProjectState, BaseState(), PersistentStateComponent<FoldableProjectSettings> {
    @get:OptionTag("FOLDING_ENABLED")
    override var foldingEnabled by property(true)

    @get:OptionTag("MATCH_DIRECTORIES")
    override var matchDirectories by property(true)

    @get:OptionTag("HIDE_EMPTY_GROUPS")
    override var hideEmptyGroups by property(false)

    @get:OptionTag("HIDE_ALL_GROUPS")
    override var hideAllGroups by property(false)

    @get:OptionTag("CASE_SENSITIVE")
    override var caseSensitive by property(true)

    @get:OptionTag("HIDE_IGNORED_FILES")
    override var foldIgnoredFiles by property(true)

    @get:OptionTag("RULES")
    override var rules by list<Rule>()

    init {
        val tempRules = mutableListOf<Rule>()
        tempRules.add(Rule("Ignored Files", getGitIgnorePatterns().joinToString(" "), null, null))
        rules = tempRules
    }

    private fun getGitIgnorePatterns(): List<String> {
        val currentPath = System.getProperty("user.dir")
        val gitIgnoreFile = File("$currentPath/.gitignore")
        return if (gitIgnoreFile.exists()) {
            gitIgnoreFile.readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        } else {
            emptyList()
        }
    }

    override fun getState() = this

    override fun loadState(state: FoldableProjectSettings) = copyFrom(state)
}
