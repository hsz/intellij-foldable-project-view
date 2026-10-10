package ski.chrzanow.foldableprojectview.psi.search

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.SearchScopeProvider
import ski.chrzanow.foldableprojectview.FoldableProjectViewBundle.message
import ski.chrzanow.foldableprojectview.settings.FoldableProjectSettings
import ski.chrzanow.foldableprojectview.settings.RuleType

class FoldableProjectScopesProvider : SearchScopeProvider {

    override fun getDisplayName() = message("foldableProjectView.name")

    override fun getSearchScopes(project: Project, dataContext: DataContext): List<SearchScope> {
        val settings = project.service<FoldableProjectSettings>()

        // The built-in ignored files rule has no pattern to search by, so only the active pattern rules are exposed.
        return settings.rules
            .filter { it.isActive && it.type == RuleType.PATTERN }
            .map { it.getScope(project, settings) }
    }
}
