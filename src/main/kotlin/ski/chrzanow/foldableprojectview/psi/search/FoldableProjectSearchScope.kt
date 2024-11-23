package ski.chrzanow.foldableprojectview.psi.search

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessModuleDir
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vcs.changes.ignore.cache.PatternCache
import com.intellij.openapi.vcs.changes.ignore.lang.Syntax
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import ski.chrzanow.foldableprojectview.settings.FoldableProjectSettings
import kotlin.io.path.pathString
import kotlin.io.path.relativeToOrNull

class FoldableProjectSearchScope(project: Project, val settings: FoldableProjectSettings, val rule: String) : GlobalSearchScope(project) {

    private val fileIndex = ProjectFileIndex.getInstance(project)
    private fun String.caseSensitive() = when (settings.caseSensitive) {
        true -> this
        false -> this.lowercase()
    }

    private val pattern = PatternCache.getInstance(project).createPattern(rule.caseSensitive(), Syntax.GLOB)

    override fun contains(file: VirtualFile): Boolean {
        if (pattern == null) {
            return false
        }

        val moduleDir = fileIndex.getModuleForFile(file)?.guessModuleDir() ?: project?.guessProjectDir() ?: return false
        val base = moduleDir.toNioPath()
        val relativePath = file.toNioPath().relativeToOrNull(base) ?: return false
        val path = relativePath.pathString.caseSensitive()

        return pattern.matcher(path).matches()
    }

    override fun isSearchInModuleContent(aModule: Module) = true

    override fun isSearchInLibraries() = false

    override fun getDisplayName() = rule

    override fun toString() = rule
}
