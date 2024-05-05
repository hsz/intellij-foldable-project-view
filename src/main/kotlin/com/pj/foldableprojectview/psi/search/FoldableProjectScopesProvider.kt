package com.pj.foldableprojectview.psi.search

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.SearchScopeProvider
import com.pj.foldableprojectview.FoldableProjectViewBundle
import com.pj.foldableprojectview.settings.FoldableProjectSettings

class FoldableProjectScopesProvider : SearchScopeProvider {

    override fun getDisplayName() = FoldableProjectViewBundle.getMessage("foldableProjectView.name")

    override fun getSearchScopes(project: Project, dataContext: DataContext): MutableList<SearchScope> {
        val settings = project.service<FoldableProjectSettings>()
        return settings.rules.map { FoldableProjectSearchScope(project, it.pattern) }.toMutableList()
    }
}
