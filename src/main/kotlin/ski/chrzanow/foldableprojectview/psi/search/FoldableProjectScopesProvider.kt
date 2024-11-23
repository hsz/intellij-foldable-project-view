package ski.chrzanow.foldableprojectview.psi.search

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.SearchScopeProvider
import ski.chrzanow.foldableprojectview.FoldableProjectViewBundle
import ski.chrzanow.foldableprojectview.settings.FoldableProjectSettings

class FoldableProjectScopesProvider : SearchScopeProvider {

    override fun getDisplayName() = FoldableProjectViewBundle.getMessage("foldableProjectView.name")

    override fun getSearchScopes(project: Project, dataContext: DataContext): MutableList<SearchScope> {
        val settings = project.service<FoldableProjectSettings>()

        return settings.rules.map {
            FoldableProjectSearchScope(project, settings, it.pattern)
        }.toMutableList()
    }
}
