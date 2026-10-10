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
import ski.chrzanow.foldableprojectview.settings.RuleType
import java.util.concurrent.ConcurrentHashMap

/**
 * Search scope that contains files matching the rule's glob patterns.
 *
 * Patterns are space-separated, gitignore-like globs (`README.md *.yml gradle* !CHANGELOG.md`) matched
 * against the file path relative to its content root, see [GlobPatterns].
 *
 * For the [RuleType.PATTERNS_FILE] rules, the patterns are read from the files listed in [Rule.pattern]
 * (like `.foldignore`), located in the content root of the checked file. The parsed patterns are cached per content root
 * and re-read once any of the files changes.
 */
class FoldableProjectSearchScope(project: Project, state: FoldableProjectState, val rule: Rule) : GlobalSearchScope(project) {

    private val fileIndex = ProjectFileIndex.getInstance(project)
    private val caseSensitive = state.caseSensitive
    private val patterns = when (rule.type) {
        RuleType.PATTERN -> GlobPatterns.parse(rule.pattern, caseSensitive)
        else -> null
    }
    private val patternsFilePaths = rule.patternsFilePaths

    /**
     * Patterns read from the patterns files of a content root, along with the modification stamps of these files.
     */
    private class CachedPatterns(val stamps: Map<VirtualFile, Long>, val patterns: GlobPatterns)

    private val cachedPatterns = ConcurrentHashMap<VirtualFile, CachedPatterns>()

    override fun contains(file: VirtualFile): Boolean {
        val root = fileIndex.getContentRootForFile(file, false) ?: project?.guessProjectDir() ?: return false
        val patterns = getPatterns(root) ?: return false
        if (patterns.isEmpty) {
            return false
        }

        val path = VfsUtilCore.getRelativePath(file, root) ?: return false

        return patterns.matches(path, file.isDirectory)
    }

    private fun getPatterns(root: VirtualFile) = when (rule.type) {
        RuleType.PATTERN -> patterns
        RuleType.PATTERNS_FILE -> readPatternsFiles(root)
        RuleType.IGNORED_FILES -> null
    }

    /**
     * Reads the patterns from the patterns files located in the [root], or returns `null` if none of them exists.
     */
    private fun readPatternsFiles(root: VirtualFile): GlobPatterns? {
        val files = patternsFilePaths.mapNotNull { path ->
            root.findFileByRelativePath(path)?.takeUnless(VirtualFile::isDirectory)
        }
        if (files.isEmpty()) {
            cachedPatterns.remove(root)
            return null
        }

        val stamps = files.associateWith(VirtualFile::getModificationStamp)
        cachedPatterns[root]
            ?.takeIf { it.stamps == stamps }
            ?.let { return it.patterns }

        val text = files.joinToString("\n") { file ->
            runCatching { VfsUtilCore.loadText(file) }.getOrDefault("")
        }
        return GlobPatterns.parseLines(text, caseSensitive)
            .also { cachedPatterns[root] = CachedPatterns(stamps, it) }
    }

    override fun isSearchInModuleContent(aModule: Module) = true

    override fun isSearchInLibraries() = false

    override fun getDisplayName() = rule.name

    override fun toString() = "${rule.name}: ${rule.pattern}"
}
