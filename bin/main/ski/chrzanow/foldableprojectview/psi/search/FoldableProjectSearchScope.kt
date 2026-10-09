package ski.chrzanow.foldableprojectview.psi.search

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessModuleDir
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectFileIndex
import java.nio.file.FileSystems
import java.nio.file.PathMatcher
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import ski.chrzanow.foldableprojectview.go.GoWorkService
import ski.chrzanow.foldableprojectview.settings.FoldableProjectSettings
import kotlin.io.path.pathString
import kotlin.io.path.relativeToOrNull

class FoldableProjectSearchScope(project: Project, val settings: FoldableProjectSettings, val rule: String) : GlobalSearchScope(project) {

    private val fileIndex = ProjectFileIndex.getInstance(project)
    private fun String.caseSensitive() = when (settings.caseSensitive) {
        true -> this
        false -> this.lowercase()
    }

    private val patterns: List<PathMatcher> =
        rule
            .split(' ')
            .mapNotNull { raw ->
                val p = raw.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                try {
                    FileSystems.getDefault().getPathMatcher("glob:${p.caseSensitive()}")
                } catch (_: Exception) {
                    null
                }
            }

    override fun contains(file: VirtualFile): Boolean {
        if (patterns.isEmpty()) {
            return false
        }
        // Keep base detection for future relative logic (currently we match by name only)
        val goWorkService = project?.getService(GoWorkService::class.java)
        val workspaceRoot = goWorkService?.findWorkspaceRootFor(file)
        val moduleDir = workspaceRoot ?: fileIndex.getModuleForFile(file)?.guessModuleDir() ?: project?.guessProjectDir() ?: return false
        moduleDir.toNioPath()

        // Match by file/directory name only (consistent with UI/Tree filtering)
        val name = file.name.caseSensitive()
        val pathToMatch = try {
            java.nio.file.Paths.get(name)
        } catch (_: Exception) {
            return false
        }

        return patterns.any { m ->
            try {
                m.matches(pathToMatch.fileName ?: pathToMatch)
            } catch (_: Exception) {
                false
            }
        }
    }

    override fun isSearchInModuleContent(aModule: Module) = true

    override fun isSearchInLibraries() = false

    override fun getDisplayName() = rule

    override fun toString() = rule
}
