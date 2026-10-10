package ski.chrzanow.foldableprojectview.psi.search

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.SearchScopeProvider
import ski.chrzanow.foldableprojectview.FoldableProjectViewBundle.message
import ski.chrzanow.foldableprojectview.settings.FoldableProjectSettings

class FoldableProjectScopesProvider : SearchScopeProvider {

    override fun getDisplayName() = message("foldableProjectView.name")

    override fun getSearchScopes(project: Project, dataContext: DataContext): List<SearchScope> {
        val settings = project.service<FoldableProjectSettings>()

        // The built-in ignored files rule has no patterns to search by, so only the active rules with patterns are exposed.
        return settings.rules
            .filter { it.isActive && it.usesPatterns }
            .map { it.getScope(project, settings) }
    }
}
