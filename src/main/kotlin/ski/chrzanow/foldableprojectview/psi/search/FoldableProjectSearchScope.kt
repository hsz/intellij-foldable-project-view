package ski.chrzanow.foldableprojectview.psi.search

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import ski.chrzanow.foldableprojectview.settings.FoldableProjectState
import ski.chrzanow.foldableprojectview.settings.Rule

/**
 * Search scope that contains files matching the rule's glob patterns.
 *
 * Patterns are space-separated, gitignore-like globs (`README.md *.yml gradle* !CHANGELOG.md`) matched
 * against the file path relative to its content root, see [GlobPatterns].
 */
class FoldableProjectSearchScope(project: Project, state: FoldableProjectState, val rule: Rule) : GlobalSearchScope(project) {

    private val fileIndex = ProjectFileIndex.getInstance(project)
    private val patterns = GlobPatterns.parse(rule.pattern, state.caseSensitive)

    override fun contains(file: VirtualFile): Boolean {
        if (patterns.isEmpty) {
            return false
        }

        val root = fileIndex.getContentRootForFile(file, false) ?: project?.guessProjectDir() ?: return false
        val path = VfsUtilCore.getRelativePath(file, root) ?: return false

        return patterns.matches(path, file.isDirectory)
    }

    override fun isSearchInModuleContent(aModule: Module) = true

    override fun isSearchInLibraries() = false

    override fun getDisplayName() = rule.name

    override fun toString() = "${rule.name}: ${rule.pattern}"
}
