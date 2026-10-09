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

        return settings.rules
            .filter { it.pattern.isNotBlank() }
            .map { it.getScope(project, settings) }
    }
}
